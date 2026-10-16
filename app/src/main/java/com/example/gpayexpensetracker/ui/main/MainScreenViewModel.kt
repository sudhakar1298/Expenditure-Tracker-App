package com.example.gpayexpensetracker.ui.main

import android.content.Context
import android.util.Log
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpayexpensetracker.data.BackendSyncUtility
import com.example.gpayexpensetracker.data.CategoryStore
import com.example.gpayexpensetracker.data.DataRepository
import com.example.gpayexpensetracker.data.DeviceAuthManager
import com.example.gpayexpensetracker.data.ExpenseCategories
import com.example.gpayexpensetracker.data.SheetsSyncUtility
import com.example.gpayexpensetracker.data.TransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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

    private val _categories = MutableStateFlow(CategoryStore.load(appContext))
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _googleSheetsUrl = MutableStateFlow(sharedPrefs.getString("google_sheets_url", "") ?: "")
    val googleSheetsUrl: StateFlow<String> = _googleSheetsUrl.asStateFlow()

    private val _backendApiUrl = MutableStateFlow(
        sharedPrefs.getString("backend_api_url", null)
            ?.takeUnless { it.isBlank() }
            ?: BackendSyncUtility.DEFAULT_BACKEND_BASE_URL
    )
    val backendApiUrl: StateFlow<String> = _backendApiUrl.asStateFlow()
    val syncStatus: StateFlow<String> = BackendSyncUtility.status.asStateFlow()

    private val _isDevicePaired = MutableStateFlow(DeviceAuthManager.isPaired(appContext))
    val isDevicePaired: StateFlow<Boolean> = _isDevicePaired.asStateFlow()

    private val _devicePairingStatus = MutableStateFlow<String?>(null)
    val devicePairingStatus: StateFlow<String?> = _devicePairingStatus.asStateFlow()

    private val _isNotificationServiceEnabled = MutableStateFlow(false)
    val isNotificationServiceEnabled: StateFlow<Boolean> = _isNotificationServiceEnabled.asStateFlow()

    init {
        Log.d("MainScreenViewModel", "Loaded URL from SharedPreferences: ${_googleSheetsUrl.value}")
        checkNotificationPermission()
        refreshFromBackend()
        viewModelScope.launch {
            while (true) {
                delay(15000)
                BackendSyncUtility.retryPending(appContext)
            }
        }
    }

    fun refreshFromBackend() {
        BackendSyncUtility.fetchRemoteExpensesFromBackend(appContext, repository)
    }

    fun pairThisDevice(pairingCode: String, onFinished: (Boolean) -> Unit) {
        _devicePairingStatus.value = "Pairing this phone..."
        viewModelScope.launch {
            val result = DeviceAuthManager.pair(appContext, pairingCode)
            result.onSuccess {
                _isDevicePaired.value = true
                _devicePairingStatus.value = "This phone is paired securely."
                refreshFromBackend()
                BackendSyncUtility.retryPending(appContext)
                onFinished(true)
            }.onFailure { error ->
                _devicePairingStatus.value = error.message ?: "Could not pair this phone."
                onFinished(false)
            }
        }
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

    fun setBackendApiUrl(url: String) {
        _backendApiUrl.value = url.trim()
        sharedPrefs.edit().putString("backend_api_url", url.trim()).apply()
        refreshFromBackend()
    }

    fun addManualTransaction(
        amount: Double,
        merchant: String,
        category: String,
        reason: String,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val transaction = TransactionEntity(
                    amount,
                    resolveManualMerchant(merchant, reason),
                    category,
                    System.currentTimeMillis(),
                    "Manual",
                    "Manually entered"
                )
                transaction.reason = reason.trim().takeUnless { it.isBlank() }
                repository.insert(transaction)
                onResult(null)

                // The expense is already safe in Room. Remote delivery must never
                // be able to terminate the UI or undo a successful local save.
                viewModelScope.launch {
                    try {
                        SheetsSyncUtility.sync(appContext, transaction)
                        BackendSyncUtility.sync(appContext, transaction)
                    } catch (error: Exception) {
                        Log.e("MainScreenViewModel", "Manual expense sync failed", error)
                        BackendSyncUtility.status.value = "Saved on phone; upload pending"
                    }
                }
            } catch (error: Exception) {
                Log.e("MainScreenViewModel", "Manual expense save failed", error)
                onResult("Could not save the expense. Please try again.")
            }
        }
    }

    fun updateTransactionCategory(transaction: TransactionEntity, newCategory: String) {
        viewModelScope.launch {
            transaction.category = newCategory
            repository.update(transaction)
            BackendSyncUtility.sync(appContext, transaction)
        }
    }

    fun addCategory(rawName: String): String? {
        val name = CategoryStore.cleanName(rawName)
        val error = validateCategoryName(name)
        if (error != null) return error

        val updated = _categories.value + name
        CategoryStore.save(appContext, updated)
        CategoryStore.recordAdd(appContext, name)
        _categories.value = updated
        return null
    }

    fun renameCategory(oldName: String, rawName: String): String? {
        if (oldName == ExpenseCategories.OTHER) return "The Other category is required as a fallback."
        val name = CategoryStore.cleanName(rawName)
        val error = validateCategoryName(name, oldName)
        if (error != null) return error
        if (name == oldName) return null

        val updated = _categories.value.map { if (it == oldName) name else it }
        CategoryStore.save(appContext, updated)
        CategoryStore.recordRename(appContext, oldName, name)
        _categories.value = updated
        moveTransactionsToCategory(oldName, name)
        return null
    }

    fun deleteCategory(name: String): String? {
        if (name == ExpenseCategories.OTHER) return "The Other category cannot be removed."
        if (_categories.value.none { it == name }) return "Category not found."

        val updated = _categories.value.filterNot { it == name }
        CategoryStore.save(appContext, updated)
        CategoryStore.recordDelete(appContext, name)
        _categories.value = updated
        moveTransactionsToCategory(name, ExpenseCategories.OTHER)
        return null
    }

    private fun validateCategoryName(name: String, currentName: String? = null): String? {
        if (name.isBlank()) return "Enter a category name."
        if (name.length > 32) return "Use 32 characters or fewer."
        val normalized = ExpenseCategories.normalize(name)
        if (normalized != name) {
            return "Use \"$normalized\" instead; \"$name\" is a legacy category name."
        }
        if (_categories.value.any {
                !it.equals(currentName, ignoreCase = true) && it.equals(name, ignoreCase = true)
            }
        ) return "That category already exists."
        return null
    }

    private fun moveTransactionsToCategory(oldName: String, newName: String) {
        viewModelScope.launch {
            repository.allTransactions.first()
                .filter { it.category.equals(oldName, ignoreCase = true) }
                .forEach { transaction ->
                    try {
                        transaction.category = newName
                        repository.update(transaction)
                        BackendSyncUtility.sync(appContext, transaction)
                    } catch (error: Exception) {
                        Log.e("MainScreenViewModel", "Could not recategorize transaction", error)
                        BackendSyncUtility.status.value = "Category saved; transaction sync pending"
                    }
                }
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            try {
                repository.delete(transaction)
                BackendSyncUtility.delete(appContext, transaction)
            } catch (error: Exception) {
                Log.e("MainScreenViewModel", "Expense deletion failed", error)
                BackendSyncUtility.status.value = "Could not delete activity"
            }
        }
    }

    fun clearPendingTransactions(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val backendCleared = BackendSyncUtility.clearPending(appContext)
                repository.deletePending()
                onResult(
                    if (backendCleared) null
                    else "Pending drafts were cleared from this phone. Backend cleanup is still pending."
                )
            } catch (error: Exception) {
                Log.e("MainScreenViewModel", "Pending draft cleanup failed", error)
                onResult("Could not clear pending drafts. Please try again.")
            }
        }
    }

    fun finalizePendingTransaction(transaction: TransactionEntity, merchant: String, category: String, reason: String) {
        viewModelScope.launch {
            transaction.merchant = merchant
            transaction.category = category
            transaction.reason = reason.trim().takeUnless { it.isBlank() }
            transaction.isPending = false
            repository.update(transaction)
            SheetsSyncUtility.sync(appContext, transaction)
            BackendSyncUtility.sync(appContext, transaction)
        }
    }
}

fun resolveManualMerchant(merchant: String, reason: String): String =
    merchant.trim().ifBlank { reason.trim().ifBlank { "Manual expense" } }
