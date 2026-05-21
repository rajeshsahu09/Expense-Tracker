package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ExpenseEntity
import com.example.data.ExpenseRepository
import com.example.ml.OnDeviceProcessor
import com.example.ml.ParsedExpense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface ParseStatus {
    object Idle : ParseStatus
    object Processing : ParseStatus
    data class Success(val parsed: ParsedExpense) : ParseStatus
    data class Error(val message: String) : ParseStatus
}

class ExpenseViewModel(
    private val repository: ExpenseRepository,
    private val mlProcessor: OnDeviceProcessor
) : ViewModel() {

    // Monthly budget set by the user, default $2500.0
    private val _budget = MutableStateFlow(2500.0)
    val budget: StateFlow<Double> = _budget.asStateFlow()

    // Filters for UI
    private val _selectedTimeRange = MutableStateFlow<String>("All") // "Day", "Week", "Month", "Year", "All"
    val selectedTimeRange: StateFlow<String> = _selectedTimeRange.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _selectedPaymentFilter = MutableStateFlow<String?>(null)
    val selectedPaymentFilter: StateFlow<String?> = _selectedPaymentFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Real-time on-device text parsing status
    private val _parsingStatus = MutableStateFlow<ParseStatus>(ParseStatus.Idle)
    val parsingStatus: StateFlow<ParseStatus> = _parsingStatus.asStateFlow()

    // Offline existing SMS inbox scanner status
    private val _smsScanResult = MutableStateFlow<String?>(null)
    val smsScanResult: StateFlow<String?> = _smsScanResult.asStateFlow()

    // Reactive pipeline flow combining DB stream and UI view filters
    val uiState: StateFlow<DashboardUiState> = combine(
        repository.allExpenses,
        _budget,
        _selectedTimeRange,
        _selectedCategoryFilter,
        _selectedPaymentFilter,
        _searchQuery
    ) { flowsArray ->
        @Suppress("UNCHECKED_CAST")
        val expenses = flowsArray[0] as List<ExpenseEntity>
        val budgetVal = flowsArray[1] as Double
        val timeRange = flowsArray[2] as String
        val categoryFilter = flowsArray[3] as String?
        val paymentFilter = flowsArray[4] as String?
        val query = flowsArray[5] as String

        if (expenses.isEmpty()) {
            DashboardUiState.Empty
        } else {
            val now = System.currentTimeMillis()
            // Apply queries
            val filteredList = expenses.filter { item ->
                val matchesCategory = categoryFilter == null || item.category == categoryFilter
                val matchesPayment = paymentFilter == null || item.paymentMethod == paymentFilter
                val matchesSearch = query.isEmpty() || 
                        item.description.contains(query, ignoreCase = true) ||
                        item.note.contains(query, ignoreCase = true)
                
                val matchesTime = when (timeRange) {
                    "Day" -> {
                        val midnight = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        item.dateMillis >= midnight
                    }
                    "Week" -> {
                        val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000)
                        item.dateMillis >= sevenDaysAgo
                    }
                    "Month" -> {
                        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)
                        item.dateMillis >= thirtyDaysAgo
                    }
                    "Year" -> {
                        val oneYearAgo = now - (365L * 24 * 60 * 60 * 1000)
                        item.dateMillis >= oneYearAgo
                    }
                    else -> true
                }
                
                matchesCategory && matchesPayment && matchesSearch && matchesTime
            }

            // Calculations based on the currently scoped list (so metrics adjust to selected time wise or parameter views)
            val totalSpent = filteredList.sumOf { it.amount }
            val balance = budgetVal - totalSpent

            // Categorical sums
            val categoryBreakdown = filteredList.groupBy { item -> item.category }
                .mapValues { (_, items) -> items.sumOf { item -> item.amount } }

            // Payment method sums
            val paymentBreakdown = filteredList.groupBy { item -> item.paymentMethod }
                .mapValues { (_, items) -> items.sumOf { item -> item.amount } }

            DashboardUiState.Success(
                expenses = filteredList, // Exposing only filtered list to the UI recycler/lazy list
                totalSpent = totalSpent,
                budget = budgetVal,
                balance = balance,
                categoryBreakdown = categoryBreakdown,
                paymentMethodBreakdown = paymentBreakdown
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading
    )

    init {
        // Prepopulate database with realistic sandbox data if empty so the user is greeted with a live layout
        prepopulateSampleDataIfEmpty()
    }

    /**
     * Set Category filter (null resets filter)
     */
    fun selectCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    /**
     * Set Payment Method filter (null resets filter)
     */
    fun selectPaymentFilter(paymentMethod: String?) {
        _selectedPaymentFilter.value = paymentMethod
    }

    /**
     * Set search query
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Change overall monthly budget limits
     */
    fun updateBudget(amount: Double) {
        _budget.value = amount
    }

    /**
     * Inserts an expense into local persistence using Dispatchers.IO
     */
    fun addExpense(
        amount: Double,
        description: String,
        category: String,
        paymentMethod: String,
        dateMillis: Long,
        note: String = ""
    ) {
        viewModelScope.launch {
            val entity = ExpenseEntity(
                amount = amount,
                description = description.ifBlank { "Uncategorized Item" },
                category = category,
                paymentMethod = paymentMethod,
                dateMillis = dateMillis,
                note = note
            )
            repository.insertExpense(entity)
        }
    }

    /**
     * Delete transaction safely off main thread
     */
    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    /**
     * Set selected evaluation time range
     */
    fun selectTimeRange(timeRange: String) {
        _selectedTimeRange.value = timeRange
    }

    /**
     * Scans the local SMS inbox, reads past SMS notifications on background threads,
     * and automatically saves transaction entities.
     */
    fun scanSmsInbox(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _smsScanResult.value = "Scanning inbox (reading past SMS)..."
            try {
                val uri = android.net.Uri.parse("content://sms/inbox")
                val projection = arrayOf("body", "address", "date")
                val cursor = context.contentResolver.query(uri, projection, null, null, "date DESC")
                var importedCount = 0
                
                if (cursor != null) {
                    val bodyIndex = cursor.getColumnIndexOrThrow("body")
                    val addressIndex = cursor.getColumnIndexOrThrow("address")
                    val dateIndex = cursor.getColumnIndexOrThrow("date")
                    
                    var processedCount = 0
                    while (cursor.moveToNext() && processedCount < 50) {
                        processedCount++
                        val body = cursor.getString(bodyIndex) ?: ""
                        val sender = cursor.getString(addressIndex) ?: "Unknown"
                        val timestamp = cursor.getLong(dateIndex)
                        
                        val parsed = mlProcessor.processText(body)
                        if (parsed.amount > 0.0) {
                            val entity = ExpenseEntity(
                                amount = parsed.amount,
                                description = if (parsed.description == "Generic Transaction" || parsed.description == "Generic Expenses") {
                                    "SMS from $sender"
                                } else {
                                    parsed.description
                                },
                                category = parsed.category,
                                paymentMethod = parsed.paymentMethod,
                                dateMillis = timestamp,
                                note = "Scanned Inbox SMS ($sender)"
                            )
                            repository.insertExpense(entity)
                            importedCount++
                        }
                    }
                    cursor.close()
                }
                _smsScanResult.value = "Inbox scan completed. Found & imported $importedCount dynamic transactions successfully!"
            } catch (e: SecurityException) {
                _smsScanResult.value = "SMS Read permission is missing. Please authorize in application dialog settings."
            } catch (e: Exception) {
                _smsScanResult.value = "Error scanning SMS storage: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Clear current SMS scan diagnostic state output
     */
    fun clearSmsResult() {
        _smsScanResult.value = null
    }

    /**
     * Reset parsing status
     */
    fun clearParsingStatus() {
        _parsingStatus.value = ParseStatus.Idle
    }

    /**
     * Parse raw text (banking SMS, casual notes) asynchronously
     */
    fun parseTextWithML(rawText: String) {
        if (rawText.isBlank()) {
            _parsingStatus.value = ParseStatus.Error("Please enter some text to process.")
            return
        }

        _parsingStatus.value = ParseStatus.Processing
        viewModelScope.launch {
            try {
                val parsedResult = mlProcessor.processText(rawText)
                _parsingStatus.value = ParseStatus.Success(parsedResult)
            } catch (e: Exception) {
                _parsingStatus.value = ParseStatus.Error("ML Parser Exception: ${e.localizedMessage}")
            }
        }
    }

    private fun prepopulateSampleDataIfEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.allExpenses.first().let { currentList ->
                if (currentList.isEmpty()) {
                    val sampleData = listOf(
                        ExpenseEntity(
                            amount = 450.0,
                            description = "Zomato Pizza Feast",
                            category = OnDeviceProcessor.CAT_FOOD,
                            paymentMethod = "UPI",
                            dateMillis = System.currentTimeMillis() - (1000 * 60 * 60 * 4) // 4 hours ago
                        ),
                        ExpenseEntity(
                            amount = 54.20,
                            description = "Uber Airport Ride",
                            category = OnDeviceProcessor.CAT_TRAVEL,
                            paymentMethod = "Card",
                            dateMillis = System.currentTimeMillis() - (1000 * 60 * 60 * 24) // 1 day ago
                        ),
                        ExpenseEntity(
                            amount = 125.0,
                            description = "CVS Pharma Medicines",
                            category = OnDeviceProcessor.CAT_MEDICAL,
                            paymentMethod = "Cash",
                            dateMillis = System.currentTimeMillis() - (1000 * 60 * 60 * 24 * 3) // 3 days ago
                        ),
                        ExpenseEntity(
                            amount = 890.0,
                            description = "Electricity utility bill",
                            category = OnDeviceProcessor.CAT_UTILITIES,
                            paymentMethod = "UPI",
                            dateMillis = System.currentTimeMillis() - (1000 * 60 * 60 * 24 * 5) // 5 days ago
                        ),
                        ExpenseEntity(
                            amount = 299.00,
                            description = "Zara Winter Jacket",
                            category = OnDeviceProcessor.CAT_SHOPPING,
                            paymentMethod = "Card",
                            dateMillis = System.currentTimeMillis() - (1000 * 60 * 60 * 24 * 7) // 7 days ago
                        )
                    )
                    sampleData.forEach { repository.insertExpense(it) }
                }
            }
        }
    }
}

/**
 * Custom Factory pattern for dependency instantiation.
 */
class ExpenseViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            val database = AppDatabase.getDatabase(context)
            val repository = ExpenseRepository(database.expenseDao())
            val mlProcessor = OnDeviceProcessor()
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(repository, mlProcessor) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
