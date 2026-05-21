package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ExpenseEntity
import com.example.ml.OnDeviceProcessor
import com.example.ml.ParsedExpense
import java.text.SimpleDateFormat
import java.util.*

// Theme accent colors for Vault
val GrayBackground = Color(0xFF0C101A)
val CardSurface = Color(0xFF141A29)
val PrimaryAccent = Color(0xFF10B981) // Neon emerald
val SpentAccent = Color(0xFFEF4444) // Coral red
val BorderColor = Color(0xFF222B45)
val HighContrastText = Color(0xFFF3F4F6)
val MedContrastText = Color(0xFF9CA3AF)

// Category palettes
val ColorCategoryFood = Color(0xFFF59E0B) // Orange
val ColorCategoryTravel = Color(0xFF06B6D4) // Cyan
val ColorCategoryUtilities = Color(0xFF8B5CF6) // Purple
val ColorCategoryShopping = Color(0xFF3B82F6) // Blue
val ColorCategoryMedical = Color(0xFFEC4899) // Pink Accent

fun getCategoryColor(category: String): Color {
    return when (category) {
        OnDeviceProcessor.CAT_FOOD -> ColorCategoryFood
        OnDeviceProcessor.CAT_TRAVEL -> ColorCategoryTravel
        OnDeviceProcessor.CAT_UTILITIES -> ColorCategoryUtilities
        OnDeviceProcessor.CAT_SHOPPING -> ColorCategoryShopping
        OnDeviceProcessor.CAT_MEDICAL -> ColorCategoryMedical
        else -> PrimaryAccent
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val budgetValue by viewModel.budget.collectAsState()
    val activeCategoryFilter by viewModel.selectedCategoryFilter.collectAsState()
    val activePaymentFilter by viewModel.selectedPaymentFilter.collectAsState()
    val queryVal by viewModel.searchQuery.collectAsState()
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()
    val smsScanResult by viewModel.smsScanResult.collectAsState()

    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    // Register Activity Result permission callback
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsRead = permissions[android.Manifest.permission.READ_SMS] ?: false
        val smsReceive = permissions[android.Manifest.permission.RECEIVE_SMS] ?: false
        if (smsRead) {
            viewModel.scanSmsInbox(context)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(GrayBackground),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.padding(end = 10.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryAccent.copy(alpha = 0.15f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "On-Device Privacy Lock",
                                tint = PrimaryAccent,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "VAULT",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                letterSpacing = 2.sp,
                                color = HighContrastText
                            )
                            Text(
                                text = "100% LOCAL PRIVACY SECURED",
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryAccent
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showBudgetDialog = true },
                        modifier = Modifier.testTag("settings_budget_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Edit Monthly Budget Goal",
                            tint = MedContrastText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GrayBackground,
                    titleContentColor = HighContrastText
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PrimaryAccent,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .testTag("add_expense_fab")
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Expense")
                    Text("Add Expense", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GrayBackground)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Check for diagnostic logs alert message
            smsScanResult?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = PrimaryAccent.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, PrimaryAccent.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "SMS Scanner Broadcast Notification",
                                tint = PrimaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = HighContrastText
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearSmsResult() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Alerts Banner",
                                tint = MedContrastText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryAccent)
                    }
                }
                is DashboardUiState.Empty -> {
                    EmptyStateContent(
                        onQuickSeed = { showAddDialog = true },
                        onScanSms = {
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_SMS,
                                    android.Manifest.permission.RECEIVE_SMS
                                )
                            )
                        }
                    )
                }
                is DashboardUiState.Success -> {
                    SuccessLayout(
                        state = state,
                        budgetValue = budgetValue,
                        activeCategoryFilter = activeCategoryFilter,
                        activePaymentFilter = activePaymentFilter,
                        selectedTimeRange = selectedTimeRange,
                        queryVal = queryVal,
                        onSelectCategory = { viewModel.selectCategoryFilter(it) },
                        onSelectPayment = { viewModel.selectPaymentFilter(it) },
                        onSelectTimeRange = { viewModel.selectTimeRange(it) },
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onDeleteExpense = { viewModel.deleteExpense(it) },
                        onScanSms = {
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_SMS,
                                    android.Manifest.permission.RECEIVE_SMS
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    // Modal Add & Smart Parse Interactive Drawer Sheet
    if (showAddDialog) {
        AddExpenseDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false }
        )
    }

    // Edit Budget Limit Floating Sheet
    if (showBudgetDialog) {
        EditBudgetDialog(
            currentBudget = budgetValue,
            onSave = {
                viewModel.updateBudget(it)
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false }
        )
    }
}

/**
 * Empty placeholder state
 */
@Composable
fun EmptyStateContent(
    onQuickSeed: () -> Unit,
    onScanSms: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = CardSurface,
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Zero Expenses Logo",
                    tint = PrimaryAccent.copy(alpha = 0.6f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Vault is Perfectly Clean",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = HighContrastText
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "No recorded transactions found. Generate a manual entry or click below to scan device SMS inbox using on-device ML.",
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            color = MedContrastText,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onQuickSeed,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_seed_button")
            ) {
                Text("Add Manually", fontWeight = FontWeight.Bold)
            }
            
            OutlinedButton(
                onClick = onScanSms,
                border = BorderStroke(1.dp, PrimaryAccent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_sms_scan_button")
            ) {
                Icon(imageVector = Icons.Default.Email, contentDescription = "Scan SMS", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan SMS Inbox", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Beautiful, user-interactive segment chips row for choosing evaluation time frames.
 */
@Composable
fun TimeRangeSelector(
    selectedRange: String,
    onSelectRange: (String) -> Unit
) {
    val ranges = listOf("All", "Day", "Week", "Month", "Year")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ranges.forEach { range ->
            val isSelected = selectedRange == range
            Surface(
                onClick = { onSelectRange(range) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) PrimaryAccent else CardSurface,
                border = BorderStroke(1.dp, if (isSelected) PrimaryAccent else BorderColor),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("time_chip_$range")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = when(range) {
                            "Day" -> "Today"
                            "Week" -> "This Week"
                            "Month" -> "This Month"
                            "Year" -> "This Year"
                            else -> "All History"
                        },
                        color = if (isSelected) Color.Black else MedContrastText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Super premium Live SMS Sync control card displaying status and authorizing permissions
 */
@Composable
fun LocalSmsAssistantCard(onScanSms: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sms_assistant_card"),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PrimaryAccent.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "SMS AI Sync Service",
                        tint = PrimaryAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Live SMS Expense Scanner",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = HighContrastText
                )
                Text(
                    text = "Background daemon automatically reads incoming messages. Click to sync past banking SMS.",
                    fontSize = 11.sp,
                    color = MedContrastText,
                    lineHeight = 15.sp
                )
            }
            Button(
                onClick = onScanSms,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryAccent.copy(alpha = 0.18f),
                    contentColor = PrimaryAccent
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Sync SMS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Standard dashboard dynamic data overview
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuccessLayout(
    state: DashboardUiState.Success,
    budgetValue: Double,
    activeCategoryFilter: String?,
    activePaymentFilter: String?,
    selectedTimeRange: String,
    queryVal: String,
    onSelectCategory: (String?) -> Unit,
    onSelectPayment: (String?) -> Unit,
    onSelectTimeRange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    onScanSms: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Hero Section: Monthly ring progress
        item {
            HeroRingMetricCard(
                totalSpent = state.totalSpent,
                budget = budgetValue,
                balance = state.balance
            )
        }

        // Time Range Filter Segment
        item {
            TimeRangeSelector(
                selectedRange = selectedTimeRange,
                onSelectRange = onSelectTimeRange
            )
        }

        // Dynamic local real-time tracker authorization card
        item {
            LocalSmsAssistantCard(onScanSms = onScanSms)
        }

        // Segment Category representation Canvas bar
        item {
            SegmentationCanvasCard(
                categoryBreakdown = state.categoryBreakdown,
                totalSpent = state.totalSpent
            )
        }

        // Interactive Filtering Control Panel
        item {
            InteractiveFilterPanel(
                query = queryVal,
                onQueryChange = onQueryChange,
                selectedCategory = activeCategoryFilter,
                onSelectCategory = onSelectCategory,
                selectedPayment = activePaymentFilter,
                onSelectPayment = onSelectPayment
            )
        }

        // Instantly Filterable list segment
        if (state.expenses.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "No Matches Found",
                            tint = SpentAccent,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No Matching Transactions",
                            fontWeight = FontWeight.Bold,
                            color = HighContrastText
                        )
                        Text(
                            "Loosen filter criteria to view hidden cached records.",
                            fontSize = 12.sp,
                            color = MedContrastText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(state.expenses, key = { it.id }) { item ->
                ExpenseCardItem(
                    item = item,
                    onDelete = { onDeleteExpense(item) }
                )
            }
        }
    }
}

/**
 * Beautiful dynamic metric hero featuring native custom progress circles
 */
@Composable
fun HeroRingMetricCard(
    totalSpent: Double,
    budget: Double,
    balance: Double
) {
    val progress = (totalSpent / budget).coerceIn(0.0, 1.0).toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 50f),
        label = "Ring Animation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_metric_card"),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interactive balance ring progress chart using raw canvas
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 10.dp.toPx()
                    // Background Ring Path
                    drawArc(
                        color = BorderColor,
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    // Foreground Utilization Arc
                    drawArc(
                        color = if (progress >= 0.90f) SpentAccent else PrimaryAccent,
                        startAngle = 140f,
                        sweepAngle = animatedProgress * 260f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = if (progress >= 0.90f) SpentAccent else HighContrastText
                    )
                    Text(
                        text = "SPENT",
                        fontSize = 8.sp,
                        letterSpacing = 0.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedContrastText
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Text breakdowns
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text("ACTIVE WALLET BALANCE", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MedContrastText)
                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", balance)}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (balance < 0) SpentAccent else HighContrastText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("BUDGET LIMIT", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = MedContrastText)
                        Text(
                            "$${String.format(Locale.US, "%,.0f", budget)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = HighContrastText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TOTAL SPENT", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = MedContrastText)
                        Text(
                            "$${String.format(Locale.US, "%,.2f", totalSpent)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = SpentAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Segmentation proportions bar drawn entirely inside pure Canvas
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentationCanvasCard(
    categoryBreakdown: Map<String, Double>,
    totalSpent: Double
) {
    val total = if (totalSpent <= 0.0) 1.0 else totalSpent

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "AUTOMATED CATEGORIZATION BREAKDOWN",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = MedContrastText
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Customized Canvas Proportion Bar
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                var currentStartX = 0f
                val barWidth = size.width

                OnDeviceProcessor.ALL_CATEGORIES.forEach { category ->
                    val amt = categoryBreakdown[category] ?: 0.0
                    if (amt > 0) {
                        val segmentPercent = (amt / total).toFloat()
                        val segmentWidth = segmentPercent * barWidth
                        drawRect(
                            color = getCategoryColor(category),
                            topLeft = Offset(currentStartX, 0f),
                            size = Size(segmentWidth, size.height)
                        )
                        currentStartX += segmentWidth
                    }
                }
                if (currentStartX == 0f) {
                    // Empty background fallback
                    drawRect(
                        color = BorderColor,
                        topLeft = Offset(0f, 0f),
                        size = size
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dynamic Categories Legend Grid
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnDeviceProcessor.ALL_CATEGORIES.forEach { category ->
                    val amt = categoryBreakdown[category] ?: 0.0
                    val percent = if (totalSpent > 0) (amt / totalSpent * 100).toInt() else 0
                    Row(
                        modifier = Modifier
                            .background(getCategoryColor(category).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .border(1.dp, getCategoryColor(category).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(getCategoryColor(category))
                        )
                        Text(
                            text = category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HighContrastText
                        )
                        Text(
                            text = "$percent%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = getCategoryColor(category)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern card filters panel including search text capabilities
 */
@Composable
fun InteractiveFilterPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    selectedPayment: String?,
    onSelectPayment: (String?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search textbox
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search description or notes...", color = MedContrastText, fontSize = 13.sp) },
            prefix = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MedContrastText, modifier = Modifier.size(16.dp).padding(end = 4.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input_field"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardSurface,
                unfocusedContainerColor = CardSurface,
                focusedBorderColor = PrimaryAccent,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = HighContrastText,
                unfocusedTextColor = HighContrastText
            ),
            shape = RoundedCornerShape(14.dp)
        )

        // Horizontal Category filtering stream
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "All" filter chip
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onSelectCategory(null) },
                label = { Text("All Categories", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryAccent,
                    selectedLabelColor = Color.Black,
                    containerColor = CardSurface,
                    labelColor = MedContrastText
                ),
                border = FilterChipDefaults.filterChipBorder(
                    selectedBorderColor = PrimaryAccent,
                    borderColor = BorderColor,
                    enabled = true,
                    selected = selectedCategory == null
                )
            )

            OnDeviceProcessor.ALL_CATEGORIES.forEach { category ->
                val categoryColor = getCategoryColor(category)
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                    label = { Text(category, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = categoryColor,
                        selectedLabelColor = Color.Black,
                        containerColor = CardSurface,
                        labelColor = HighContrastText
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = BorderColor,
                        selectedBorderColor = categoryColor,
                        enabled = true,
                        selected = selectedCategory == category
                    )
                )
            }
        }

        // Action methods filtering stream (UPI, Cash, Card)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Payment:", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MedContrastText)
            
            val payments = listOf("UPI", "Cash", "Card")
            payments.forEach { method ->
                val isSelected = selectedPayment == method
                Surface(
                    modifier = Modifier
                        .clickable { onSelectPayment(if (isSelected) null else method) }
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (isSelected) PrimaryAccent else BorderColor, RoundedCornerShape(8.dp)),
                    color = if (isSelected) PrimaryAccent.copy(alpha = 0.12f) else CardSurface
                ) {
                    Text(
                        text = method,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) PrimaryAccent else HighContrastText
                    )
                }
            }
        }
    }
}

/**
 * Gorgeous transaction card render
 */
@Composable
fun ExpenseCardItem(
    item: ExpenseEntity,
    onDelete: () -> Unit
) {
    val dateString = remember(item.dateMillis) {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
        sdf.format(Date(item.dateMillis))
    }
    val catColor = getCategoryColor(item.category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("expense_item_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category graphic circle indicator
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = catColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, catColor.copy(alpha = 0.25f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Unique visual characters indicating types
                    val iconText = when (item.category) {
                        OnDeviceProcessor.CAT_FOOD -> "🍿"
                        OnDeviceProcessor.CAT_TRAVEL -> "🚕"
                        OnDeviceProcessor.CAT_UTILITIES -> "⚡"
                        OnDeviceProcessor.CAT_SHOPPING -> "🛍️"
                        OnDeviceProcessor.CAT_MEDICAL -> "💊"
                        else -> "💸"
                    }
                    Text(iconText, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.description,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = HighContrastText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Method Tag badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BorderColor,
                        modifier = Modifier.padding(bottom = 1.dp)
                    ) {
                        Text(
                            text = item.paymentMethod,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MedContrastText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateString,
                        fontSize = 11.sp,
                        color = MedContrastText
                    )
                    if (item.note.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(MedContrastText)
                        )
                        Text(
                            text = item.note,
                            fontSize = 11.sp,
                            color = MedContrastText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Transaction value total
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "-$${String.format(Locale.US, "%.2f", item.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = SpentAccent
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_expense_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove transaction",
                        tint = MedContrastText.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Advanced unified insert dialog incorporating raw SMS text extraction
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val parsingStatus by viewModel.parsingStatus.collectAsState()

    var activeTabManual by remember { mutableStateOf(false) } // Default to "AI SMS Parser" or manual

    // SMS Input
    var pasteInput by remember { mutableStateOf(TextFieldValue("")) }

    // Manual Fields (State gets synced beautifully if processed successfully)
    var amountValue by remember { mutableStateOf("") }
    var descValue by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(OnDeviceProcessor.CAT_FOOD) }
    var selectedPayment by remember { mutableStateOf("Cash") }
    var noteValue by remember { mutableStateOf("") }

    // Sync state values on successful on-device parsing completion
    LaunchedEffect(parsingStatus) {
        if (parsingStatus is ParseStatus.Success) {
            val parsed = (parsingStatus as ParseStatus.Success).parsed
            amountValue = if (parsed.amount > 0) parsed.amount.toString() else ""
            descValue = parsed.description
            selectedCategory = parsed.category
            selectedPayment = parsed.paymentMethod
            activeTabManual = true // auto-toggle manual edits to let user verify
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(GrayBackground),
            color = GrayBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SECURE LOCAL TRANSACTION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = PrimaryAccent,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (activeTabManual) "Add Custom Expense" else "Copilot Intelligent SMS Reader",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = HighContrastText
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.clearParsingStatus()
                            onDismiss()
                        },
                        modifier = Modifier.background(CardSurface, CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = HighContrastText)
                    }
                }

                // Split Custom Accent Segment switcher tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSurface, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Button(
                        onClick = { activeTabManual = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!activeTabManual) BorderColor else Color.Transparent,
                            contentColor = HighContrastText
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp).padding(end = 4.dp))
                        Text("AI SMS Parser", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { activeTabManual = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTabManual) BorderColor else Color.Transparent,
                            contentColor = HighContrastText
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp).padding(end = 4.dp))
                        Text("Manual Details", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                AnimatedContent(
                    targetState = activeTabManual,
                    transitionSpec = {
                        slideInHorizontally(animationSpec = tween(220)) { if (targetState) it else -it } togetherWith
                        slideOutHorizontally(animationSpec = tween(220)) { if (targetState) -it else it }
                    },
                    label = "Tab 전환"
                ) { tab ->
                    if (!tab) {
                        // AI SMS Reader Terminal Pane
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CardSurface,
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        "PASTE RAW TEXT OR BANK SMS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MedContrastText,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedTextField(
                                        value = pasteInput,
                                        onValueChange = { pasteInput = it },
                                        placeholder = {
                                            Text(
                                                "Example: Debited USD 45 at Starbucks on Ref 5831...\nor: Spent 12 dollars at McDonald's yesterday for burger",
                                                color = MedContrastText,
                                                fontSize = 12.sp
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .testTag("ai_raw_input_sms"),
                                        textStyle = TextStyle(fontSize = 13.sp, color = HighContrastText, fontFamily = FontFamily.Monospace),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = GrayBackground,
                                            unfocusedContainerColor = GrayBackground
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // Process Action button
                                    Button(
                                        onClick = {
                                            viewModel.parseTextWithML(pasteInput.text)
                                        },
                                        enabled = pasteInput.text.isNotBlank() && parsingStatus != ParseStatus.Processing,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("run_ml_parser_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PrimaryAccent,
                                            contentColor = Color.Black,
                                            disabledContainerColor = BorderColor,
                                            disabledContentColor = MedContrastText
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (parsingStatus is ParseStatus.Processing) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                                                Text("Processing On-Device...", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                            }
                                        } else {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Text("Extract & Classify (Local)", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                            }

                            // Dynamic ML Feedback Logs Console
                            AnimatedVisibility(
                                visible = parsingStatus !is ParseStatus.Idle,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black,
                                    border = BorderStroke(1.dp, BorderColor)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "LOCAL PIPELINE EXECUTION OUTPUT",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = PrimaryAccent,
                                            letterSpacing = 1.sp
                                        )

                                        when (val status = parsingStatus) {
                                            is ParseStatus.Processing -> {
                                                Text(
                                                    "Calculating ML Kit Entity Extraction and MediaPipe neural classification tokens securely off-thread...",
                                                    fontSize = 11.sp,
                                                    color = MedContrastText
                                                )
                                            }
                                            is ParseStatus.Error -> {
                                                Text(
                                                    status.message,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = SpentAccent
                                                )
                                            }
                                            is ParseStatus.Success -> {
                                                // Confidence indicators
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Confidence Level: ${(status.parsed.confidence * 100).toInt()}%",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = PrimaryAccent
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = PrimaryAccent.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            "LOCAL SECURED",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = PrimaryAccent,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                HorizontalDivider(color = BorderColor)
                                                
                                                // Raw logs printout
                                                Text(
                                                    text = status.parsed.logs,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF10F3A2),
                                                    modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())
                                                )
                                                
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    "✨ Auto-filled data successfully! Tweak details in Manual Tab or tap save.",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = HighContrastText
                                                )
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Manual Entry Form Screen
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Amount Numerical
                            Column {
                                Text("AMOUNT ($)", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MedContrastText)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = amountValue,
                                    onValueChange = { amountValue = it },
                                    placeholder = { Text("0.00", color = MedContrastText) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("expense_amount_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = CardSurface,
                                        unfocusedContainerColor = CardSurface,
                                        focusedBorderColor = PrimaryAccent,
                                        unfocusedBorderColor = BorderColor,
                                        focusedTextColor = HighContrastText,
                                        unfocusedTextColor = HighContrastText
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            // Description Text
                            Column {
                                Text("DESCRIPTION", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MedContrastText)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = descValue,
                                    onValueChange = { descValue = it },
                                    placeholder = { Text("e.g., Starbucks Coffee / Uber Flight", color = MedContrastText) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("expense_desc_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = CardSurface,
                                        unfocusedContainerColor = CardSurface,
                                        focusedBorderColor = PrimaryAccent,
                                        unfocusedBorderColor = BorderColor,
                                        focusedTextColor = HighContrastText,
                                        unfocusedTextColor = HighContrastText
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            // Category Selector Chips
                            Column {
                                Text("MAIN CATEGORY ALIGNMENT", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MedContrastText)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OnDeviceProcessor.ALL_CATEGORIES.forEach { category ->
                                        val isSelected = selectedCategory == category
                                        val activeColor = getCategoryColor(category)
                                        Surface(
                                            modifier = Modifier
                                                .clickable { selectedCategory = category }
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(1.dp, if (isSelected) activeColor else BorderColor, RoundedCornerShape(10.dp)),
                                            color = if (isSelected) activeColor.copy(alpha = 0.15f) else CardSurface
                                        ) {
                                            Text(
                                                text = category,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = if (isSelected) activeColor else HighContrastText,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Payment Channel Choices
                            Column {
                                Text("PAYMENT INSTRUMENT", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MedContrastText)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("UPI", "Cash", "Card").forEach { method ->
                                        val isSelected = selectedPayment == method
                                        Button(
                                            onClick = { selectedPayment = method },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) PrimaryAccent else CardSurface,
                                                contentColor = if (isSelected) Color.Black else HighContrastText
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f),
                                            border = BorderStroke(1.dp, if (isSelected) PrimaryAccent else BorderColor)
                                        ) {
                                            Text(method, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }

                            // Metadata notes text
                            Column {
                                Text("CUSTOM METADATA / NOTE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MedContrastText)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = noteValue,
                                    onValueChange = { noteValue = it },
                                    placeholder = { Text("e.g. UPI ref receipt code or bank account name", color = MedContrastText, fontSize = 11.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("expense_note_input"),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = CardSurface,
                                        unfocusedContainerColor = CardSurface,
                                        focusedBorderColor = PrimaryAccent,
                                        unfocusedBorderColor = BorderColor,
                                        focusedTextColor = HighContrastText,
                                        unfocusedTextColor = HighContrastText
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Final save action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.clearParsingStatus()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HighContrastText)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val doubleAmount = amountValue.toDoubleOrNull() ?: 0.0
                            viewModel.addExpense(
                                amount = doubleAmount,
                                description = descValue,
                                category = selectedCategory,
                                paymentMethod = selectedPayment,
                                dateMillis = System.currentTimeMillis(),
                                note = noteValue
                            )
                            viewModel.clearParsingStatus()
                            onDismiss()
                        },
                        enabled = amountValue.isNotBlank() && descValue.isNotBlank() && (amountValue.toDoubleOrNull() ?: 0.0) > 0.0,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("submit_expense_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryAccent,
                            contentColor = Color.Black,
                            disabledContainerColor = BorderColor,
                            disabledContentColor = MedContrastText
                        )
                    ) {
                        Text("Save Vault Lock", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Centered budget configuration sheet
 */
@Composable
fun EditBudgetDialog(
    currentBudget: Double,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var budgetValue by remember { mutableStateOf(currentBudget.toInt().toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = PrimaryAccent.copy(alpha = 0.12f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Configure Monthly Limit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = HighContrastText
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Specify active monthly budget allowance limits.",
                    fontSize = 11.sp,
                    color = MedContrastText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = budgetValue,
                    onValueChange = { budgetValue = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_limit_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GrayBackground,
                        unfocusedContainerColor = GrayBackground,
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = HighContrastText,
                        unfocusedTextColor = HighContrastText
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MedContrastText)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val amt = budgetValue.toDoubleOrNull() ?: 1000.0
                            if (amt > 0) {
                                onSave(amt)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("submit_budget_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent, contentColor = Color.Black)
                    ) {
                        Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
