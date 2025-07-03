package com.example.polaris_client.controllers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.polaris_client.models.HttpTestResult
import com.example.polaris_client.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class HttpTestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val networkTestService = NetworkTestService(applicationContext)
        val testResult = networkTestService.runHttpTest(url)
        if (testResult != null) {
            val uploadSuccess = uploadResult(testResult)
            if (uploadSuccess) Result.success() else Result.retry()
        } else {
            Result.failure()
        }
    }

    private fun uploadResult(testResult: HttpTestResult): Boolean {
        val tokenManager = TokenManager(applicationContext)
        val token = tokenManager.getToken() ?: return false
        val client = OkHttpClient()
        val currentTime = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_INSTANT)
        val json = JSONObject().apply {
            put("longitude", testResult.longitude)
            put("latitude", testResult.latitude)
            put("responsetime", testResult.responseTime)
            put("hostname", testResult.hostname)
            put("time", currentTime)
        }
        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/httpresponse/create/")
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