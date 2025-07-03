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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.polaris_client.R
import com.example.polaris_client.controllers.DnsTestWorker
import com.example.polaris_client.models.DnsTestResult
import com.example.polaris_client.utils.DatabaseHelper
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.random.Random
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

class DnsTestFragment : Fragment() {

    private lateinit var hostnameInput: TextInputEditText
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
        val root = inflater.inflate(R.layout.fragment_dns_test, container, false)

        hostnameInput = root.findViewById(R.id.hostname_input)
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
        sharedPreferences = requireContext().getSharedPreferences("dns_test_settings", Context.MODE_PRIVATE)

        loadSettings()
        updateTimeButtons()
        testsPerDaySlider.value = testsPerDay.toFloat()
        testsPerDayText.text = testsPerDay.toString()

        startButton.setOnClickListener {
            val hostname = hostnameInput.text.toString()
            if (hostname.isEmpty()) {
                Toast.makeText(context, "Please enter a hostname", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runTest(hostname)
        }

        startTimeButton.setOnClickListener {
            showTimePickerDialog(startTime) { selectedTime ->
                startTime = selectedTime
                updateTimeButtons()
                saveSettings()
                if (scheduleSwitch.isChecked) {
                    scheduleDnsTests()
                }
            }
        }

        endTimeButton.setOnClickListener {
            showTimePickerDialog(endTime) { selectedTime ->
                endTime = selectedTime
                updateTimeButtons()
                saveSettings()
                if (scheduleSwitch.isChecked) {
                    scheduleDnsTests()
                }
            }
        }

        testsPerDaySlider.addOnChangeListener { _, value, _ ->
            testsPerDay = value.toInt()
            testsPerDayText.text = testsPerDay.toString()
            saveSettings()
            if (scheduleSwitch.isChecked) {
                scheduleDnsTests()
            }
        }

        scheduleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hostnameInput.text.toString().isEmpty()) {
                    Toast.makeText(
                        context,
                        "Please enter hostname first",
                        Toast.LENGTH_SHORT
                    ).show()
                    scheduleSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
                saveSettings()
                scheduleDnsTests()
                Toast.makeText(context, "Automated DNS testing enabled", Toast.LENGTH_SHORT).show()
            } else {
                cancelScheduledDnsTests()
                Toast.makeText(context, "Automated DNS testing disabled", Toast.LENGTH_SHORT).show()
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
        hostnameInput.setText(sharedPreferences.getString("hostname", ""))
    }

    private fun saveSettings() {
        sharedPreferences.edit().apply {
            putInt("start_hour", startTime.get(Calendar.HOUR_OF_DAY))
            putInt("start_minute", startTime.get(Calendar.MINUTE))
            putInt("end_hour", endTime.get(Calendar.HOUR_OF_DAY))
            putInt("end_minute", endTime.get(Calendar.MINUTE))
            putInt("tests_per_day", testsPerDay)
            putString("hostname", hostnameInput.text.toString())
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

    private fun scheduleDnsTests() {
        cancelScheduledDnsTests()

        val hostname = hostnameInput.text.toString()
        if (hostname.isEmpty()) {
            Toast.makeText(context, "Please enter a hostname", Toast.LENGTH_SHORT).show()
            return
        }

        val latitude = 0.0 // Optionally replace with real location
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
                .putString("hostname", hostname)
                .putInt("test_id", i)
                .putDouble("latitude", latitude)
                .putDouble("longitude", longitude)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<DnsTestWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag("dns_test_scheduled")
                .build()
            WorkManager.getInstance(requireContext()).enqueue(workRequest)
        }
        sharedPreferences.edit()
            .putBoolean("schedule_enabled", true)
            .apply()
    }

    private fun cancelScheduledDnsTests() {
        WorkManager.getInstance(requireContext()).cancelAllWorkByTag("dns_test_scheduled")
        sharedPreferences.edit()
            .putBoolean("schedule_enabled", false)
            .apply()
    }

    private fun runTest(hostname: String) {
        progressBar.visibility = View.VISIBLE
        startButton.isEnabled = false
        resultText.text = "Running DNS test..."

        Thread {
            val start = System.currentTimeMillis()
            var responseTime: Float? = null
            try {
                java.net.InetAddress.getByName(hostname)
                responseTime = (System.currentTimeMillis() - start).toFloat()
            } catch (e: Exception) {
                responseTime = null
            }

            Handler(Looper.getMainLooper()).post {
                progressBar.visibility = View.GONE
                startButton.isEnabled = true
                if (responseTime != null) {
                    resultText.text = "DNS lookup time: ${String.format("%.2f", responseTime)} ms"
                    // Optionally, upload to server and save to DB
                    val testResult = DnsTestResult(
                        responseTime = responseTime,
                        latitude = 0.0,
                        longitude = 0.0,
                        hostname = hostname,
                        time = java.time.ZonedDateTime.now().format(java.time.format.DateTimeFormatter.ISO_INSTANT)
                    )
                    sendDnsDataToServer(testResult)
                } else {
                    resultText.text = "Error running DNS test. Please check the hostname and try again."
                }
                loadPreviousResults()
            }
        }.start()
    }

    private fun sendDnsDataToServer(testResult: DnsTestResult) {
        val tokenManager = com.example.polaris_client.utils.TokenManager(requireContext())
        val token = tokenManager.getToken()
        if (token == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val client = okhttp3.OkHttpClient()
        val json = org.json.JSONObject().apply {
            put("longitude", testResult.longitude)
            put("latitude", testResult.latitude)
            put("responsetime", testResult.responseTime)
            put("hostname", testResult.hostname)
            put("time", testResult.time)
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = okhttp3.Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/dnsdata/create/")
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
                        Toast.makeText(context, "DNS data uploaded successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
                response.close()
            }
        })
    }

    private fun loadPreviousResults() {
        val results = dbHelper.getNetworkTestResults("DNS")
        recyclerView.adapter = com.example.polaris_client.views.ui.tests.TestResultAdapter(results)
    }
}