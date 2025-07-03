package com.example.polaris_client.controllers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.polaris_client.utils.TokenManager
import com.example.polaris_client.models.DownloadTestResult
import com.example.polaris_client.models.UploadTestResult
import com.example.polaris_client.models.MeasuredLatencyResult
import com.example.polaris_client.controllers.NetworkSpeedTestService
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
import java.util.concurrent.CountDownLatch

class SpeedTestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val networkSpeedTestService = NetworkSpeedTestService(applicationContext)
        val latch = CountDownLatch(1)
        var downloadResult: DownloadTestResult? = null
        var uploadResult: UploadTestResult? = null
        var latencyResult: MeasuredLatencyResult? = null
        var error: String? = null

        // Run full speed test (latency, jitter, download, upload)
        networkSpeedTestService.runFullSpeedTest(
            serverUrl = null,
            durationSec = 10,
            listener = object : NetworkSpeedTestService.SpeedTestListener {
                override fun onTestUpdate(status: String, progressPercent: Int) {}
                override fun onLatencyMeasured(latencyMs: Long) {}
                override fun onJitterMeasured(jitterMs: Float) {}
                override fun onDownloadComplete(speedMbps: Float) {}
                override fun onUploadComplete(speedMbps: Float) {}
                override fun onTestComplete() {
                    // Results are saved to DB and uploaded in NetworkSpeedTestService
                    latch.countDown()
                }
                override fun onTestFailed(e: String) {
                    error = e
                    latch.countDown()
                }
            }
        )
        latch.await()
        if (error == null) Result.success() else Result.failure()
    }
} 