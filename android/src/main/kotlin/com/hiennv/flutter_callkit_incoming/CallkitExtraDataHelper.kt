package com.hiennv.flutter_callkit_incoming

import android.util.Log
import android.os.Bundle
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object CallkitExtraDataHelper {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun processData(action: String, data: Bundle) {
        val extra = data.getSerializable(CallkitConstants.EXTRA_CALLKIT_EXTRA) as? Map<String, Any> ?: return
        val refreshToken = extra["refreshToken"] as? String ?: return
        val token = extra["token"] as? String ?: return
        val statusApi = extra["statusApi"] as? String ?: return
        val tokenApi = extra["tokenApi"] as? String ?: return

        Log.d("CallkitExtraDataHelper", "Received extra data")

        when (action) {
            CallkitConstants.ACTION_CALL_DECLINE -> {
                val body = extra["rejected"] as? Map<String, Any> ?: return
                coroutineScope.launch {
                    sendDeclinedRequest(body, token, refreshToken, statusApi, tokenApi, isRetry = false)
                }
            }
        }
    }

    private suspend fun sendDeclinedRequest(
        body: Map<String, Any>,
        token: String,
        refreshToken: String?,
        statusApi: String,
        tokenApi: String,
        isRetry: Boolean
    ) {
        try {
            val response = sendHttpPost(statusApi, token, body)
            Log.d("CallkitHttpHelper", "Call declined successfully: $response")
        } catch (e: Exception) {
            if (e.message?.contains("401") == true && refreshToken != null && !isRetry) {
                Log.d("CallkitHttpHelper", "Token expired, refreshing...")
                val newToken = refreshAuthToken(refreshToken, tokenApi)
                if (newToken != null) {
                    sendDeclinedRequest(body, newToken, refreshToken, statusApi, tokenApi, isRetry = true)
                } else {
                    Log.e("CallkitHttpHelper", "Failed to refresh token")
                }
            } else {
                Log.e("CallkitHttpHelper", "Failed to decline call: ${e.message}", e)
            }
        }
    }

    private suspend fun sendHttpPost(url: String, authToken: String, requestBody: Map<String, Any>): String {
        return withContext(Dispatchers.IO) {
            val urlConnection = URL(url).openConnection() as HttpURLConnection
            urlConnection.requestMethod = "POST"
            urlConnection.setRequestProperty("Content-Type", "application/json")
            urlConnection.setRequestProperty("Authorization", "Bearer ${authToken}")
            urlConnection.doOutput = true

            val jsonBody = JSONObject(requestBody).toString()
            urlConnection.outputStream.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            }

            val responseCode = urlConnection.responseCode
            if (responseCode == 401) {
                throw Exception("401 Unauthorized")
            } else if (responseCode in 200..299) {
                urlConnection.inputStream.bufferedReader().use { it.readText() }
            } else {
                throw Exception("HTTP error code: $responseCode")
            }
        }
    }

    private suspend fun refreshAuthToken(refreshToken: String, tokenUrl: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = sendHttpPost(tokenUrl, "", mapOf("refresh_token" to refreshToken))
                val json = JSONObject(response)
                json.optString("access_token", null)
            } catch (e: Exception) {
                Log.e("CallkitHttpHelper", "Token refresh failed: ${e.message}", e)
                null
            }
        }
    }
}
