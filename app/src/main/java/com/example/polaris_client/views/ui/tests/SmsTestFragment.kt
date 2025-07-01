package com.example.polaris_client.views.ui.tests

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.polaris_client.utils.DatabaseHelper
import com.example.polaris_client.controllers.NetworkTestService
import com.example.polaris_client.R
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Random
import java.util.concurrent.TimeUnit
import com.example.polaris_client.models.SmsTestResult
import com.example.polaris_client.utils.TokenManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SmsTestFragment : Fragment(), NetworkTestService.SmsDeliveryListener {

    private lateinit var phoneInput: TextInputEditText
    private lateinit var messageInput: TextInputEditText
    private lateinit var startButton: Button
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var testsPerDaySlider: Slider
    private lateinit var testsPerDayText: TextView
    private lateinit var scheduleSwitch: Switch
    private lateinit var progressBar: ProgressBar
    private lateinit var resultText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var networkTestService: NetworkTestService
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var sharedPreferences: SharedPreferences

    private val SMS_PERMISSION_REQUEST_CODE = 101
    private val ALARM_ACTION = "com.example.polaris_client.SMS_TEST_ALARM"
    private val SMS_DELIVERY_TIMEOUT = 60000L // 60 seconds timeout for SMS delivery
    private val MIN_SECONDS_BETWEEN_TESTS = 30 // Minimum 30 seconds between tests

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
        val root = inflater.inflate(R.layout.fragment_sms_test, container, false)

        phoneInput = root.findViewById(R.id.phone_input)
        messageInput = root.findViewById(R.id.message_input)
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

        networkTestService = NetworkTestService(requireContext())
        dbHelper = DatabaseHelper(requireContext())
        sharedPreferences =
            requireContext().getSharedPreferences("sms_test_settings", Context.MODE_PRIVATE)

        // Load saved settings
        loadSettings()

        // Set UI elements to saved values
        updateTimeButtons()

        testsPerDaySlider.value = testsPerDay.toFloat()
        testsPerDayText.text = testsPerDay.toString()

        // Set up listeners for UI components
        startButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString()
            val message = messageInput.text.toString()

            if (phoneNumber.isEmpty()) {
                Toast.makeText(context, "Please enter a phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (message.isEmpty()) {
                Toast.makeText(context, "Please enter a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check for SMS permission
            if (hasSmsPermission()) {
                runTest(phoneNumber, message)
            } else {
                requestSmsPermission()
            }
        }

        startTimeButton.setOnClickListener {
            showTimePickerDialog(startTime) { selectedTime ->
                startTime = selectedTime
                updateTimeButtons()
                saveSettings()
                if (scheduleSwitch.isChecked) {
                    scheduleTests()
                }
            }
        }

        endTimeButton.setOnClickListener {
            showTimePickerDialog(endTime) { selectedTime ->
                endTime = selectedTime
                updateTimeButtons()
                saveSettings()
                if (scheduleSwitch.isChecked) {
                    scheduleTests()
                }
            }
        }

        testsPerDaySlider.addOnChangeListener { _, value, _ ->
            testsPerDay = value.toInt()
            testsPerDayText.text = testsPerDay.toString()
            saveSettings()
            if (scheduleSwitch.isChecked) {
                scheduleTests()
            }
        }

        scheduleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (phoneInput.text.toString().isEmpty() || messageInput.text.toString()
                        .isEmpty()
                ) {
                    Toast.makeText(
                        context,
                        "Please enter phone number and message first",
                        Toast.LENGTH_SHORT
                    ).show()
                    scheduleSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }

                saveSettings()
                scheduleTests()
                Toast.makeText(context, "Automated testing enabled", Toast.LENGTH_SHORT).show()
            } else {
                cancelScheduledTests()
                Toast.makeText(context, "Automated testing disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Initially set the switch state
        scheduleSwitch.isChecked = sharedPreferences.getBoolean("schedule_enabled", false)

        loadPreviousResults()

        return root
    }

    private fun loadSettings() {
        startTime.set(Calendar.HOUR_OF_DAY, sharedPreferences.getInt("start_hour", 9))
        startTime.set(Calendar.MINUTE, sharedPreferences.getInt("start_minute", 0))

        endTime.set(Calendar.HOUR_OF_DAY, sharedPreferences.getInt("end_hour", 17))
        endTime.set(Calendar.MINUTE, sharedPreferences.getInt("end_minute", 0))

        testsPerDay = sharedPreferences.getInt("tests_per_day", 3)

        phoneInput.setText(sharedPreferences.getString("phone_number", ""))
        messageInput.setText(sharedPreferences.getString("message", ""))
    }

    private fun saveSettings() {
        sharedPreferences.edit().apply {
            putInt("start_hour", startTime.get(Calendar.HOUR_OF_DAY))
            putInt("start_minute", startTime.get(Calendar.MINUTE))

            putInt("end_hour", endTime.get(Calendar.HOUR_OF_DAY))
            putInt("end_minute", endTime.get(Calendar.MINUTE))

            putInt("tests_per_day", testsPerDay)

            putString("phone_number", phoneInput.text.toString())
            putString("message", messageInput.text.toString())

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

    private fun scheduleTests() {
        // First cancel any existing scheduled tests
        cancelScheduledTests()

        // Calculate the testing window duration in seconds
        val startTimeSeconds =
            startTime.get(Calendar.HOUR_OF_DAY) * 3600 + startTime.get(Calendar.MINUTE) * 60
        val endTimeSeconds =
            endTime.get(Calendar.HOUR_OF_DAY) * 3600 + endTime.get(Calendar.MINUTE) * 60

        // Handle case where end time is before start time (next day)
        var windowDurationSeconds = if (endTimeSeconds > startTimeSeconds) {
            endTimeSeconds - startTimeSeconds
        } else {
            (24 * 3600) - startTimeSeconds + endTimeSeconds
        }

        // Calculate intervals for tests
        var secondsBetweenTests = windowDurationSeconds / testsPerDay

        // Ensure minimum time between tests
        if (secondsBetweenTests < MIN_SECONDS_BETWEEN_TESTS) {
            secondsBetweenTests = MIN_SECONDS_BETWEEN_TESTS
            // Adjust tests per day based on minimum time between tests
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

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val phoneNumber = phoneInput.text.toString()
        val message = messageInput.text.toString()

        // Schedule tests
        for (i in 0 until testsPerDay) {
            val testTime = Calendar.getInstance()

            // Set to today's start time first
            testTime.set(Calendar.HOUR_OF_DAY, startTime.get(Calendar.HOUR_OF_DAY))
            testTime.set(Calendar.MINUTE, startTime.get(Calendar.MINUTE))
            testTime.set(Calendar.SECOND, 0)

            // Add interval seconds plus a small random offset to avoid exact repetition
            val randomOffset = if (secondsBetweenTests <= 4) {
                0 // No randomization for very small intervals
            } else {
                Random().nextInt(Math.max(1, secondsBetweenTests / 4))
            }
            val offsetSeconds = i * secondsBetweenTests + randomOffset
            testTime.add(Calendar.SECOND, offsetSeconds)

            // If time has already passed today, schedule for tomorrow
            if (testTime.timeInMillis < System.currentTimeMillis()) {
                testTime.add(Calendar.DAY_OF_YEAR, 1)
            }

            val intent = Intent(requireContext(), SmsAlarmReceiver::class.java).apply {
                action = ALARM_ACTION
                putExtra("phone_number", phoneNumber)
                putExtra("message", message)
                putExtra("test_id", i)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                1000 + i, // Use different request codes for each alarm
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Schedule repeating alarm
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                testTime.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }

        // Save scheduled state
        sharedPreferences.edit()
            .putBoolean("schedule_enabled", true)
            .apply()
    }

    private fun cancelScheduledTests() {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel all possible test alarms
        for (i in 0 until 24) { // Assuming maximum 24 tests per day
            val intent = Intent(requireContext(), SmsAlarmReceiver::class.java).apply {
                action = ALARM_ACTION
            }

            val pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                1000 + i,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }

        // Save scheduled state
        sharedPreferences.edit()
            .putBoolean("schedule_enabled", false)
            .apply()
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }

    override fun onResume() {
        super.onResume()
        // Reload results when fragment becomes visible
        loadPreviousResults()
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.SEND_SMS),
            SMS_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, try again
                val phoneNumber = phoneInput.text.toString()
                val message = messageInput.text.toString()

                if (phoneNumber.isNotEmpty() && message.isNotEmpty()) {
                    runTest(phoneNumber, message)
                }
            } else {
                Toast.makeText(
                    context,
                    "SMS permission denied. Cannot run test.",
                    Toast.LENGTH_SHORT
                ).show()
                // Disable the schedule switch if permission is denied
                scheduleSwitch.isChecked = false
            }
        }
    }

    // Sends an SmsTestResult to the server
    private fun sendSmsDataToServer(context: Context, result: SmsTestResult) {
        val tokenManager = TokenManager(context)
        val token = tokenManager.getToken()

        if (token == null) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val client = OkHttpClient()

        val json = JSONObject().apply {
            put("longitude", result.longitude)
            put("latitude", result.latitude)
            put("deliveryTime", result.deliveryTime)
            put("phoneNumber", result.phoneNumber)
            put("time", result.time)
        }

        val requestBody =
            json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/smstest/create/")
            .addHeader("Authorization", "Token $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Failed to send SMS data: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                Handler(Looper.getMainLooper()).post {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            context,
                            "SMS data uploaded successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Upload failed: ${response.code}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun runTest(phoneNumber: String, message: String) {
        progressBar.visibility = View.VISIBLE
        startButton.isEnabled = false
        resultText.text = "Sending SMS and waiting for delivery..."

        // Run SMS test with this fragment as listener
        networkTestService.runSmsTest(
            phoneNumber,
            message,
            object : NetworkTestService.SmsDeliveryListener {
                override fun onSmsDelivered(result: SmsTestResult) {
                    progressBar.visibility = View.GONE
                    startButton.isEnabled = true
                    resultText.text = "SMS delivered in ${result.deliveryTime} seconds"

                    // Send data to server
                    sendSmsDataToServer(requireContext(), result)
                }

                override fun onSmsDeliveryFailed(error: String) {
                    progressBar.visibility = View.GONE
                    startButton.isEnabled = true
                    resultText.text = "SMS delivery failed: $error"
                    Toast.makeText(
                        requireContext(),
                        "SMS delivery failed: $error",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

        // Timeout handler
        Handler(Looper.getMainLooper()).postDelayed({
            if (!startButton.isEnabled) {
                onSmsDeliveryFailed("Delivery timeout after ${SMS_DELIVERY_TIMEOUT / 1000} seconds")
            }
        }, SMS_DELIVERY_TIMEOUT)
    }

    override fun onSmsDelivered(result: SmsTestResult) {
        activity?.runOnUiThread {
            progressBar.visibility = View.GONE
            startButton.isEnabled = true
            resultText.text = "SMS delivery time: ${result.deliveryTime} s\n" +
                    "Phone: ${result.phoneNumber}\n" +
                    "Location: (${result.latitude}, ${result.longitude})\n" +
                    "Time: ${result.time}"

            // Optionally save to database if needed here
            dbHelper.insertNetworkTestData(
                "SMS",
                (result.deliveryTime * 1000).toString(), // save in ms if needed
                result.phoneNumber,
                result.latitude,
                result.longitude
            )

            loadPreviousResults()
        }
    }

    override fun onSmsDeliveryFailed(error: String) {
        activity?.runOnUiThread {
            progressBar.visibility = View.GONE
            startButton.isEnabled = true
            resultText.text = "SMS delivery failed: $error"

            // Also log failed test in database
            val dbHelper = DatabaseHelper(requireContext())
            dbHelper.insertNetworkTestData(
                "SMS",
                "-1", // Use -1 to indicate failure
                "Failed: $error",
                0.0, // latitude
                0.0  // longitude
            )

            loadPreviousResults()
        }
    }

    private fun formatReadableTime(timeMs: Long): String {
        return when {
            timeMs < 1000 -> "${timeMs}ms"
            timeMs < 60000 -> "${String.format("%.1f", timeMs / 1000.0)}s"
            else -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs) % 60
                "${minutes}m ${seconds}s"
            }
        }
    }

    private fun loadPreviousResults() {
        val results = dbHelper.getNetworkTestResults("SMS")
        recyclerView.adapter = TestResultAdapter(results)
    }

    // Broadcast receiver to handle scheduled SMS tests
    class SmsAlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.polaris_client.SMS_TEST_ALARM") {
                val phoneNumber = intent.getStringExtra("phone_number") ?: return
                val message = intent.getStringExtra("message") ?: return
                val testId = intent.getIntExtra("test_id", 0)

                // Add test ID to message to differentiate scheduled tests
                val testMessage = "$message (Test #$testId)"

                // Run the test
                val networkTestService = NetworkTestService(context)
                val dbHelper = DatabaseHelper(context)

                networkTestService.runSmsTest(
                    phoneNumber,
                    testMessage,
                    object : NetworkTestService.SmsDeliveryListener {
                        override fun onSmsDelivered(result: SmsTestResult) {
                            // Log the test result to database when running in background
                            dbHelper.insertNetworkTestData(
                                "SMS",
                                (result.deliveryTime * 1000).toString(), // Convert seconds to ms if needed
                                "To: ${result.phoneNumber}, Time: ${result.time}",
                                result.latitude,
                                result.longitude
                            )
                            // Send to server from background
                            SmsTestFragment().sendSmsDataToServer(context, result)
                        }

                        override fun onSmsDeliveryFailed(error: String) {
                            // Log the failed test to database
                            dbHelper.insertNetworkTestData(
                                "SMS",
                                "-1", // Use -1 to indicate failure
                                "Failed: $error, To: $phoneNumber, Msg: $testMessage",
                                0.0, // latitude
                                0.0  // longitude
                            )
                        }
                    }
                )

                // Set timeout for SMS delivery
                Handler(Looper.getMainLooper()).postDelayed({
                    // This is a simplified timeout mechanism for automated tests
                    // Actual implementation would need to track each test's state
                }, 60000) // 60 seconds timeout
            }
        }
    }
}