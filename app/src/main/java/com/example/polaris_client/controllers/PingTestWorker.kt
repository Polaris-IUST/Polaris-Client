package com.example.polaris_client.controllers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.polaris_client.models.PingTestResult
import com.example.polaris_client.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class PingTestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val host = inputData.getString("host") ?: return@withContext Result.failure()
        val count = inputData.getInt("count", 5)
        val latitude = inputData.getDouble("latitude", 0.0)
        val longitude = inputData.getDouble("longitude", 0.0)

        // Run the ping test. Use your own implementation or NetworkTestService.
        val networkTestService = com.example.polaris_client.controllers.NetworkTestService(applicationContext)
        val testResult = networkTestService.runPingTest(host, count)
        if (testResult == null) return@withContext Result.failure()

        // Optionally, override coordinates here with inputData if you want
        val uploadSuccess = uploadResult(testResult.copy(latitude = latitude, longitude = longitude))
        if (uploadSuccess) Result.success() else Result.retry()
    }

    private fun uploadResult(result: PingTestResult): Boolean {
        val tokenManager = TokenManager(applicationContext)
        val token = tokenManager.getToken() ?: return false
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("longitude", result.longitude)
            put("latitude", result.latitude)
            put("avgResponseTime", result.avgResponseTime)
            put("suceessCountRate", result.successRate)
            put("time", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT))
        }
        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/pingtest/create/")
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