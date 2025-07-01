package com.example.polaris_client.controllers

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Date
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.app.PendingIntent
import android.content.Intent
import com.example.polaris_client.models.DnsTestResult
import com.example.polaris_client.utils.DatabaseHelper

class NetworkTestService(private val context: Context) {
    private val dbHelper = DatabaseHelper(context)
    
    // HTTP Throughput Test
    suspend fun runHttpTest(url: String): Float {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                
                val contentLength = connection.contentLength
                val inputStream = connection.inputStream
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytesRead += bytesRead
                }
                
                val endTime = System.currentTimeMillis()
                val duration = (endTime - startTime) / 1000f // in seconds
                val throughputKbps = (totalBytesRead * 8 / 1000f) / duration // in kbps
                
                inputStream.close()
                connection.disconnect()
                
                // Save the result to database
                val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0
                dbHelper.insertNetworkTestData("HTTP", throughputKbps.toString(), duration.toString(), latitude, longitude)
                
                throughputKbps
            } catch (e: Exception) {
                Log.e("NetworkTestService", "HTTP test error: ${e.message}")
                -1f
            }
        }
    }
    
    // Ping Test
    suspend fun runPingTest(host: String, count: Int = 5): Float {
        return withContext(Dispatchers.IO) {
            try {
                var totalTime = 0f
                var successCount = 0
                
                repeat(count) {
                    val startTime = System.currentTimeMillis()
                    val isReachable = InetAddress.getByName(host).isReachable(5000) // 5 second timeout
                    val endTime = System.currentTimeMillis()
                    
                    if (isReachable) {
                        totalTime += (endTime - startTime)
                        successCount++
                    }
                }
                
                val avgResponseTime = if (successCount > 0) totalTime / successCount else -1f
                
                // Save the result to database
                val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0
                dbHelper.insertNetworkTestData("PING", avgResponseTime.toString(), successCount.toString(), latitude, longitude)
                
                avgResponseTime
            } catch (e: Exception) {
                Log.e("NetworkTestService", "Ping test error: ${e.message}")
                -1f
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
            
            val sentPI = PendingIntent.getBroadcast(context, 0, 
                sentIntent, PendingIntent.FLAG_IMMUTABLE)
                
            val deliveredPI = PendingIntent.getBroadcast(context, 0,
                deliveredIntent, PendingIntent.FLAG_IMMUTABLE)
                
            // Set up broadcast receivers
            context.registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context, intent: android.content.Intent) {
                    when (resultCode) {
                        android.app.Activity.RESULT_OK -> {
                            Log.d("SMS", "SMS sent successfully")
                        }
                        else -> {
                            listener.onSmsDeliveryFailed("Failed to send SMS")
                        }
                    }
                    context.unregisterReceiver(this)
                }
            }, android.content.IntentFilter("SMS_SENT"), RECEIVER_NOT_EXPORTED)
            
            context.registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context, intent: android.content.Intent) {
                    val deliveredTime = Date().time
                    val deliveryTime = deliveredTime - sentTime
                    
                    // Save the result to database
                    val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                    val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0
                    dbHelper.insertNetworkTestData("SMS", deliveryTime.toString(), phoneNumber, latitude, longitude)
                    
                    listener.onSmsDelivered(deliveryTime)
                    context.unregisterReceiver(this)
                }
            }, android.content.IntentFilter("SMS_DELIVERED"), RECEIVER_NOT_EXPORTED)
            
            smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)
        } catch (e: Exception) {
            Log.e("NetworkTestService", "SMS test error: ${e.message}")
            listener.onSmsDeliveryFailed(e.message ?: "Unknown error")
        }
    }
    
    interface SmsDeliveryListener {
        fun onSmsDelivered(deliveryTimeMs: Long)
        fun onSmsDeliveryFailed(error: String)
    }
} 