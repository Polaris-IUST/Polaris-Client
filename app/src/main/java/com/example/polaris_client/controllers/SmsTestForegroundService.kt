package com.example.polaris_client.controllers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.polaris_client.R
import com.example.polaris_client.models.SmsTestResult
import com.example.polaris_client.utils.TokenManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import android.util.Log

class SmsTestForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "SmsTestForegroundChannel"
        const val NOTIFICATION_ID = 1002
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_TEST_ID = "extra_test_id"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        val phoneNumber = intent?.getStringExtra(EXTRA_PHONE) ?: ""
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: ""
        val testId = intent?.getIntExtra(EXTRA_TEST_ID, 0) ?: 0

        // Start the actual SMS test logic (on a background thread)
        Thread {
            val networkTestService = NetworkTestService(this)
            networkTestService.runSmsTest(phoneNumber, message, object : NetworkTestService.SmsDeliveryListener {
                override fun onSmsDelivered(result: SmsTestResult) {
                    sendSmsDataToServer(this@SmsTestForegroundService, result)
                    stopForeground(true)
                    stopSelf()
                }
                override fun onSmsDeliveryFailed(error: String) {
                    Log.e("SmsTestForegroundService", "SMS delivery failed: $error")
                    stopForeground(true)
                    stopSelf()
                }
            })
        }.start()

        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Polaris Automated SMS Test")
            .setContentText("Automated SMS test is running in the background")
            .setSmallIcon(R.drawable.ic_notifications_black_24dp) // use your actual icon resource name
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Automated SMS Test",
                NotificationManager.IMPORTANCE_HIGH // or HIGH if you want heads-up
            )
            serviceChannel.description = "Shows when automated SMS test is running in the background"
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun sendSmsDataToServer(context: Context, result: SmsTestResult) {
        val tokenManager = TokenManager(context)
        val token = tokenManager.getToken()
        if (token == null) {
            Log.e("SmsTestForegroundService", "User not authenticated")
            return
        }
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
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SmsTestForegroundService", "Failed to send SMS data: ${e.localizedMessage}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        Log.i("SmsTestForegroundService", "SMS data uploaded successfully")
                    } else {
                        Log.e("SmsTestForegroundService", "Upload failed: ${response.code}")
                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null
}