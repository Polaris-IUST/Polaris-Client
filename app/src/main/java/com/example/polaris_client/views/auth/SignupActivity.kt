package com.example.polaris_client.views.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.polaris_client.views.MainActivity
import com.example.polaris_client.utils.ThemeManager
import com.example.polaris_client.databinding.ActivitySignupBinding
import com.example.polaris_client.utils.TokenManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SignupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize theme before setting content view
        ThemeManager.applyTheme(ThemeManager.isDarkMode(this))
        
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        tokenManager = TokenManager(this)

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        binding.nameInput.doAfterTextChanged {
            validateName()
        }

        binding.emailInput.doAfterTextChanged {
            validateEmail()
        }

        binding.passwordInput.doAfterTextChanged {
            validatePassword()
        }

        binding.confirmPasswordInput.doAfterTextChanged {
            validateConfirmPassword()
        }
    }

    private fun setupListeners() {
        binding.signupButton.setOnClickListener {
            if (validateForm()) {
                performSignup()
            }
        }

        binding.loginLink.setOnClickListener {
            finish()
        }
    }

    private fun validateName(): Boolean {
        val name = binding.nameInput.text.toString()
        return if (name.length >= 2) {
            binding.nameLayout.error = null
            true
        } else {
            binding.nameLayout.error = "Name must be at least 2 characters"
            false
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
        return if (password.length >= 6) {
            binding.passwordLayout.error = null
            true
        } else {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            false
        }
    }

    private fun validateConfirmPassword(): Boolean {
        val password = binding.passwordInput.text.toString()
        val confirmPassword = binding.confirmPasswordInput.text.toString()
        return if (password == confirmPassword) {
            binding.confirmPasswordLayout.error = null
            true
        } else {
            binding.confirmPasswordLayout.error = "Passwords do not match"
            false
        }
    }

    private fun validateForm(): Boolean {
        return validateName() && validateEmail() && validatePassword() && validateConfirmPassword()
    }

    private fun performSignup() {
        val name = binding.nameInput.text.toString()
        val email = binding.emailInput.text.toString()
        val password = binding.passwordInput.text.toString()
        val username = name // or use a separate username field if you have one

        val client = OkHttpClient()
        val json = JSONObject()
        json.put("username", username)
        json.put("password", password)
        json.put("email",email)
        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://odysseyanalytics.ir/polaris/api/signup/")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SignupActivity, "Network error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@SignupActivity, "Signup successful! Please log in.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                        finish()
                    }
                } else {
                    val errorBody = response.body?.string()
                    val errorMsg = try {
                        JSONObject(errorBody).toString(2)
                    } catch (e: Exception) {
                        "Signup failed: ${response.code}"
                    }
                    runOnUiThread {
                        Toast.makeText(this@SignupActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
} 