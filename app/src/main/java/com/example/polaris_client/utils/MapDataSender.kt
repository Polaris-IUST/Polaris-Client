package com.example.polaris_client.utils

import android.content.Context
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
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Server error: ${response.code} - ${response.body?.string()}")
                    } else {
                        Log.d(TAG, "Datapoint sent successfully")
                    }
                }
            }
        })
    }
}