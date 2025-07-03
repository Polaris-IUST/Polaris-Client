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
import com.example.polaris_client.models.HttpTestResult
import com.example.polaris_client.utils.TokenManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import android.app.TimePickerDialog
import android.content.SharedPreferences
import com.google.android.material.slider.Slider
import java.util.Calendar
import java.util.Locale
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.polaris_client.controllers.HttpTestWorker
import android.widget.Switch
import android.content.Context

class HttpTestFragment : Fragment() {

    private lateinit var urlInput: TextInputEditText
    private lateinit var startButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var resultText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var networkTestService: NetworkTestService
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var testsPerDaySlider: Slider
    private lateinit var testsPerDayText: TextView
    private lateinit var scheduleSwitch: Switch
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
        val root = inflater.inflate(R.layout.fragment_http_test, container, false)

        urlInput = root.findViewById(R.id.url_input)
        startButton = root.findViewById(R.id.start_test_button)
        progressBar = root.findViewById(R.id.progress_bar)
        resultText = root.findViewById(R.id.result_text)
        recyclerView = root.findViewById(R.id.results_recycler_view)
        startTimeButton = root.findViewById(R.id.start_time_button)
        endTimeButton = root.findViewById(R.id.end_time_button)
        testsPerDaySlider = root.findViewById(R.id.tests_per_day_slider)
        testsPerDayText = root.findViewById(R.id.tests_per_day_text)
        scheduleSwitch = root.findViewById(R.id.schedule_switch)
        sharedPreferences = requireContext().getSharedPreferences("http_test_settings", Context.MODE_PRIVATE)

        recyclerView.layoutManager = LinearLayoutManager(context)
        
        networkTestService = NetworkTestService(requireContext())
        dbHelper = DatabaseHelper(requireContext())

        loadSettings()
        updateTimeButtons()
        testsPerDaySlider.value = testsPerDay.toFloat()
        testsPerDayText.text = testsPerDay.toString()

        startButton.setOnClickListener {
            val url = urlInput.text.toString()
            if (url.isEmpty()) {
                Toast.makeText(context, "Please enter a URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            runTest(url)
        }
        
        loadPreviousResults()

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
                if (urlInput.text.toString().isEmpty()) {
                    Toast.makeText(context, "Please enter a URL first", Toast.LENGTH_SHORT).show()
                    scheduleSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
                saveSettings()
                scheduleTests()
                Toast.makeText(context, "Automated HTTP testing enabled", Toast.LENGTH_SHORT).show()
            } else {
                cancelScheduledTests()
                Toast.makeText(context, "Automated HTTP testing disabled", Toast.LENGTH_SHORT).show()
            }
        }
        scheduleSwitch.isChecked = sharedPreferences.getBoolean("schedule_enabled", false)

        return root
    }

    private fun sendHttpDataToServer(testResult: HttpTestResult) {
        val tokenManager = TokenManager(requireContext())
        val token = tokenManager.getToken()
        if (token == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val client = OkHttpClient()

        val currentTime = java.time.ZonedDateTime.now()
            .format(java.time.format.DateTimeFormatter.ISO_INSTANT)

        val json = org.json.JSONObject().apply {
            put("longitude", testResult.longitude)
            put("latitude", testResult.latitude)
            put("responsetime", testResult.responseTime)
            put("hostname", testResult.hostname)
            put("time", currentTime)
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/httpresponse/create/")
            .addHeader("Authorization", "Token $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "Failed to upload HTTP data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                requireActivity().runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "HTTP data uploaded successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }


    private fun runTest(url: String) {
        progressBar.visibility = View.VISIBLE
        startButton.isEnabled = false
        resultText.text = "Running HTTP test..."

        lifecycleScope.launch {
            val testResult = networkTestService.runHttpTest(url)
            progressBar.visibility = View.GONE
            startButton.isEnabled = true

            if (testResult != null && testResult.responseTime >= 0) {
                resultText.text = "HTTP response time: ${String.format("%.2f", testResult.responseTime)} ms"
                sendHttpDataToServer(testResult)
            } else {
                resultText.text = "Error running HTTP test. Please check the URL and try again."
            }

            loadPreviousResults()
        }
    }

    
    private fun loadPreviousResults() {
        val results = dbHelper.getNetworkTestResults("HTTP")
        recyclerView.adapter = TestResultAdapter(results)
    }

    private fun loadSettings() {
        startTime.set(Calendar.HOUR_OF_DAY, sharedPreferences.getInt("start_hour", 9))
        startTime.set(Calendar.MINUTE, sharedPreferences.getInt("start_minute", 0))
        endTime.set(Calendar.HOUR_OF_DAY, sharedPreferences.getInt("end_hour", 17))
        endTime.set(Calendar.MINUTE, sharedPreferences.getInt("end_minute", 0))
        testsPerDay = sharedPreferences.getInt("tests_per_day", 3)
        urlInput.setText(sharedPreferences.getString("url", ""))
    }
    private fun saveSettings() {
        sharedPreferences.edit().apply {
            putInt("start_hour", startTime.get(Calendar.HOUR_OF_DAY))
            putInt("start_minute", startTime.get(Calendar.MINUTE))
            putInt("end_hour", endTime.get(Calendar.HOUR_OF_DAY))
            putInt("end_minute", endTime.get(Calendar.MINUTE))
            putInt("tests_per_day", testsPerDay)
            putString("url", urlInput.text.toString())
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
        val url = urlInput.text.toString()
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
            val inputData = Data.Builder()
                .putString("url", url)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<HttpTestWorker>()
                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("http_test_scheduled")
                .build()
            WorkManager.getInstance(requireContext()).enqueue(workRequest)
        }
        sharedPreferences.edit().putBoolean("schedule_enabled", true).apply()
    }
    private fun cancelScheduledTests() {
        WorkManager.getInstance(requireContext()).cancelAllWorkByTag("http_test_scheduled")
        sharedPreferences.edit().putBoolean("schedule_enabled", false).apply()
    }
} 