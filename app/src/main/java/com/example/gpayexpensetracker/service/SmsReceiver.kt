package com.example.gpayexpensetracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.gpayexpensetracker.data.BackendSyncUtility
import com.example.gpayexpensetracker.data.CategoryStore
import com.example.gpayexpensetracker.data.DefaultDataRepository
import com.example.gpayexpensetracker.data.ExpenseCategories
import com.example.gpayexpensetracker.data.SheetsSyncUtility
import com.example.gpayexpensetracker.data.TransactionDatabase
import com.example.gpayexpensetracker.data.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null || intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val database = TransactionDatabase.getDatabase(context.applicationContext)
        val repository = DefaultDataRepository(database.transactionDao)

        val pendingResult = goAsync()
        scope.launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                val fullText = messages.joinToString(separator = "") { it.messageBody ?: "" }
                Log.d(TAG, "Incoming SMS: $fullText")

                val timestamp = messages.minOfOrNull { it.timestampMillis }
                    ?: System.currentTimeMillis()
                val transaction = parseSmsText(fullText, timestamp)
                if (transaction != null) {
                    transaction.category = CategoryStore.resolveSuggested(
                        context.applicationContext,
                        transaction.category
                    )
                    repository.insert(transaction)
                    SheetsSyncUtility.sync(context.applicationContext, transaction)
                    BackendSyncUtility.sync(context.applicationContext, transaction)
                    Log.d(TAG, "Inserted pending transaction: ₹${transaction.amount} from SMS and synced to backend")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"

        fun parseSmsText(
            text: String,
            timestamp: Long = System.currentTimeMillis()
        ): TransactionEntity? {
            val lower = text.lowercase()
            // We only look for debit/spent/sent transactions
            if (!lower.contains("debited") && !lower.contains("sent") && !lower.contains("spent") && !lower.contains("paid") && !lower.contains("tx of")) {
                return null
            }
            
            // Ignore credit/refund/reversal alerts to focus on expenditure
            if (lower.contains("credited") || lower.contains("received") || lower.contains("refunded") || lower.contains("reversal")) {
                return null
            }

            try {
                // Find currency-amount pattern anywhere in the text (e.g. Rs 78.00, INR 1,250.50, ₹150)
                val amountRegex = "(?i)(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)".toRegex()
                val amountMatch = amountRegex.find(text) ?: return null
                
                val amountStr = amountMatch.groupValues[1].replace(",", "")
                val amount = amountStr.toDoubleOrNull() ?: return null

                // Prefill merchant name if we find "to [MerchantName]" before punctuation or "UPI"
                val merchantRegex = "(?i)to\\s+([^.]+?)(?:\n|\\s+UPI|\\s+on|\\s+A/c|\\.\\s|$)".toRegex()
                val merchantMatch = merchantRegex.find(text)
                
                var merchant = merchantMatch?.groupValues[1]?.trim() ?: "Pending Details"
                
                // If merchant prefilled contains bank words or is blank, default it
                if (merchant.isBlank() || merchant.length > 40) {
                    merchant = "Pending Details"
                }

                return TransactionEntity(
                    amount,
                    merchant,
                    ExpenseCategories.suggest(merchant),
                    timestamp,
                    "SMS",
                    text,
                    true // isPending = true! Tells the UI to prompt for confirmation
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing SMS content: $text", e)
                return null
            }
        }
    }
}
