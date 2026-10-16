package com.example.gpayexpensetracker.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

object DeviceAuthManager {
    private const val KEY_ALIAS = "expense_tracker_device_signing_key"
    private const val PREFS_NAME = "DeviceAuthentication"
    private const val DEVICE_ID_KEY = "device_id"
    private const val PAIRED_KEY = "paired"

    fun isPaired(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PAIRED_KEY, false) && getPrivateKey() != null

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(DEVICE_ID_KEY, null) ?: run {
            val generated = "android-${UUID.randomUUID().toString().replace("-", "")}"
            check(prefs.edit().putString(DEVICE_ID_KEY, generated).commit())
            generated
        }
    }

    suspend fun pair(context: Context, pairingCode: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(pairingCode.trim().length >= 24) {
                    "Enter the pairing code configured in Render."
                }
                val keyPair = ensureKeyPair()
                val payload = JSONObject().apply {
                    put("device_id", deviceId(context))
                    put(
                        "public_key_der",
                        Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
                    )
                    put("pairing_code", pairingCode.trim())
                    put("label", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                }.toString().toByteArray(Charsets.UTF_8)
                val pairUrl = URL(
                    BackendSyncUtility.primaryExpenseApiUrl(context)
                        .removeSuffix("expenses/") + "auth/device/pair"
                )
                val connection = pairUrl.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(payload) }
                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        val message = runCatching {
                            JSONObject(detail ?: "{}").optString("detail")
                        }.getOrNull().orEmpty()
                        error(message.ifBlank { "Pairing failed with HTTP $responseCode" })
                    }
                    val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(PAIRED_KEY, true).commit()
                    check(stored) { "Could not save the paired-device status" }
                } finally {
                    connection.disconnect()
                }
            }
        }

    fun signIfPaired(
        context: Context,
        connection: HttpURLConnection,
        url: URL,
        method: String,
        body: ByteArray = ByteArray(0)
    ) {
        if (!isPaired(context)) return
        val privateKey = getPrivateKey() ?: return
        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val uri = URI(url.toString())
        val target = buildString {
            append(uri.rawPath)
            if (!uri.rawQuery.isNullOrEmpty()) append('?').append(uri.rawQuery)
        }
        val bodyHash = MessageDigest.getInstance("SHA-256")
            .digest(body).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val canonical = listOf(method.uppercase(), target, timestamp, nonce, bodyHash)
            .joinToString("\n").toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(canonical)
            sign()
        }
        connection.setRequestProperty("X-Device-ID", deviceId(context))
        connection.setRequestProperty("X-Timestamp", timestamp)
        connection.setRequestProperty("X-Nonce", nonce)
        connection.setRequestProperty(
            "X-Signature", Base64.encodeToString(signature, Base64.NO_WRAP)
        )
    }

    private fun ensureKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existingPrivate = keyStore.getKey(KEY_ALIAS, null) as? java.security.PrivateKey
        val existingPublic = keyStore.getCertificate(KEY_ALIAS)?.publicKey
        if (existingPrivate != null && existingPublic != null) {
            return KeyPair(existingPublic, existingPrivate)
        }
        return KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
        ).apply {
            initialize(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
        }.generateKeyPair()
    }

    private fun getPrivateKey(): java.security.PrivateKey? = runCatching {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .getKey(KEY_ALIAS, null) as? java.security.PrivateKey
    }.getOrNull()
}
