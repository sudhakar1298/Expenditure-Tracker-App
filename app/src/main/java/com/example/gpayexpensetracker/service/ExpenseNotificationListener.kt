package com.example.gpayexpensetracker.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.gpayexpensetracker.data.BackendSyncUtility
import com.example.gpayexpensetracker.data.CategoryStore
import com.example.gpayexpensetracker.data.DefaultDataRepository
import com.example.gpayexpensetracker.data.ExpenseCategories
import com.example.gpayexpensetracker.data.TransactionDatabase
import com.example.gpayexpensetracker.data.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExpenseNotificationListener : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private val repository by lazy {
        val database = TransactionDatabase.getDatabase(applicationContext)
        DefaultDataRepository(database.transactionDao)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        // Filter for Google Pay package. GPay India is "com.google.android.apps.nbu.paisa.user"
        // We also match common global payment app package substrings or debug notifications for testing.
        if (packageName != "com.google.android.apps.nbu.paisa.user" && packageName != applicationContext.packageName) {
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        Log.d(TAG, "Notification received from $packageName: Title: $title, Text: $text")

        // Parse GPay India debit transaction patterns
        val transaction = parseTransactionText(text)
        if (transaction != null) {
            transaction.category = CategoryStore.resolveSuggested(applicationContext, transaction.category)
            scope.launch {
                repository.insert(transaction)
                ExpenseReminderNotifications.showNewExpense(applicationContext, transaction)
                com.example.gpayexpensetracker.data.SheetsSyncUtility.sync(applicationContext, transaction)
                BackendSyncUtility.sync(applicationContext, transaction)
                Log.d(TAG, "Successfully inserted and synced transaction: ${transaction.amount} to ${transaction.merchant}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        private const val TAG = "ExpenseNotification"

        // Pattern matching: "Paid ₹150 to Ramen Shop" or "You sent Rs.500 to John"
        private val GPAY_DEBIT_REGEX = "(?i)(Paid|Sent|You sent|You paid)\\s*(?:₹|Rs\\.?)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*to\\s*(.+)".toRegex()

        fun parseTransactionText(text: String): TransactionEntity? {
            val match = GPAY_DEBIT_REGEX.find(text) ?: return null
            
            try {
                val amountStr = match.groupValues[2].replace(",", "")
                val amount = amountStr.toDoubleOrNull() ?: return null
                val merchant = match.groupValues[3].trim()
                
                val category = ExpenseCategories.suggest(merchant)
                
                return TransactionEntity(
                    amount,
                    merchant,
                    category,
                    System.currentTimeMillis(),
                    "GPay",
                    text,
                    true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing transaction text: $text", e)
                return null
            }
        }

    }
}
