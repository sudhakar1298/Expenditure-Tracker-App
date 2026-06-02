package com.example.gpayexpensetracker.ui.main

import android.content.Context
import android.util.Log
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpayexpensetracker.data.DataRepository
import com.example.gpayexpensetracker.data.TransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainScreenViewModel(
    private val repository: DataRepository,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val sharedPrefs = appContext.getSharedPreferences("GPayTrackerPrefs", Context.MODE_PRIVATE)

    val allTransactions: StateFlow<List<TransactionEntity>> =
        repository.allTransactions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _monthlyBudget = MutableStateFlow(sharedPrefs.getFloat("monthly_budget", 10000f).toDouble())
    val monthlyBudget: StateFlow<Double> = _monthlyBudget.asStateFlow()

    private val _googleSheetsUrl = MutableStateFlow(sharedPrefs.getString("google_sheets_url", "") ?: "")
    val googleSheetsUrl: StateFlow<String> = _googleSheetsUrl.asStateFlow()

    private val _isNotificationServiceEnabled = MutableStateFlow(false)
    val isNotificationServiceEnabled: StateFlow<Boolean> = _isNotificationServiceEnabled.asStateFlow()

    init {
        Log.d("MainScreenViewModel", "Loaded URL from SharedPreferences: ${_googleSheetsUrl.value}")
        checkNotificationPermission()
    }

    fun checkNotificationPermission() {
        val packageName = appContext.packageName
        val flat = Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners")
        val enabled = flat != null && flat.contains(packageName)
        _isNotificationServiceEnabled.value = enabled
    }

    fun setMonthlyBudget(budget: Double) {
        _monthlyBudget.value = budget
        sharedPrefs.edit().putFloat("monthly_budget", budget.toFloat()).apply()
    }

    fun setGoogleSheetsUrl(url: String) {
        Log.d("MainScreenViewModel", "Saving URL to SharedPreferences: $url")
        _googleSheetsUrl.value = url
        sharedPrefs.edit().putString("google_sheets_url", url).apply()
    }

    fun addManualTransaction(amount: Double, merchant: String, category: String) {
        viewModelScope.launch {
            val transaction = TransactionEntity(
                amount,
                merchant,
                category,
                System.currentTimeMillis(),
                "Manual",
                "Manually entered"
            )
            repository.insert(transaction)
            com.example.gpayexpensetracker.data.SheetsSyncUtility.sync(appContext, transaction)
        }
    }

    fun updateTransactionCategory(transaction: TransactionEntity, newCategory: String) {
        viewModelScope.launch {
            transaction.category = newCategory
            repository.update(transaction)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.delete(transaction)
        }
    }

    fun finalizePendingTransaction(transaction: TransactionEntity, merchant: String, category: String) {
        viewModelScope.launch {
            transaction.merchant = merchant
            transaction.category = category
            transaction.isPending = false
            repository.update(transaction)
            com.example.gpayexpensetracker.data.SheetsSyncUtility.sync(appContext, transaction)
        }
    }
}
