package com.example.gpayexpensetracker.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BackendSyncUtility {
    private const val TAG = "BackendSync"
    const val DEFAULT_BACKEND_BASE_URL = "https://expenditure-tracker-app.onrender.com"
    val status = MutableStateFlow("Sync not checked")
    private val uploadLock = Mutex()

    private fun getCandidateUrls(context: Context): List<String> {
        val sharedPrefs = context.applicationContext.getSharedPreferences("GPayTrackerPrefs", Context.MODE_PRIVATE)
        val configuredUrl = sharedPrefs.getString("backend_api_url", "")?.trim()
        val customUrl = configuredUrl
            ?.takeUnless { it.isBlank() }
            ?.trimEnd('/')
            ?.plus("/")
        
        val list = mutableListOf<String>()
        if (!customUrl.isNullOrBlank()) {
            val base = customUrl.trimEnd('/')
            list.add(when {
                base.endsWith("/api/v1/expenses") -> "$base/"
                base.endsWith("/api/v1") -> "$base/expenses/"
                else -> "$base/api/v1/expenses/"
            })
            return list
        }
        // The hosted backend is the default. LAN addresses remain available as
        // development fallbacks when the user has not configured another URL.
        list.add("$DEFAULT_BACKEND_BASE_URL/api/v1/expenses/")
        list.add("http://192.168.1.37:8000/api/v1/expenses/")
        list.add("http://10.0.2.2:8000/api/v1/expenses/")
        return list.distinct()
    }

    suspend fun sync(context: Context, transaction: TransactionEntity): Boolean = withContext(Dispatchers.IO) {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        // Keep the ID independent of merchant/category so an SMS draft that is
        // later finalized updates the same logical transaction on retry.
        val transactionId = "android-${transaction.timestamp}-${transaction.amount}-${transaction.sourceApp}"
        val payload = JSONObject().apply {
            put("transaction_id", transactionId)
            put("amount", transaction.amount)
            put("merchant", transaction.merchant)
            put("reason", transaction.reason?.takeUnless { it.isBlank() } ?: JSONObject.NULL)
            put("category", ExpenseCategories.normalize(transaction.category))
            put("source", transaction.sourceApp)
            put("is_pending", transaction.isPending)
            put("transaction_date", isoFormat.format(Date(transaction.timestamp)))
            put("raw_message", transaction.rawText ?: "")
            put("currency", "INR")
        }.toString()

        // Persist before attempting delivery; process death or offline use must
        // not discard a saved expense. A retry uses the same transaction ID.
        val queue = context.getSharedPreferences("BackendOutbox", Context.MODE_PRIVATE)
        if (!synchronized(queue) { queue.edit().putString(transactionId, payload).commit() }) {
            status.value = "Saved on phone; could not queue upload"
            return@withContext false
        }
        retryPending(context)
        !queue.contains(transactionId)
    }

    suspend fun delete(context: Context, transaction: TransactionEntity): Boolean = withContext(Dispatchers.IO) {
        val transactionId = "android-${transaction.timestamp}-${transaction.amount}-${transaction.sourceApp}"
        val queue = context.getSharedPreferences("BackendOutbox", Context.MODE_PRIVATE)
        synchronized(queue) { queue.edit().remove(transactionId).commit() }

        val encodedId = URLEncoder.encode(transactionId, Charsets.UTF_8.name())
        for (backendUrl in getCandidateUrls(context)) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("${backendUrl}by-transaction/$encodedId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    status.value = "Activity deleted"
                    return@withContext true
                }
                Log.w(TAG, "Backend delete returned HTTP $responseCode")
            } catch (error: Exception) {
                Log.e(TAG, "Backend delete failed", error)
            } finally {
                connection?.disconnect()
            }
        }
        status.value = "Deleted from phone; server deletion pending"
        false
    }

    suspend fun clearPending(context: Context): Boolean = withContext(Dispatchers.IO) {
        var backendCleared = false
        for (backendUrl in getCandidateUrls(context)) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("${backendUrl}pending").openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                if (connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    backendCleared = true
                    break
                }
                Log.w(TAG, "Backend clear pending returned HTTP ${connection.responseCode}")
            } catch (error: Exception) {
                Log.e(TAG, "Backend clear pending failed", error)
            } finally {
                connection?.disconnect()
            }
        }
        removeQueuedPending(context)
        status.value = if (backendCleared) {
            "Pending drafts cleared"
        } else {
            "Pending drafts cleared from phone; backend cleanup pending"
        }
        backendCleared
    }

    private fun removeQueuedPending(context: Context) {
        val queue = context.getSharedPreferences("BackendOutbox", Context.MODE_PRIVATE)
        synchronized(queue) {
            val editor = queue.edit()
            queue.all.forEach { (id, value) ->
                val payload = value as? String ?: return@forEach
                if (runCatching { JSONObject(payload).optBoolean("is_pending", false) }.getOrDefault(false)) {
                    editor.remove(id)
                }
            }
            editor.commit()
        }
    }

    suspend fun retryPending(context: Context) = withContext(Dispatchers.IO) {
        uploadLock.withLock {
            val queue = context.getSharedPreferences("BackendOutbox", Context.MODE_PRIVATE)
            if (queue.all.isEmpty()) return@withLock
            for ((id, value) in queue.all) {
                val payload = value as? String ?: continue
                if (!post(context, payload)) return@withLock
                // A newer edit may have been queued while this request ran.
                synchronized(queue) {
                    if (queue.getString(id, null) == payload) queue.edit().remove(id).commit()
                }
            }
            status.value = if (queue.all.isEmpty()) "Backend sync complete" else "Upload pending"
        }
    }

    private fun post(context: Context, payload: String): Boolean {
        status.value = "Uploading saved expenses…"
        val candidateUrls = getCandidateUrls(context)

        for (backendUrlStr in candidateUrls) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(backendUrlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val responseCode = conn.responseCode
                Log.d(TAG, "Backend POST response code: $responseCode via $backendUrlStr")
                if (responseCode in 200..299) return true
                status.value = "Saved on phone; server returned HTTP $responseCode. Upload pending."
            } catch (e: Exception) {
                status.value = "Saved on phone; backend unreachable. Check Backend API URL. Upload pending."
                Log.e(TAG, "Failed POST sync via $backendUrlStr", e)
            } finally {
                conn?.disconnect()
            }
        }
        return false
    }

    /**
     * Pulls remote expenses from FastAPI backend and syncs them down to local Room DB.
     */
    private fun fetchAll(url: String): JSONArray {
        val result = JSONArray()
        var skip = 0
        while (true) {
            val connection = URL("$url?skip=$skip&limit=100").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                check(connection.responseCode == 200) { "Expense fetch HTTP ${connection.responseCode}" }
                val page = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                for (i in 0 until page.length()) result.put(page.getJSONObject(i))
                if (page.length() < 100) return result
                skip += page.length()
            } finally {
                connection.disconnect()
            }
        }
    }

    fun fetchRemoteExpensesFromBackend(context: Context, repository: DataRepository) {
        val candidateUrls = getCandidateUrls(context)

        CoroutineScope(Dispatchers.IO).launch {
            retryPending(context)
            for (backendUrlStr in candidateUrls) {
                try {
                    val url = URL(backendUrlStr)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000

                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        conn.disconnect()
                        val jsonArray = fetchAll(backendUrlStr)
                        if (context.getSharedPreferences("BackendOutbox", Context.MODE_PRIVATE).all.isEmpty()) {
                            status.value = "Backend connected"
                        }
                        val entityList = mutableListOf<TransactionEntity>()

                        for (i in 0 until jsonArray.length()) {
                            val item = jsonArray.getJSONObject(i)
                            val amount = item.optDouble("amount", 0.0)
                            val merchant = item.optString("merchant", "Expense")
                            val category = ExpenseCategories.normalize(item.optString("category", ExpenseCategories.OTHER))
                            val source = item.optString("source", "Web")
                            val isPending = item.optBoolean("is_pending", false)
                            val rawMsg = item.optString("raw_message", "")
                            val reason = item.optString("reason", "")
                                .takeUnless { item.isNull("reason") || it.isBlank() || it == "null" }
                            
                            val dateStr = item.optString("transaction_date", "")
                            var timestamp = System.currentTimeMillis()
                            if (dateStr.isNotBlank()) {
                                try {
                                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                                    val parsedDate = sdf.parse(dateStr)
                                    if (parsedDate != null) timestamp = parsedDate.time
                                } catch (_: Exception) {
                                    // fallback
                                }
                            }

                            val entity = TransactionEntity(
                                amount,
                                merchant,
                                category,
                                timestamp,
                                source,
                                rawMsg,
                                isPending
                            )
                            entity.reason = reason

                            entityList.add(entity)
                        }

                        run {
                            // Never delete locally saved/offline entries on a pull.
                            val local = repository.allTransactions.first()
                            // Recover entries saved by older versions with no outbox.
                            for (saved in local) {
                                if (entityList.none { it.amount == saved.amount &&
                                    it.merchant == saved.merchant &&
                                    kotlin.math.abs(it.timestamp - saved.timestamp) < 1000 }) {
                                    if (!sync(context, saved)) break
                                }
                            }
                            val missing = entityList.filter { remote ->
                                local.none { it.amount == remote.amount &&
                                    it.merchant == remote.merchant &&
                                    kotlin.math.abs(it.timestamp - remote.timestamp) < 1000 }
                            }
                            repository.insertAll(missing)
                        }
                        conn.disconnect()
                        break // Success!
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed GET pull via $backendUrlStr", e)
                }
            }
        }
    }
}
