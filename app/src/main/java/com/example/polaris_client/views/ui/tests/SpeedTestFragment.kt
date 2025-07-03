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
import android.app.TimePickerDialog
import android.content.SharedPreferences
import com.google.android.material.slider.Slider
import java.util.Calendar
import java.util.Locale
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.polaris_client.controllers.SpeedTestWorker
import android.widget.Toast
import android.widget.Switch
import android.content.Context

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
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var testsPerDaySlider: Slider
    private lateinit var testsPerDayText: TextView
    private lateinit var scheduleSwitch: Switch
    
    private lateinit var networkSpeedTestService: NetworkSpeedTestService
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var testResultAdapter: TestResultAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private var startTime = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
    }
    private var endTime = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 17)
        set(Calendar.MINUTE, 0)
    }
    private var testsPerDay = 3
    private val MIN_SECONDS_BETWEEN_TESTS = 30

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
        startTimeButton = root.findViewById(R.id.start_time_button)
        endTimeButton = root.findViewById(R.id.end_time_button)
        testsPerDaySlider = root.findViewById(R.id.tests_per_day_slider)
        testsPerDayText = root.findViewById(R.id.tests_per_day_text)
        scheduleSwitch = root.findViewById(R.id.schedule_switch)
        sharedPreferences = requireContext().getSharedPreferences("speed_test_settings", Context.MODE_PRIVATE)
        
        // Initialize services and adapters
        networkSpeedTestService = NetworkSpeedTestService(requireContext())
        dbHelper = DatabaseHelper(requireContext())
        
        setupResultsRecyclerView()
        setupButtonListeners()
        loadSettings()
        updateTimeButtons()
        testsPerDaySlider.value = testsPerDay.toFloat()
        testsPerDayText.text = testsPerDay.toString()
        startTimeButton.setOnClickListener {
            showTimePickerDialog(startTime) { selectedTime ->
                startTime = selectedTime
                updateTimeButtons()
                saveSettings()
                if (scheduleSwitch.isChecked) scheduleTests()
            }
        }
        endTimeButton.setOnClickListener {
            showTimePickerDialog(endTime) { selectedTime ->
                endTime = selectedTime
                updateTimeButtons()
                saveSettings()
                if (scheduleSwitch.isChecked) scheduleTests()
            }
        }
        testsPerDaySlider.addOnChangeListener { _, value, _ ->
            testsPerDay = value.toInt()
            testsPerDayText.text = testsPerDay.toString()
            saveSettings()
            if (scheduleSwitch.isChecked) scheduleTests()
        }
        scheduleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                saveSettings()
                scheduleTests()
                Toast.makeText(context, "Automated speed testing enabled", Toast.LENGTH_SHORT).show()
            } else {
                cancelScheduledTests()
                Toast.makeText(context, "Automated speed testing disabled", Toast.LENGTH_SHORT).show()
            }
        }
        scheduleSwitch.isChecked = sharedPreferences.getBoolean("schedule_enabled", false)
        
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

    private fun loadSettings() {
        startTime.set(Calendar.HOUR_OF_DAY, sharedPreferences.getInt("start_hour", 9))
        startTime.set(Calendar.MINUTE, sharedPreferences.getInt("start_minute", 0))
        endTime.set(Calendar.HOUR_OF_DAY, sharedPreferences.getInt("end_hour", 17))
        endTime.set(Calendar.MINUTE, sharedPreferences.getInt("end_minute", 0))
        testsPerDay = sharedPreferences.getInt("tests_per_day", 3)
    }
    private fun saveSettings() {
        sharedPreferences.edit().apply {
            putInt("start_hour", startTime.get(Calendar.HOUR_OF_DAY))
            putInt("start_minute", startTime.get(Calendar.MINUTE))
            putInt("end_hour", endTime.get(Calendar.HOUR_OF_DAY))
            putInt("end_minute", endTime.get(Calendar.MINUTE))
            putInt("tests_per_day", testsPerDay)
            putBoolean("schedule_enabled", scheduleSwitch.isChecked)
            apply()
        }
    }
    private fun updateTimeButtons() {
        val timeFormat = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
        startTimeButton.text = timeFormat.format(startTime.time)
        endTimeButton.text = timeFormat.format(endTime.time)
    }
    private fun showTimePickerDialog(initialTime: Calendar, onTimeSelected: (Calendar) -> Unit) {
        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val selectedTime = Calendar.getInstance()
                selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedTime.set(Calendar.MINUTE, minute)
                onTimeSelected(selectedTime)
            },
            initialTime.get(Calendar.HOUR_OF_DAY),
            initialTime.get(Calendar.MINUTE),
            false
        )
        timePickerDialog.show()
    }
    private fun scheduleTests() {
        cancelScheduledTests()
        val startTimeSeconds = startTime.get(Calendar.HOUR_OF_DAY) * 3600 + startTime.get(Calendar.MINUTE) * 60
        val endTimeSeconds = endTime.get(Calendar.HOUR_OF_DAY) * 3600 + endTime.get(Calendar.MINUTE) * 60
        var windowDurationSeconds = if (endTimeSeconds > startTimeSeconds) {
            endTimeSeconds - startTimeSeconds
        } else {
            (24 * 3600) - startTimeSeconds + endTimeSeconds
        }
        var secondsBetweenTests = windowDurationSeconds / testsPerDay
        if (secondsBetweenTests < MIN_SECONDS_BETWEEN_TESTS) {
            secondsBetweenTests = MIN_SECONDS_BETWEEN_TESTS
            val adjustedTestsPerDay = windowDurationSeconds / MIN_SECONDS_BETWEEN_TESTS
            if (adjustedTestsPerDay < testsPerDay) {
                Toast.makeText(
                    requireContext(),
                    "Reduced to ${adjustedTestsPerDay.toInt()} tests per day due to minimum interval requirement",
                    Toast.LENGTH_LONG
                ).show()
                testsPerDay = adjustedTestsPerDay.toInt()
                testsPerDaySlider.value = testsPerDay.toFloat()
                testsPerDayText.text = testsPerDay.toString()
            }
        }
        val now = System.currentTimeMillis()
        for (i in 0 until testsPerDay) {
            val testTime = Calendar.getInstance()
            testTime.set(Calendar.HOUR_OF_DAY, startTime.get(Calendar.HOUR_OF_DAY))
            testTime.set(Calendar.MINUTE, startTime.get(Calendar.MINUTE))
            testTime.set(Calendar.SECOND, 0)
            val randomOffset = if (secondsBetweenTests <= 4) 0 else java.util.Random().nextInt(Math.max(1, secondsBetweenTests / 4))
            val offsetSeconds = i * secondsBetweenTests + randomOffset
            testTime.add(Calendar.SECOND, offsetSeconds)
            if (testTime.timeInMillis < now) {
                testTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            val delay = testTime.timeInMillis - now
            val inputData = Data.Builder().build()
            val workRequest = OneTimeWorkRequestBuilder<SpeedTestWorker>()
                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("speed_test_scheduled")
                .build()
            WorkManager.getInstance(requireContext()).enqueue(workRequest)
        }
        sharedPreferences.edit().putBoolean("schedule_enabled", true).apply()
    }
    private fun cancelScheduledTests() {
        WorkManager.getInstance(requireContext()).cancelAllWorkByTag("speed_test_scheduled")
        sharedPreferences.edit().putBoolean("schedule_enabled", false).apply()
    }
} 