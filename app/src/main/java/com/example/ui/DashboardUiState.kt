package com.example.ui

import com.example.data.ExpenseEntity

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    
    object Empty : DashboardUiState
    
    data class Success(
        val expenses: List<ExpenseEntity>,
        val totalSpent: Double,
        val budget: Double,
        val balance: Double,
        val categoryBreakdown: Map<String, Double>, // Category -> Amount
        val paymentMethodBreakdown: Map<String, Double> // PaymentMethod -> Amount
    ) : DashboardUiState
}
