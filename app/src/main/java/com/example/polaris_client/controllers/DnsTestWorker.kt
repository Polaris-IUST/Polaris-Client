package com.example.polaris_client.controllers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.polaris_client.models.DnsTestResult
import com.example.polaris_client.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class DnsTestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val hostname = inputData.getString("hostname") ?: return@withContext Result.failure()
        val testId = inputData.getInt("test_id", 0)
        val latitude = inputData.getDouble("latitude", 0.0)
        val longitude = inputData.getDouble("longitude", 0.0)

        // Run DNS test
        val startTime = System.currentTimeMillis()
        try {
            InetAddress.getByName(hostname)
        } catch (e: Exception) {
            return@withContext Result.failure()
        }
        val endTime = System.currentTimeMillis()
        val responseTime = (endTime - startTime).toFloat()

        // Build result
        val currentTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
        val result = DnsTestResult(
            responseTime = responseTime,
            latitude = latitude,
            longitude = longitude,
            hostname = hostname,
            time = currentTime
        )

        // Upload result to server
        val uploadSuccess = uploadResult(result)
        if (uploadSuccess) Result.success() else Result.retry()
    }

    private fun uploadResult(result: DnsTestResult): Boolean {
        val tokenManager = TokenManager(applicationContext)
        val token = tokenManager.getToken() ?: return false
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("longitude", result.longitude)
            put("latitude", result.latitude)
            put("responsetime", result.responseTime)
            put("hostname", result.hostname)
            put("time", result.time)
        }
        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/dnsdata/create/")
            .addHeader("Authorization", "Token $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: IOException) {
            false
        }
    }
}