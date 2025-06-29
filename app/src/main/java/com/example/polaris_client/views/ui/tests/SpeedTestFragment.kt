package com.example.polaris_client.views.ui.tests

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.polaris_client.utils.DatabaseHelper
import com.example.polaris_client.controllers.NetworkSpeedTestService
import com.example.polaris_client.R
import com.example.polaris_client.views.ui.tests.TestResultAdapter
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpeedTestFragment : Fragment(), NetworkSpeedTestService.SpeedTestListener {

    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var testDurationInput: TextInputEditText
    private lateinit var startDownloadButton: Button
    private lateinit var startUploadButton: Button
    private lateinit var startFullTestButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var testStatusText: TextView
    private lateinit var downloadSpeedText: TextView
    private lateinit var uploadSpeedText: TextView
    private lateinit var latencyText: TextView
    private lateinit var jitterText: TextView
    private lateinit var resultsRecyclerView: RecyclerView
    
    private lateinit var networkSpeedTestService: NetworkSpeedTestService
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var testResultAdapter: TestResultAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_speed_test, container, false)
        
        // Initialize views
        serverUrlInput = root.findViewById(R.id.server_url_input)
        testDurationInput = root.findViewById(R.id.test_duration_input)
        startDownloadButton = root.findViewById(R.id.start_download_test_button)
        startUploadButton = root.findViewById(R.id.start_upload_test_button)
        startFullTestButton = root.findViewById(R.id.start_full_test_button)
        progressBar = root.findViewById(R.id.progress_bar)
        testStatusText = root.findViewById(R.id.test_status_text)
        downloadSpeedText = root.findViewById(R.id.download_speed_text)
        uploadSpeedText = root.findViewById(R.id.upload_speed_text)
        latencyText = root.findViewById(R.id.latency_text)
        jitterText = root.findViewById(R.id.jitter_text)
        resultsRecyclerView = root.findViewById(R.id.results_recycler_view)
        
        // Initialize services and adapters
        networkSpeedTestService = NetworkSpeedTestService(requireContext())
        dbHelper = DatabaseHelper(requireContext())
        
        setupResultsRecyclerView()
        setupButtonListeners()
        
        return root
    }
    
    private fun setupResultsRecyclerView() {
        resultsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        testResultAdapter = TestResultAdapter(emptyList())
        resultsRecyclerView.adapter = testResultAdapter
        
        // Load previous test results
        lifecycleScope.launch {
            val previousResults = withContext(Dispatchers.IO) {
                // Get all SPEED_ prefixed test results
                dbHelper.getNetworkTestResults()
                    .filter { it.testType.startsWith("SPEED_") }
            }
            testResultAdapter.updateData(previousResults)
        }
    }
    
    private fun setupButtonListeners() {
        startDownloadButton.setOnClickListener {
            runSpeedTest("download")
        }
        
        startUploadButton.setOnClickListener {
            runSpeedTest("upload")
        }
        
        startFullTestButton.setOnClickListener {
            runSpeedTest("full")
        }
    }
    
    private fun runSpeedTest(testType: String) {
        // Get test configuration - if empty, pass null to use default URL
        val serverUrl = serverUrlInput.text.toString().takeIf { it.isNotEmpty() }
        val testDuration = testDurationInput.text.toString().toIntOrNull() ?: 10
        
        // Reset UI
        progressBar.progress = 0
        progressBar.visibility = View.VISIBLE
        testStatusText.text = "Preparing test..."
        disableButtons()
        
        lifecycleScope.launch {
            when (testType) {
                "download" -> {
                    downloadSpeedText.text = "Testing..."
                    networkSpeedTestService.runDownloadTest(
                        serverUrl = serverUrl,
                        durationSec = testDuration,
                        listener = this@SpeedTestFragment
                    )
                }
                "upload" -> {
                    uploadSpeedText.text = "Testing..."
                    networkSpeedTestService.runUploadTest(
                        serverUrl = serverUrl,
                        durationSec = testDuration,
                        listener = this@SpeedTestFragment
                    )
                }
                "full" -> {
                    downloadSpeedText.text = "Waiting..."
                    uploadSpeedText.text = "Waiting..."
                    latencyText.text = "Testing..."
                    jitterText.text = "Testing..."
                    networkSpeedTestService.runFullSpeedTest(
                        serverUrl = serverUrl,
                        durationSec = testDuration,
                        listener = this@SpeedTestFragment
                    )
                }
            }
            
            // Refresh test results after test completes
            val updatedResults = withContext(Dispatchers.IO) {
                dbHelper.getNetworkTestResults()
                    .filter { it.testType.startsWith("SPEED_") }
            }
            testResultAdapter.updateData(updatedResults)
        }
    }
    
    private fun disableButtons() {
        startDownloadButton.isEnabled = false
        startUploadButton.isEnabled = false
        startFullTestButton.isEnabled = false
    }
    
    private fun enableButtons() {
        startDownloadButton.isEnabled = true
        startUploadButton.isEnabled = true
        startFullTestButton.isEnabled = true
    }
    
    // NetworkSpeedTestListener interface implementations
    override fun onTestUpdate(status: String, progressPercent: Int) {
        testStatusText.text = status
        progressBar.progress = progressPercent
    }
    
    override fun onLatencyMeasured(latencyMs: Long) {
        latencyText.text = "$latencyMs ms"
    }
    
    override fun onJitterMeasured(jitterMs: Float) {
        jitterText.text = "${String.format("%.1f", jitterMs)} ms"
    }
    
    override fun onDownloadComplete(speedMbps: Float) {
        downloadSpeedText.text = "${String.format("%.2f", speedMbps)} Mbps"
        enableButtons()
    }
    
    override fun onUploadComplete(speedMbps: Float) {
        uploadSpeedText.text = "${String.format("%.2f", speedMbps)} Mbps"
        enableButtons()
    }
    
    override fun onTestComplete() {
        progressBar.visibility = View.INVISIBLE
        testStatusText.text = "Test completed"
        enableButtons()
    }
    
    override fun onTestFailed(error: String) {
        progressBar.visibility = View.INVISIBLE
        testStatusText.text = error
        enableButtons()
    }
} 