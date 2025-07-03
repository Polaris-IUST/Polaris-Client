package com.example.polaris_client.controllers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.polaris_client.models.SmsTestResult
import com.example.polaris_client.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SmsTestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val phoneNumber = inputData.getString("phone_number") ?: return@withContext Result.failure()
        val message = inputData.getString("message") ?: return@withContext Result.failure()
        val testId = inputData.getInt("test_id", 0)

        // Run the SMS test (reuse your NetworkTestService logic)
        val networkTestService = NetworkTestService(applicationContext)
        var result: SmsTestResult? = null
        var error: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        networkTestService.runSmsTest(phoneNumber, message, object : NetworkTestService.SmsDeliveryListener {
            override fun onSmsDelivered(r: SmsTestResult) {
                result = r
                latch.countDown()
            }
            override fun onSmsDeliveryFailed(e: String) {
                error = e
                latch.countDown()
            }
        })
        latch.await()
        if (result != null) {
            // Upload result to server
            val uploadSuccess = uploadResult(result!!)
            if (uploadSuccess) Result.success() else Result.retry()
        } else {
            // Optionally, you could return Result.failure() or retry
            Result.failure()
        }
    }

    private fun uploadResult(result: SmsTestResult): Boolean {
        val tokenManager = TokenManager(applicationContext)
        val token = tokenManager.getToken() ?: return false
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("longitude", result.longitude)
            put("latitude", result.latitude)
            put("deliveryTime", result.deliveryTime)
            put("phoneNumber", result.phoneNumber)
            put("time", result.time)
        }
        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/smstest/create/")
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