package com.example.polaris_client.controllers

import android.content.Context
import android.util.Log
import com.example.polaris_client.utils.DatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random

class NetworkSpeedTestService(private val context: Context) {
    private val dbHelper = DatabaseHelper(context)
    
    // Default settings for speed test
    private val DEFAULT_SPEED_TEST_URL = "https://speed.cloudflare.com/__down?bytes=100000000" // 100MB download test
    private val DEFAULT_UPLOAD_TEST_URL = "https://speed.cloudflare.com/__up"
    private val ALTERNATIVE_UPLOAD_URLS = listOf(
        "https://httpbin.org/post",
        "https://postman-echo.com/post",
        "https://httpbin.org/delay/1"
    )
    private val DEFAULT_TEST_DURATION_SEC = 10
    
    // Download Speed Test
    suspend fun runDownloadTest(serverUrl: String? = null, 
                               durationSec: Int = DEFAULT_TEST_DURATION_SEC, 
                               listener: SpeedTestListener): Float {
        // Use default URL if serverUrl is null or empty
        val actualUrl = if (serverUrl.isNullOrEmpty()) DEFAULT_SPEED_TEST_URL else serverUrl
        
        return withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    listener.onTestUpdate("Starting download test...", 0)
                }
                
                var totalBytesRead = 0L
                val startTime = System.currentTimeMillis()
                val endTime = startTime + (durationSec * 1000)
                val buffer = ByteArray(8192)
                val url = URL(actualUrl)
                
                // Run download test until duration is reached
                var progress = 0
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                val inputStream = connection.inputStream
                
                while (System.currentTimeMillis() < endTime) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    
                    totalBytesRead += bytesRead
                    
                    // Update progress every ~10% of test duration
                    val currentProgress = ((System.currentTimeMillis() - startTime).toFloat() / 
                                         (durationSec * 1000) * 100).toInt()
                    if (currentProgress > progress + 9) {
                        progress = currentProgress
                        val currentSpeed = calculateSpeed(totalBytesRead, System.currentTimeMillis() - startTime)
                        withContext(Dispatchers.Main) {
                            listener.onTestUpdate("Download: ${String.format("%.2f", currentSpeed)} Mbps", 
                                                progress)
                        }
                    }
                }
                
                inputStream.close()
                connection.disconnect()
                
                val testDuration = System.currentTimeMillis() - startTime
                val downloadSpeed = calculateSpeed(totalBytesRead, testDuration)
                
                // Save result to database
                val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0
                dbHelper.insertNetworkTestData("SPEED_DOWN", downloadSpeed.toString(), 
                                             "Duration: ${testDuration/1000}s", latitude, longitude)
                
                withContext(Dispatchers.Main) {
                    listener.onDownloadComplete(downloadSpeed)
                }
                downloadSpeed
            } catch (e: Exception) {
                Log.e("NetworkSpeedTestService", "Download test error: ${e.message}")
                withContext(Dispatchers.Main) {
                    listener.onTestFailed("Download test failed: ${e.message}")
                }
                -1f
            }
        }
    }
    
    // Upload Speed Test
    suspend fun runUploadTest(serverUrl: String? = null, 
                             durationSec: Int = DEFAULT_TEST_DURATION_SEC, 
                             listener: SpeedTestListener): Float {
        // Find a working upload URL
        val actualUrl = if (!serverUrl.isNullOrEmpty()) {
            serverUrl
        } else {
            findWorkingUploadUrl() ?: DEFAULT_UPLOAD_TEST_URL
        }
        
        return withContext(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    listener.onTestUpdate("Starting upload test to $actualUrl...", 0)
                }
                
                // Try the main upload method first
                val result = tryUploadTest(actualUrl, durationSec, listener)
                if (result > 0) {
                    return@withContext result
                }
                
                // If main method fails, try fallback method
                withContext(Dispatchers.Main) {
                    listener.onTestUpdate("Main upload failed, trying fallback method...", 0)
                }
                
                tryFallbackUploadTest(actualUrl, durationSec, listener)
            } catch (e: Exception) {
                Log.e("NetworkSpeedTestService", "Upload test error: ${e.message}")
                withContext(Dispatchers.Main) {
                    listener.onTestFailed("Upload test failed: ${e.message}")
                }
                -1f
            }
        }
    }
    
    // Main upload test method
    private suspend fun tryUploadTest(actualUrl: String, durationSec: Int, listener: SpeedTestListener): Float {
        return withContext(Dispatchers.IO) {
            try {
                var totalBytesUploaded = 0L
                val startTime = System.currentTimeMillis()
                val endTime = startTime + (durationSec * 1000)
                
                // Generate random data for upload (1MB)
                val testData = ByteArray(1024 * 1024)
                Random().nextBytes(testData)
                
                // Create a single connection for the entire test
                val connection = URL(actualUrl).openConnection() as HttpURLConnection
                connection.doOutput = true
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.setRequestProperty("Content-Length", testData.size.toString())
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                connection.setChunkedStreamingMode(8192) // Use chunked transfer encoding
                
                val outputStream = connection.outputStream
                var progress = 0
                
                // Upload data continuously until time runs out
                while (System.currentTimeMillis() < endTime) {
                    try {
                        outputStream.write(testData)
                        outputStream.flush()
                        totalBytesUploaded += testData.size
                        
                        // Update progress every ~10% of test duration
                        val currentProgress = ((System.currentTimeMillis() - startTime).toFloat() / 
                                             (durationSec * 1000) * 100).toInt()
                        if (currentProgress > progress + 9) {
                            progress = currentProgress
                            val currentSpeed = calculateSpeed(totalBytesUploaded, System.currentTimeMillis() - startTime)
                            withContext(Dispatchers.Main) {
                                listener.onTestUpdate("Upload: ${String.format("%.2f", currentSpeed)} Mbps", 
                                                    progress)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("NetworkSpeedTestService", "Upload chunk failed: ${e.message}")
                        // Continue with the test even if one chunk fails
                        break
                    }
                }
                
                // Close the connection
                try {
                    outputStream.close()
                    val responseCode = connection.responseCode
                    Log.d("NetworkSpeedTestService", "Upload test completed with response code: $responseCode")
                } catch (e: Exception) {
                    Log.w("NetworkSpeedTestService", "Error closing upload connection: ${e.message}")
                } finally {
                    connection.disconnect()
                }
                
                val testDuration = System.currentTimeMillis() - startTime
                val uploadSpeed = calculateSpeed(totalBytesUploaded, testDuration)
                
                // Save result to database
                val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0
                dbHelper.insertNetworkTestData("SPEED_UP", uploadSpeed.toString(), 
                                             "Duration: ${testDuration/1000}s", latitude, longitude)
                
                withContext(Dispatchers.Main) {
                    listener.onUploadComplete(uploadSpeed)
                }
                uploadSpeed
            } catch (e: Exception) {
                Log.e("NetworkSpeedTestService", "Main upload test failed: ${e.message}")
                -1f
            }
        }
    }
    
    // Fallback upload test method (simpler approach)
    private suspend fun tryFallbackUploadTest(actualUrl: String, durationSec: Int, listener: SpeedTestListener): Float {
        return withContext(Dispatchers.IO) {
            try {
                var totalBytesUploaded = 0L
                val startTime = System.currentTimeMillis()
                val endTime = startTime + (durationSec * 1000)
                
                // Use smaller chunks for fallback (100KB)
                val testData = ByteArray(100 * 1024)
                Random().nextBytes(testData)
                
                var progress = 0
                
                // Upload data in smaller chunks with new connections
                while (System.currentTimeMillis() < endTime) {
                    try {
                        val connection = URL(actualUrl).openConnection() as HttpURLConnection
                        connection.doOutput = true
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Content-Type", "application/octet-stream")
                        connection.connectTimeout = 5000
                        connection.readTimeout = 10000
                        
                        val outputStream = connection.outputStream
                        outputStream.write(testData)
                        outputStream.flush()
                        outputStream.close()
                        
                        // Check response code
                        val responseCode = connection.responseCode
                        connection.disconnect()
                        
                        if (responseCode in 200..299) {
                            totalBytesUploaded += testData.size
                        }
                        
                        // Update progress every ~10% of test duration
                        val currentProgress = ((System.currentTimeMillis() - startTime).toFloat() / 
                                             (durationSec * 1000) * 100).toInt()
                        if (currentProgress > progress + 9) {
                            progress = currentProgress
                            val currentSpeed = calculateSpeed(totalBytesUploaded, System.currentTimeMillis() - startTime)
                            withContext(Dispatchers.Main) {
                                listener.onTestUpdate("Upload (fallback): ${String.format("%.2f", currentSpeed)} Mbps", 
                                                    progress)
                            }
                        }
                        
                        // Small delay between requests
                        delay(100)
                    } catch (e: Exception) {
                        Log.w("NetworkSpeedTestService", "Fallback upload chunk failed: ${e.message}")
                        delay(500) // Longer delay on failure
                    }
                }
                
                val testDuration = System.currentTimeMillis() - startTime
                val uploadSpeed = calculateSpeed(totalBytesUploaded, testDuration)
                
                // Save result to database
                val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0
                dbHelper.insertNetworkTestData("SPEED_UP_FALLBACK", uploadSpeed.toString(), 
                                             "Duration: ${testDuration/1000}s", latitude, longitude)
                
                withContext(Dispatchers.Main) {
                    listener.onUploadComplete(uploadSpeed)
                }
                uploadSpeed
            } catch (e: Exception) {
                Log.e("NetworkSpeedTestService", "Fallback upload test failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    listener.onTestFailed("Upload test failed: ${e.message}")
                }
                -1f
            }
        }
    }
    
    // Helper method to find a working upload URL
    private suspend fun findWorkingUploadUrl(): String? {
        val allUrls = listOf(DEFAULT_UPLOAD_TEST_URL) + ALTERNATIVE_UPLOAD_URLS
        
        for (url in allUrls) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                
                val responseCode = connection.responseCode
                connection.disconnect()
                
                // Accept 2xx and 4xx responses (4xx means server is reachable but doesn't accept HEAD)
                if (responseCode in 200..499) {
                    Log.d("NetworkSpeedTestService", "Found working upload URL: $url")
                    return url
                }
            } catch (e: Exception) {
                Log.d("NetworkSpeedTestService", "URL $url not working: ${e.message}")
                continue
            }
        }
        
        Log.w("NetworkSpeedTestService", "No working upload URL found, using default")
        return null
    }
    
    // Measure network latency
    suspend fun measureLatency(host: String): Pair<Long, Float> {
        return withContext(Dispatchers.IO) {
            try {
                val latencyValues = mutableListOf<Long>()
                
                // Take 5 measurements
                repeat(5) {
                    val startTime = System.currentTimeMillis()
                    val connection = URL("https://$host").openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.connect()
                    connection.disconnect()
                    val latency = System.currentTimeMillis() - startTime
                    latencyValues.add(latency)
                    
                    // Small delay between measurements
                    delay(200)
                }
                
                val avgLatency = latencyValues.average().toLong()
                val jitter = calculateJitter(latencyValues)
                
                // Save result to database
                val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0
                dbHelper.insertNetworkTestData("LATENCY", avgLatency.toString(), 
                                             "Jitter: ${jitter}ms", latitude, longitude)
                
                Pair(avgLatency, jitter)
            } catch (e: Exception) {
                Log.e("NetworkSpeedTestService", "Latency measurement error: ${e.message}")
                Pair(-1L, -1f)
            }
        }
    }
    
    // Run full speed test
    suspend fun runFullSpeedTest(serverUrl: String? = null, durationSec: Int = DEFAULT_TEST_DURATION_SEC, listener: SpeedTestListener) {
        // Use default URL if serverUrl is null or empty
        val actualUrl = if (serverUrl.isNullOrEmpty()) DEFAULT_SPEED_TEST_URL else serverUrl
        
        withContext(Dispatchers.IO) {
            try {
                // Extract host from URL
                val host = try {
                    URL(actualUrl).host
                } catch (e: Exception) {
                    "speed.cloudflare.com"
                }
                
                // Step 1: Measure latency
                withContext(Dispatchers.Main) {
                    listener.onTestUpdate("Measuring latency...", 0)
                }
                val (latency, jitter) = measureLatency(host)
                
                if (latency > 0) {
                    withContext(Dispatchers.Main) {
                        listener.onLatencyMeasured(latency)
                        listener.onJitterMeasured(jitter)
                    }
                }
                
                // Step 2: Download test
                val downloadSpeed = runDownloadTest(actualUrl, durationSec, listener)
                
                // Short break between tests
                delay(1000)
                
                // Step 3: Upload test
                val uploadUrl = if (actualUrl == DEFAULT_SPEED_TEST_URL) DEFAULT_UPLOAD_TEST_URL else actualUrl
                val uploadSpeed = runUploadTest(uploadUrl, durationSec, listener)
                
                // Save full test result
                val testSummary = "Down: ${String.format("%.2f", downloadSpeed)} Mbps, " +
                                 "Up: ${String.format("%.2f", uploadSpeed)} Mbps, " +
                                 "Latency: ${latency}ms, Jitter: ${jitter}ms"
                                 
                val latitude = LocationService.lastKnownLocation?.latitude ?: 0.0
                val longitude = LocationService.lastKnownLocation?.longitude ?: 0.0
                dbHelper.insertNetworkTestData("SPEED_FULL", testSummary, actualUrl, latitude, longitude)
                
                withContext(Dispatchers.Main) {
                    listener.onTestComplete()
                }
            } catch (e: Exception) {
                Log.e("NetworkSpeedTestService", "Full speed test error: ${e.message}")
                withContext(Dispatchers.Main) {
                    listener.onTestFailed("Test failed: ${e.message}")
                }
            }
        }
    }
    
    // Helper methods for speed tests
    private fun calculateSpeed(bytes: Long, durationMs: Long): Float {
        if (durationMs <= 0) return 0f
        // Convert bytes to bits and duration to seconds
        return (bytes * 8) / (durationMs / 1000f) / 1_000_000f // Result in Mbps
    }
    
    private fun calculateJitter(latencyValues: List<Long>): Float {
        if (latencyValues.size <= 1) return 0f
        
        var jitterSum = 0f
        for (i in 0 until latencyValues.size - 1) {
            jitterSum += Math.abs(latencyValues[i] - latencyValues[i + 1])
        }
        
        return jitterSum / (latencyValues.size - 1)
    }
    
    interface SpeedTestListener {
        fun onTestUpdate(status: String, progressPercent: Int)
        fun onLatencyMeasured(latencyMs: Long)
        fun onJitterMeasured(jitterMs: Float)
        fun onDownloadComplete(speedMbps: Float)
        fun onUploadComplete(speedMbps: Float)
        fun onTestComplete()
        fun onTestFailed(error: String)
    }
} 