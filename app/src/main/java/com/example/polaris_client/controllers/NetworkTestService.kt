package com.example.polaris_client.controllers

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import android.content.Context.RECEIVER_NOT_EXPORTED
import com.example.polaris_client.models.DnsTestResult
import com.example.polaris_client.models.HttpTestResult
import com.example.polaris_client.models.PingTestResult
import com.example.polaris_client.utils.DatabaseHelper
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import com.example.polaris_client.models.SmsTestResult
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

class NetworkTestService(private val context: Context) {
    private val dbHelper = DatabaseHelper(context)
    
    // HTTP Throughput Test
    suspend fun runHttpTest(url: String): HttpTestResult? {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                val inputStream = connection.inputStream
                val buffer = ByteArray(8192)
                while (inputStream.read(buffer) != -1) { /* consume */ }

                val endTime = System.currentTimeMillis()
                val durationMs = (endTime - startTime).toFloat()

                inputStream.close()
                connection.disconnect()

                val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0

                dbHelper.insertNetworkTestData(
                    "HTTP",
                    durationMs.toString(),
                    durationMs.toString(),
                    latitude,
                    longitude
                )

                HttpTestResult(
                    responseTime = durationMs,
                    latitude = latitude,
                    longitude = longitude,
                    hostname = url
                )
            } catch (e: Exception) {
                Log.e("NetworkTestService", "HTTP test error: ${e.message}")
                null
            }
        }
    }


    // Ping Test
    suspend fun runPingTest(host: String, count: Int = 5): PingTestResult? {
        return withContext(Dispatchers.IO) {
            try {
                var totalTime = 0f
                var successCount = 0

                repeat(count) {
                    val startTime = System.currentTimeMillis()
                    val isReachable = InetAddress.getByName(host).isReachable(5000)
                    val endTime = System.currentTimeMillis()

                    if (isReachable) {
                        totalTime += (endTime - startTime)
                        successCount++
                    }
                }

                if (successCount == 0) return@withContext null

                val avgResponseTime = totalTime / successCount
                val successRate = (successCount / count.toFloat()) * 100f

                val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0

                // Save to local DB
                dbHelper.insertNetworkTestData("PING", avgResponseTime.toString(), successRate.toString(), latitude, longitude)

                // Return as PingTestResult
                PingTestResult(avgResponseTime, successRate, latitude, longitude, host)

            } catch (e: Exception) {
                Log.e("NetworkTestService", "Ping test error: ${e.message}")
                null
            }
        }
    }


    // DNS Test
    suspend fun runDnsTest(hostname: String): DnsTestResult? {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                InetAddress.getByName(hostname)
                val endTime = System.currentTimeMillis()
                val responseTime = (endTime - startTime).toFloat()

                // Save to DB as before
                val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0
                dbHelper.insertNetworkTestData("DNS", responseTime.toString(), hostname, latitude, longitude)

                DnsTestResult(responseTime, latitude, longitude, hostname)

            } catch (e: Exception) {
                Log.e("NetworkTestService", "DNS test error: ${e.message}")
                null
            }
        }
    }

    
    // Web Test
    suspend fun runWebTest(url: String): Float {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.connect()
                connection.inputStream.close()
                val endTime = System.currentTimeMillis()
                val responseTime = (endTime - startTime).toFloat()
                
                // Save the result to database
                val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0
                dbHelper.insertNetworkTestData("WEB", responseTime.toString(), url, latitude, longitude)
                
                connection.disconnect()
                responseTime
            } catch (e: Exception) {
                Log.e("NetworkTestService", "Web test error: ${e.message}")
                -1f
            }
        }
    }
    
    // SMS Test (Note: Requires SEND_SMS permission)
    fun runSmsTest(phoneNumber: String, message: String, listener: SmsDeliveryListener) {
        try {
            val sentTime = Date().time
            val smsManager = SmsManager.getDefault()

            // Create explicit intents by setting the package
            val sentIntent = Intent("SMS_SENT").apply {
                setPackage(context.packageName)
            }

            val deliveredIntent = Intent("SMS_DELIVERED").apply {
                setPackage(context.packageName)
            }

            val sentPI = PendingIntent.getBroadcast(
                context,
                0,
                sentIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val deliveredPI = PendingIntent.getBroadcast(
                context,
                0,
                deliveredIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            // SENT receiver
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            Log.d("SMS", "SMS sent successfully")
                        }
                        else -> {
                            listener.onSmsDeliveryFailed("Failed to send SMS")
                        }
                    }
                    context.unregisterReceiver(this)
                }
            }, IntentFilter("SMS_SENT"), Context.RECEIVER_NOT_EXPORTED)

            // DELIVERED receiver
            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val deliveredTime = Date().time
                    val deliveryTimeMs = deliveredTime - sentTime
                    val deliveryTimeSeconds = deliveryTimeMs / 1000f

                    val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                    val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0
                    val currentTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)

                    val smsTestResult = SmsTestResult(
                        longitude = longitude,
                        latitude = latitude,
                        deliveryTime = deliveryTimeSeconds,
                        phoneNumber = phoneNumber,
                        time = currentTime
                    )

                    // Save to database if needed
                    dbHelper.insertNetworkTestData(
                        "SMS",
                        deliveryTimeSeconds.toString(),
                        phoneNumber,
                        latitude,
                        longitude
                    )

                    listener.onSmsDelivered(smsTestResult)
                    context.unregisterReceiver(this)
                }
            }, IntentFilter("SMS_DELIVERED"), Context.RECEIVER_NOT_EXPORTED)

            smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)

        } catch (e: Exception) {
            Log.e("NetworkTestService", "SMS test error: ${e.message}")
            listener.onSmsDeliveryFailed(e.message ?: "Unknown error")
        }
    }



    interface SmsDeliveryListener {
        fun onSmsDelivered(result: SmsTestResult)
        fun onSmsDeliveryFailed(error: String)
    }
} 