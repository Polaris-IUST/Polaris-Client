package com.example.polaris_client.views.ui.tests

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.polaris_client.utils.DatabaseHelper
import com.example.polaris_client.controllers.NetworkTestService
import com.example.polaris_client.R
import com.example.polaris_client.models.PingTestResult
import com.example.polaris_client.utils.TokenManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException


class PingTestFragment : Fragment() {

    private lateinit var hostInput: TextInputEditText
    private lateinit var pingCountInput: TextInputEditText
    private lateinit var startButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var resultText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var networkTestService: NetworkTestService
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_ping_test, container, false)

        hostInput = root.findViewById(R.id.host_input)
        pingCountInput = root.findViewById(R.id.ping_count_input)
        startButton = root.findViewById(R.id.start_test_button)
        progressBar = root.findViewById(R.id.progress_bar)
        resultText = root.findViewById(R.id.result_text)
        recyclerView = root.findViewById(R.id.results_recycler_view)

        recyclerView.layoutManager = LinearLayoutManager(context)

        networkTestService = NetworkTestService(requireContext())
        dbHelper = DatabaseHelper(requireContext())

        startButton.setOnClickListener {
            val host = hostInput.text.toString()
            if (host.isEmpty()) {
                Toast.makeText(context, "Please enter a host", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val countText = pingCountInput.text.toString()
            val count = if (countText.isEmpty()) 5 else countText.toInt()

            runTest(host, count)
        }

        loadPreviousResults()

        return root
    }

    private fun sendPingDataToServer(testResult: PingTestResult) {
        val tokenManager = TokenManager(requireContext())
        val token = tokenManager.getToken()
        if (token.isNullOrEmpty()) {
            Log.e("NetworkTestService", "User not authenticated")
            return
        }

        val client = OkHttpClient()

        val currentTime =
            java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_INSTANT)

        val json = org.json.JSONObject().apply {
            put("longitude", testResult.longitude)
            put("latitude", testResult.latitude)
            put("avgResponseTime", testResult.avgResponseTime)
            put("suceessCountRate", testResult.successRate)
            put("time", currentTime)
        }

        val requestBody =
            json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/pingtest/create/")
            .addHeader("Authorization", "Token $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("NetworkTestService", "Failed to send ping data: ${e.localizedMessage}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (response.isSuccessful) {
                    Log.d("NetworkTestService", "Ping data uploaded successfully")
                } else {
                    Log.e("NetworkTestService", "Upload failed: ${response.code}")
                }
            }
        })
    }

    private fun runTest(host: String, count: Int) {
        progressBar.visibility = View.VISIBLE
        startButton.isEnabled = false
        resultText.text = "Running ping test..."

        lifecycleScope.launch {
            val testResult = networkTestService.runPingTest(host, count)
            progressBar.visibility = View.GONE
            startButton.isEnabled = true

            if (testResult != null) {
                resultText.text = "Ping avg response: ${String.format("%.2f", testResult.avgResponseTime)} ms, Success rate: ${String.format("%.2f", testResult.successRate)}%"
                sendPingDataToServer(testResult) // send result to server
            } else {
                resultText.text = "Error running ping test. Please check the host and try again."
            }

            loadPreviousResults()
        }
    }


    private fun loadPreviousResults() {
        val results = dbHelper.getNetworkTestResults("PING")
        recyclerView.adapter = TestResultAdapter(results)
    }
} 