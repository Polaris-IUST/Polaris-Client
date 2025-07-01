package com.example.polaris_client.views.auth

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.polaris_client.views.MainActivity
import com.example.polaris_client.R
import com.example.polaris_client.utils.ThemeManager
import com.example.polaris_client.utils.TokenManager
import com.example.polaris_client.databinding.ActivityLoginBinding
import com.google.android.material.textfield.TextInputLayout
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize theme before setting content view
        ThemeManager.applyTheme(ThemeManager.isDarkMode(this))
        
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        tokenManager = TokenManager(this)
        prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)

        val savedUsername = prefs.getString("username", null)
        val savedPassword = prefs.getString("password", null)
        if (savedUsername != null && savedPassword != null) {
            // Optionally show a loading spinner here
            performLogin(savedUsername, savedPassword)
        } else {
            setupViews()
            setupListeners()
        }
    }

    private fun setupViews() {
        // Add validation for email and password fields
        binding.emailInput.doAfterTextChanged {
            validateEmail()
        }

        binding.passwordInput.doAfterTextChanged {
            validatePassword()
        }
    }

    private fun setupListeners() {
        binding.loginButton.setOnClickListener {
            if (validateForm()) {
                performLogin(
                    binding.emailInput.text.toString(),
                    binding.passwordInput.text.toString()
                )
            }
        }

        binding.signupLink.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun validateEmail(): Boolean {
        val email = binding.emailInput.text.toString()
        return if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = null
            true
        } else {
            binding.emailLayout.error = "Invalid email address"
            false
        }
    }

    private fun validatePassword(): Boolean {
        val password = binding.passwordInput.text.toString()
        return if (password.length >= 4) {
            binding.passwordLayout.error = null
            true
        } else {
            binding.passwordLayout.error = "Password must be at least 4 characters"
            false
        }
    }

    private fun validateForm(): Boolean {
        return validateEmail() && validatePassword()
    }

    private fun performLogin(username: String, password: String) {
        binding.loginButton.isEnabled = false
        val client = OkHttpClient()
        val json = """
            {\n                \"email\": \"$username\",\n                \"password\": \"$password\"\n            }
        """.trimIndent()
        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/api-token-auth/")
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    binding.loginButton.isEnabled = true
                    Toast.makeText(this@LoginActivity, "Network error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    binding.loginButton.isEnabled = true
                }
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val token = JSONObject(responseBody).optString("token", null)
                    if (token != null) {
                        tokenManager.saveToken(token)
                        tokenManager.saveUserInfo("1", username, "Test User")
                        // Save credentials for auto-login
                        prefs.edit()
                            .putString("email", username)
                            .putString("password", password)
                            .apply()
                        runOnUiThread {
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@LoginActivity, "Invalid server response", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (response.code == 400) {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Invalid username or password", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Login failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
} 