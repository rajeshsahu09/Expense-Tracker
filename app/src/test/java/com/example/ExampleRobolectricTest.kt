package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ExpenseRepository
import com.example.ml.OnDeviceProcessor
import com.example.ui.ExpenseViewModel
import com.example.ui.DashboardUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Vault Expense Tracker", appName)
  }

  @Test
  fun testExpenseViewModelIntegration() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    // In-Memory Database setup
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val dao = db.expenseDao()
    val repository = ExpenseRepository(dao)
    val mlProcessor = OnDeviceProcessor()
    
    val viewModel = ExpenseViewModel(repository, mlProcessor)
    
    runBlocking {
      // Need a small yield/delay for flow collection if needed, or get first uiState
      val state = viewModel.uiState.first()
      // Initial standard state when database is loading/empty
      assertNotNull(state)
    }
    
    db.close()
  }
}

