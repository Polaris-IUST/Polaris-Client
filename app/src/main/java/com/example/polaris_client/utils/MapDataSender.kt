package com.example.polaris_client.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.fragment.app.Fragment
import okhttp3.OkHttpClient
import okhttp3.*
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

object MapDataSender {
    private const val TAG = "MapDataSender"
    private const val ENDPOINT_URL = "https://odysseyanalytics.ir/polaris/api/mapdata/create/"
    private val client = OkHttpClient()
    private const val CACHE_PREFS = "MapDataCachePrefs"
    private const val CACHE_KEY = "cached_mapdata"

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    private fun cacheDatapoint(context: Context, json: JSONObject) {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getStringSet(CACHE_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        cached.add(json.toString())
        prefs.edit().putStringSet(CACHE_KEY, cached).apply()
        Log.d(TAG, "Datapoint cached. Total cached: ${cached.size}")
    }

    fun flushCacheIfNeeded(context: Context) {
        if (!isNetworkAvailable(context)) return
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getStringSet(CACHE_KEY, null)?.toMutableSet() ?: return
        if (cached.isEmpty()) return
        Log.d(TAG, "Flushing ${cached.size} cached datapoints...")
        val iterator = cached.iterator()
        while (iterator.hasNext()) {
            val jsonStr = iterator.next()
            try {
                val json = JSONObject(jsonStr)
                sendDatapointInternal(context, json, removeFromCache = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached datapoint", e)
            }
        }
    }

    fun sendDatapoint(
        context: Context,
        latitude: Double,
        longitude: Double,
        timestamp: String,
        technology: String,
        plmnId: String,
        lac: String,
        rac: String?,
        tac: String,
        cellId: String,
        signalStrength: Int,
        signalQuality: Int,
        distanceWalked: Float,
        nodeId: String,
        band: String,
        arfcan: Int?,
        scanTech: String,
        scanServingSigPow: Int
    ) {
        val json = JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("timestamp", timestamp)
            put("distanceWalked", distanceWalked)
            put("technology", technology)
            put("nodeId", nodeId)
            put("plmnId", plmnId)
            put("lac", lac)
            put("rac", rac)
            put("tac", tac)
            put("cellId", cellId)
            put("band", band)
            put("arfcan", arfcan)
            put("signalStrength", signalStrength)
            put("scanTech", scanTech)
            put("signalQuality", signalQuality)
            put("scanServingSigPow", scanServingSigPow)
        }
        if (isNetworkAvailable(context)) {
            sendDatapointInternal(context, json, removeFromCache = false)
            flushCacheIfNeeded(context)
        } else {
            cacheDatapoint(context, json)
        }
    }

    private fun sendDatapointInternal(context: Context, json: JSONObject, removeFromCache: Boolean) {
        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val tokenManager = TokenManager(context)
        val token = tokenManager.getToken()
        val request = Request.Builder()
            .url(ENDPOINT_URL)
            .addHeader("Authorization", "Token $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send datapoint: ${e.message}", e)
                if (!removeFromCache) {
                    cacheDatapoint(context, json)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Server error: ${response.code} - ${response.body?.string()}")
                        if (!removeFromCache) {
                            cacheDatapoint(context, json)
                        }
                    } else {
                        Log.d(TAG, "Datapoint sent successfully")
                        if (removeFromCache) {
                            // Remove from cache
                            val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
                            val cached = prefs.getStringSet(CACHE_KEY, null)?.toMutableSet() ?: return
                            cached.remove(json.toString())
                            prefs.edit().putStringSet(CACHE_KEY, cached).apply()
                        }
                    }
                }
            }
        })
    }
}