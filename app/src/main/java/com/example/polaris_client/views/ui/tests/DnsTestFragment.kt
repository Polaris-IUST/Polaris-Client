package com.example.polaris_client.views.ui.tests

import android.os.Bundle
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
import com.example.polaris_client.models.DnsTestResult
import com.example.polaris_client.utils.TokenManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType


class DnsTestFragment : Fragment() {

    private lateinit var hostnameInput: TextInputEditText
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
        val root = inflater.inflate(R.layout.fragment_dns_test, container, false)

        hostnameInput = root.findViewById(R.id.hostname_input)
        startButton = root.findViewById(R.id.start_test_button)
        progressBar = root.findViewById(R.id.progress_bar)
        resultText = root.findViewById(R.id.result_text)
        recyclerView = root.findViewById(R.id.results_recycler_view)

        recyclerView.layoutManager = LinearLayoutManager(context)
        
        networkTestService = NetworkTestService(requireContext())
        dbHelper = DatabaseHelper(requireContext())

        startButton.setOnClickListener {
            val hostname = hostnameInput.text.toString()
            if (hostname.isEmpty()) {
                Toast.makeText(context, "Please enter a hostname", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            runTest(hostname)
        }
        
        loadPreviousResults()

        return root
    }


    private fun sendDnsDataToServer(testResult: DnsTestResult) {
        val tokenManager = TokenManager(requireContext())
        val token = tokenManager.getToken()
        if (token == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val client = OkHttpClient()

        val currentTime = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_INSTANT)

        val json = org.json.JSONObject().apply {
            put("longitude", testResult.longitude)
            put("latitude", testResult.latitude)
            put("responsetime", testResult.responseTime)
            put("hostname", testResult.hostname)
            put("time", currentTime)
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/dnsdata/create/")
            .addHeader("Authorization", "Token $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "Failed to send data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                requireActivity().runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "DNS data uploaded successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }


    private fun runTest(hostname: String) {
        progressBar.visibility = View.VISIBLE
        startButton.isEnabled = false
        resultText.text = "Running DNS test..."

        lifecycleScope.launch {
            val testResult = networkTestService.runDnsTest(hostname)
            progressBar.visibility = View.GONE
            startButton.isEnabled = true

            if (testResult != null && testResult.responseTime >= 0) {
                resultText.text = "DNS lookup time: ${String.format("%.2f", testResult.responseTime)} ms"
                sendDnsDataToServer(testResult)
            } else {
                resultText.text = "Error running DNS test. Please check the hostname and try again."
            }

            loadPreviousResults()
        }
    }

    
    private fun loadPreviousResults() {
        val results = dbHelper.getNetworkTestResults("DNS")
        recyclerView.adapter = TestResultAdapter(results)
    }
} 