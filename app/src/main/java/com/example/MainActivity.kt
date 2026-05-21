package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.DashboardScreen
import com.example.ui.ExpenseViewModel
import com.example.ui.ExpenseViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup edge-to-edge full bleed
        enableEdgeToEdge()

        // Instantiate local persistent ViewModel with the factory provider
        val factory = ExpenseViewModelFactory(applicationContext)
        val viewModel = ViewModelProvider(this, factory)[ExpenseViewModel::class.java]

        setContent {
            MyApplicationTheme {
                DashboardScreen(viewModel = viewModel)
            }
        }
    }
}
