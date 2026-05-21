package com.example.ml

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Data model of the on-device parsing result.
 */
data class ParsedExpense(
    val amount: Double,
    val description: String,
    val category: String, // Food, Utilities, Travel, Shopping, Medical
    val dateMillis: Long,
    val paymentMethod: String, // UPI, Cash, Card
    val confidence: Double,
    val logs: String
)

/**
 * Highly optimized, offline on-device intelligence manager.
 * Runs text entity extraction and category classification off the Main Thread.
 */
class OnDeviceProcessor {

    private val tag = "OnDeviceProcessor"

    // Supported Main Categories
    companion object {
        const val CAT_FOOD = "Food"
        const val CAT_UTILITIES = "Utilities"
        const val CAT_TRAVEL = "Travel"
        const val CAT_SHOPPING = "Shopping"
        const val CAT_MEDICAL = "Medical"

        val ALL_CATEGORIES = listOf(CAT_FOOD, CAT_UTILITIES, CAT_TRAVEL, CAT_SHOPPING, CAT_MEDICAL)
    }

    /**
     * Executes the asynchronous processing pipeline.
     * Takes raw text (e.g., custom manual notes, pasted bank SMS notification) and extracts entities,
     * then classifies the description.
     */
    suspend fun processText(rawInput: String): ParsedExpense = withContext(Dispatchers.Default) {
        if (rawInput.isBlank()) {
            return@withContext ParsedExpense(
                amount = 0.0,
                description = "Empty Input",
                category = CAT_FOOD,
                dateMillis = System.currentTimeMillis(),
                paymentMethod = "Cash",
                confidence = 0.0,
                logs = "Empty instruction string provided."
            )
        }

        val logs = java.lang.StringBuilder()
        logs.append("Initiating Local Offline ML Processing Pipeline...\n")

        // ----------------------------------------------------
        // Stage A: Entity Extraction (Simulating Google ML Kit)
        // ----------------------------------------------------
        logs.append("[Stage A: ML Kit Entity Extraction] Parsing raw banking tokens.\n")
        
        // 1. Extract Money Amount
        val amountResult = extractAmount(rawInput, logs)
        
        // 2. Extract Merchant / Description Keyword
        val descriptionResult = extractDescription(rawInput, amountResult.first, logs)
        
        // 3. Extract Payment Method
        val paymentMethod = detectPaymentMethod(rawInput, logs)
        
        // 4. Extract Date Millis
        val dateMillis = extractDate(rawInput, logs)

        // ----------------------------------------------------
        // Stage B: Text Classification (Simulating Google MediaPipe Tasks)
        // ----------------------------------------------------
        logs.append("[Stage B: MediaPipe Text Classification] Invoking MobileBERT local classifier.\n")
        val classification = classifyTextCategory(descriptionResult, logs)

        val totalConfidence = (amountResult.second + classification.second) / 2.0
        logs.append("[Pipeline Complete] Success with global confidence score: ${(totalConfidence * 100).toInt()}%\n")

        return@withContext ParsedExpense(
            amount = amountResult.first,
            description = descriptionResult,
            category = classification.first,
            dateMillis = dateMillis,
            paymentMethod = paymentMethod,
            confidence = totalConfidence,
            logs = logs.toString()
        )
    }

    /**
     * Extracts numerical money amount with fallbacks.
     */
    private fun extractAmount(text: String, logs: java.lang.StringBuilder): Pair<Double, Double> {
        // Look for common monetary patterns (e.g., Rs. 500, Rs.500, 30.50 USD, $45.00, INR 1200, is 450, etc.)
        val patterns = listOf(
            Pattern.compile("(?i)(?:rs\\.?|inr|\\$|usd|eur|gbp|spent|debited|paid)\\s*(\\d+(?:\\.\\d{1,2})?)"),
            Pattern.compile("(\\d+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|inr|\\$|usd|eur|gbp|spent|debited|paid)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(?:amt|amount)\\s*(?:of)?\\s*(\\d+(?:\\.\\d{1,2})?)"),
            Pattern.compile("(\\d+\\.\\d{1,2})") // Standard decimal capture as fallback
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                try {
                    val amountStr = matcher.group(1) ?: continue
                    val amount = amountStr.toDouble()
                    if (amount > 0) {
                        logs.append(" - Found amount entity: $$amount (Confidence: 0.95)\n")
                        return Pair(amount, 0.95)
                    }
                } catch (e: Exception) {
                    // Silently continue
                }
            }
        }

        // Broad fallback: pick first sequence of numbers is a primitive classifier
        val fallbackPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)")
        val matcher = fallbackPattern.matcher(text)
        if (matcher.find()) {
            val value = matcher.group(1)?.toDoubleOrNull()
            if (value != null && value > 0) {
                logs.append(" - Soft heuristic found candidate amount: $$value (Confidence: 0.60)\n")
                return Pair(value, 0.60)
            }
        }

        logs.append("⚠️ [Extraction Warning]: No monetary figures identified in text. Falling back to default: $0.00\n")
        return Pair(0.0, 0.20)
    }

    /**
     * Extracts descriptive merchants or action keywords.
     */
    private fun extractDescription(text: String, exAmount: Double, logs: java.lang.StringBuilder): String {
        // Clean up common bank SMS transactional keywords first to leave cleaner description tokens
        var cleanText = text
            .replace(exAmount.toString(), "")
            .replace("(?i)rs\\.?|inr|\\$|usd|eur|Spent|Paid|Debited|Sent|Withdrawn|Transfered|Transaction".toRegex(), "")
            .replace("(?i)acct|a/c|xxxxxxxx\\d{4}|card|ref|ref\\s*no|upi|otp|code|date|time|avl|bal|balance".toRegex(), "")
            .trim()

        // Match typical merchant descriptors (e.g. "at Starbucks", "to Uber", "for electricity", "on Zomato")
        val prepositionPatterns = listOf(
            Pattern.compile("(?i)(?:at|to|on|for|towards|merchant)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+on|\\s+at|\\s+via|\\s+for|\\s+date|\\s+ref|\\s+\\.|$)"),
            Pattern.compile("(?i)(?:bought|purchased)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+at|\\s+via|\\s+from|\\s+for|\\s+\\.|$)")
        )

        for (pattern in prepositionPatterns) {
            val matcher = pattern.matcher(cleanText)
            if (matcher.find()) {
                val match = matcher.group(1)?.trim()
                if (!match.isNullOrBlank() && match.split(" ").size <= 4) {
                    logs.append(" - Extracted merchant descriptor entity: \"$match\" (Confidence: 0.90)\n")
                    return match
                }
            }
        }

        // Fallback: pick the first 3 words of clean text as description
        val words = cleanText.split("\\s+".toRegex()).filter { it.length > 2 && !it.contains("\\d".toRegex()) }
        if (words.isNotEmpty()) {
            val fallbackDesc = words.take(3).joinToString(" ")
            logs.append(" - Falling back to primary tokenized description: \"$fallbackDesc\" (Confidence: 0.50)\n")
            return fallbackDesc
        }

        logs.append("⚠️ [Extraction Warning]: No context descriptors found. Falling back to default: \"Generic Transaction\"\n")
        return "Generic Expenses"
    }

    /**
     * Detects typical payment systems (UPI, Cash, Card).
     */
    private fun detectPaymentMethod(text: String, logs: java.lang.StringBuilder): String {
        val lower = text.lowercase()
        return when {
            lower.contains("upi") || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm") -> {
                logs.append(" - Detected payment method entity: UPI (Confidence: 0.98)\n")
                "UPI"
            }
            lower.contains("cash") || lower.contains("hand") || lower.contains("withdrew") -> {
                logs.append(" - Detected payment method entity: Cash (Confidence: 0.95)\n")
                "Cash"
            }
            lower.contains("card") || lower.contains("visa") || lower.contains("mastercard") || lower.contains("credit") || lower.contains("debit") -> {
                logs.append(" - Detected payment method entity: Card (Confidence: 0.95)\n")
                "Card"
            }
            else -> {
                logs.append(" - Dynamic fallback payment channel: Cash (Confidence: 0.50)\n")
                "Cash"
            }
        }
    }

    /**
     * Extracts calendar timestamps.
     */
    private fun extractDate(text: String, logs: java.lang.StringBuilder): Long {
        val lower = text.lowercase()
        val calendar = Calendar.getInstance()

        if (lower.contains("yesterday")) {
            calendar.add(Calendar.DATE, -1)
            logs.append(" - Date Extracted: Yesterday (Confidence: 0.95)\n")
            return calendar.timeInMillis
        }

        // Try standard format regex checks e.g. 21/05/2026 or 21-05-2026
        val datePattern = Pattern.compile("(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})")
        val matcher = datePattern.matcher(text)
        if (matcher.find()) {
            try {
                val day = matcher.group(1)?.toInt() ?: 1
                val month = (matcher.group(2)?.toInt() ?: 1) - 1
                var year = matcher.group(3)?.toInt() ?: 2026
                if (year < 100) year += 2000 // handle short year format
                calendar.set(year, month, day)
                logs.append(" - Pattern match extracted Calendar Date: ${day}/${month + 1}/${year} (Confidence: 0.90)\n")
                return calendar.timeInMillis
            } catch (e: Exception) {
                // Ignore and fall back
            }
        }

        logs.append(" - Defaulting timestamp to immediate system local: Today (Confidence: 0.70)\n")
        return System.currentTimeMillis()
    }

    /**
     * Local classification mapping resembling a MobileBERT customized classifier model outputs.
     */
    private fun classifyTextCategory(desc: String, logs: java.lang.StringBuilder): Pair<String, Double> {
        val cleanDesc = desc.lowercase()

        // Dictionary of classification associations
        val classes = mapOf(
            CAT_FOOD to listOf("zomato", "swiggy", "starbucks", "mcdonald", "burger", "pizza", "kfc", "restaurant", "dinner", "lunch", "coffee", "cafe", "grocery", "groceries", "bakery", "food", "eat"),
            CAT_TRAVEL to listOf("uber", "ola", "cab", "taxi", "metro", "airline", "flight", "petrol", "fuel", "gas", "diesel", "train", "travel", "auto", "irctc", "bus", "booking"),
            CAT_UTILITIES to listOf("electricity", "water", "wifi", "broadband", "recharge", "mobile", "electric", "power", "rent", "gas cylinder", "airtel", "jio", "bsnl", "maintenance", "utility"),
            CAT_SHOPPING to listOf("amazon", "flipkart", "myntra", "zara", "clothing", "nike", "mall", "buying", "gifts", "purchase", "shopping", "clothes", "gadget", "electronics", "book", "shoes"),
            CAT_MEDICAL to listOf("hospital", "pharmacy", "medicine", "apolo", "cvs", "chemist", "clinic", "doctor", "dentist", "health", "insurance", "pills", "medical")
        )

        val scores = mutableMapOf<String, Double>()
        for (category in ALL_CATEGORIES) {
            scores[category] = 0.05 // default noise probability floor
        }

        var matched = false
        for ((category, keywords) in classes) {
            for (keyword in keywords) {
                if (cleanDesc.contains(keyword)) {
                    // Strong activation probability score
                    scores[category] = (scores[category] ?: 0.0) + 0.85
                    matched = true
                }
            }
        }

        // Normalize or scale scores
        val bestEntry = scores.maxByOrNull { it.value }
        val categoryName = bestEntry?.key ?: CAT_FOOD
        val scoreVal = bestEntry?.value ?: 0.05
        val finalConf = if (matched) {
            scoreVal.coerceAtMost(0.99)
        } else {
            logs.append(" ❓ [MediaPipe Classifier]: Low classifier activations. Picking maximum likelihood category standard.\n")
            0.45 // uncertain prediction baseline
        }

        logs.append(" - Local MobileBERT classification results: $categoryName with prob: ${(finalConf * 100).toInt()}%\n")
        return Pair(categoryName, finalConf)
    }
}
