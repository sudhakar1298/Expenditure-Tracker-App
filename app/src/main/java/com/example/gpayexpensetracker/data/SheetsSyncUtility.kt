package com.example.gpayexpensetracker.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

object SheetsSyncUtility {
    private const val TAG = "SheetsSync"

    fun sync(context: Context, transaction: TransactionEntity) {
        val sharedPrefs = context.applicationContext.getSharedPreferences("GPayTrackerPrefs", Context.MODE_PRIVATE)
        val urlStr = sharedPrefs.getString("google_sheets_url", "") ?: ""
        
        if (urlStr.isBlank()) {
            Log.d(TAG, "Sync skipped: Google Sheets Web App URL is not set.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val escapedMerchant = transaction.merchant.replace("\"", "\\\"").replace("\n", " ")
                val json = """
                    {
                        "amount": ${transaction.amount},
                        "merchant": "$escapedMerchant",
                        "category": "${transaction.category}",
                        "timestamp": ${transaction.timestamp},
                        "sourceApp": "${transaction.sourceApp}"
                    }
                """.trimIndent()

                var url = URL(urlStr)
                var conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false // Disable automatic follow (which converts POST to GET)
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")

                conn.outputStream.use { os ->
                    os.write(json.toByteArray())
                }

                var responseCode = conn.responseCode
                Log.d(TAG, "Initial POST response code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Sync response: $responseText")
                } else if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                    val redirectUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    
                    if (!redirectUrl.isNullOrBlank()) {
                        Log.d(TAG, "Manually following redirect to: $redirectUrl")
                        url = URL(redirectUrl)
                        conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        
                        responseCode = conn.responseCode
                        Log.d(TAG, "Redirected GET response code: $responseCode")
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                            Log.d(TAG, "Sync response: $responseText")
                        }
                    }
                }

                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync transaction: ${transaction.merchant} (₹${transaction.amount})", e)
            }
        }
    }
}
