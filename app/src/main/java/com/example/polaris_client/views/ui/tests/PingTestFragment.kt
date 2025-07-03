package com.example.polaris_client.views.ui.tests

import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.polaris_client.R
import com.example.polaris_client.controllers.PingTestWorker
import com.example.polaris_client.models.PingTestResult
import com.example.polaris_client.utils.DatabaseHelper
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.random.Random
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class PingTestFragment : Fragment() {

    private lateinit var hostInput: TextInputEditText
    private lateinit var pingCountInput: TextInputEditText
    private lateinit var startButton: Button
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var testsPerDaySlider: Slider
    private lateinit var testsPerDayText: TextView
    private lateinit var scheduleSwitch: Switch
    private lateinit var progressBar: ProgressBar
    private lateinit var resultText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var sharedPreferences: SharedPreferences

    private val MIN_SECONDS_BETWEEN_TESTS = 30

    private var startTime = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
    }
    private var endTime = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 17)
        set(Calendar.MINUTE, 0)
    }
    private var testsPerDay = 3

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_ping_test, container, false)

        hostInput = root.findViewById(R.id.host_input)
        pingCountInput = root.findViewById(R.id.ping_count_input)
        startButton = root.findViewById(R.id.start_test_button)
        startTimeButton = root.findViewById(R.id.start_time_button)
        endTimeButton = root.findViewById(R.id.end_time_button)
        testsPerDaySlider = root.findViewById(R.id.tests_per_day_slider)
        testsPerDayText = root.findViewById(R.id.tests_per_day_text)
        scheduleSwitch = root.findViewById(R.id.schedule_switch)
        progressBar = root.findViewById(R.id.progress_bar)
        resultText = root.findViewById(R.id.result_text)
        recyclerView = root.findViewById(R.id.results_recycler_view)

        recyclerView.layoutManager = LinearLayoutManager(context)

        dbHelper = DatabaseHelper(requireContext())
        sharedPreferences = requireContext().getSharedPreferences("ping_test_settings", Context.MODE_PRIVATE)

        loadSettings()
        updateTimeButtons()
        testsPerDaySlider.value = testsPerDay.toFloat()
        testsPerDayText.text = testsPerDay.toString()

        startButton.setOnClickListener {
            val host = hostInput.text.toString()
            if (host.isEmpty()) {
                Toast.makeText(context, "Please enter a host", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val count = pingCountInput.text.toString().toIntOrNull() ?: 5
            runTest(host, count)
        }

        startTimeButton.setOnClickListener {
            showTimePickerDialog(startTime) { selectedTime ->
                startTime = selectedTime
                updateTimeButtons()
                saveSettings()
                if (scheduleSwitch.isChecked) {
                    schedulePingTests()
                }
            }
        }

        endTimeButton.setOnClickListener {
            showTimePickerDialog(endTime) { selectedTime ->
                endTime = selectedTime
                updateTimeButtons()
                saveSettings()
                if (scheduleSwitch.isChecked) {
                    schedulePingTests()
                }
            }
        }

        testsPerDaySlider.addOnChangeListener { _, value, _ ->
            testsPerDay = value.toInt()
            testsPerDayText.text = testsPerDay.toString()
            saveSettings()
            if (scheduleSwitch.isChecked) {
                schedulePingTests()
            }
        }

        scheduleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hostInput.text.toString().isEmpty()) {
                    Toast.makeText(
                        context,
                        "Please enter host first",
                        Toast.LENGTH_SHORT
                    ).show()
                    scheduleSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
                saveSettings()
                schedulePingTests()
                Toast.makeText(context, "Automated ping testing enabled", Toast.LENGTH_SHORT).show()
            } else {
                cancelScheduledPingTests()
                Toast.makeText(context, "Automated ping testing disabled", Toast.LENGTH_SHORT).show()
            }
        }

        if (!sharedPreferences.contains("schedule_enabled")) {
            scheduleSwitch.isChecked = false
            sharedPreferences.edit().putBoolean("schedule_enabled", false).apply()
        } else {
            scheduleSwitch.isChecked = sharedPreferences.getBoolean("schedule_enabled", false)
        }

        loadPreviousResults()

        return root
    }

    private fun loadSettings() {
        startTime.set(Calendar.HOUR_OF_DAY, sharedPreferences.getInt("start_hour", 9))
        startTime.set(Calendar.MINUTE, sharedPreferences.getInt("start_minute", 0))
        endTime.set(Calendar.HOUR_OF_DAY, sharedPreferences.getInt("end_hour", 17))
        endTime.set(Calendar.MINUTE, sharedPreferences.getInt("end_minute", 0))
        testsPerDay = sharedPreferences.getInt("tests_per_day", 3)
        hostInput.setText(sharedPreferences.getString("host", ""))
        pingCountInput.setText(sharedPreferences.getInt("ping_count", 5).toString())
    }

    private fun saveSettings() {
        sharedPreferences.edit().apply {
            putInt("start_hour", startTime.get(Calendar.HOUR_OF_DAY))
            putInt("start_minute", startTime.get(Calendar.MINUTE))
            putInt("end_hour", endTime.get(Calendar.HOUR_OF_DAY))
            putInt("end_minute", endTime.get(Calendar.MINUTE))
            putInt("tests_per_day", testsPerDay)
            putString("host", hostInput.text.toString())
            putInt("ping_count", pingCountInput.text.toString().toIntOrNull() ?: 5)
            putBoolean("schedule_enabled", scheduleSwitch.isChecked)
            apply()
        }
    }

    private fun updateTimeButtons() {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
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

    private fun schedulePingTests() {
        cancelScheduledPingTests()

        val host = hostInput.text.toString()
        if (host.isEmpty()) {
            Toast.makeText(context, "Please enter a host", Toast.LENGTH_SHORT).show()
            return
        }
        val count = pingCountInput.text.toString().toIntOrNull() ?: 5
        val latitude = 0.0 // Optionally fetch from Location
        val longitude = 0.0

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
            val randomOffset = if (secondsBetweenTests <= 4) 0 else Random.nextInt(max(1, secondsBetweenTests / 4))
            val offsetSeconds = i * secondsBetweenTests + randomOffset
            testTime.add(Calendar.SECOND, offsetSeconds)
            if (testTime.timeInMillis < now) {
                testTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            val delay = testTime.timeInMillis - now
            val inputData = Data.Builder()
                .putString("host", host)
                .putInt("count", count)
                .putInt("test_id", i)
                .putDouble("latitude", latitude)
                .putDouble("longitude", longitude)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<PingTestWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("ping_test_scheduled")
                .build()
            WorkManager.getInstance(requireContext()).enqueue(workRequest)
        }
        sharedPreferences.edit()
            .putBoolean("schedule_enabled", true)
            .apply()
    }

    private fun cancelScheduledPingTests() {
        WorkManager.getInstance(requireContext()).cancelAllWorkByTag("ping_test_scheduled")
        sharedPreferences.edit()
            .putBoolean("schedule_enabled", false)
            .apply()
    }

    private fun runTest(host: String, count: Int) {
        progressBar.visibility = View.VISIBLE
        startButton.isEnabled = false
        resultText.text = "Running ping test..."

        viewLifecycleOwner.lifecycleScope.launch {
            val networkTestService = com.example.polaris_client.controllers.NetworkTestService(requireContext())
            val testResult = networkTestService.runPingTest(host, count)
            progressBar.visibility = View.GONE
            startButton.isEnabled = true
            if (testResult != null) {
                resultText.text = "Ping avg response: ${String.format("%.2f", testResult.avgResponseTime)} ms, Success rate: ${String.format("%.2f", testResult.successRate)}%"
                sendPingDataToServer(testResult)
            } else {
                resultText.text = "Error running ping test. Please check the host and try again."
            }
            loadPreviousResults()
        }
    }

    private fun sendPingDataToServer(testResult: PingTestResult) {
        val tokenManager = com.example.polaris_client.utils.TokenManager(requireContext())
        val token = tokenManager.getToken()
        if (token.isNullOrEmpty()) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val client = okhttp3.OkHttpClient()
        val json = org.json.JSONObject().apply {
            put("longitude", testResult.longitude)
            put("latitude", testResult.latitude)
            put("avgResponseTime", testResult.avgResponseTime)
            put("suceessCountRate", testResult.successRate)
            put("time", java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_INSTANT))
        }
        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = okhttp3.Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/pingtest/create/")
            .addHeader("Authorization", "Token $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Failed to send data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                Handler(Looper.getMainLooper()).post {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Ping data uploaded successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
                response.close()
            }
        })
    }

    private fun loadPreviousResults() {
        val results = dbHelper.getNetworkTestResults("PING")
        recyclerView.adapter = com.example.polaris_client.views.ui.tests.TestResultAdapter(results)
    }
}