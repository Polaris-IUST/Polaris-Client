package com.example.polaris_client.views.auth

import android.content.Intent
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

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize theme before setting content view
        ThemeManager.applyTheme(ThemeManager.isDarkMode(this))
        
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        tokenManager = TokenManager(this)

        setupViews()
        setupListeners()
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
                performLogin()
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
        return if (password.length >= 6) {
            binding.passwordLayout.error = null
            true
        } else {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            false
        }
    }

    private fun validateForm(): Boolean {
        return validateEmail() && validatePassword()
    }

    private fun performLogin() {
        val email = binding.emailInput.text.toString()
        val password = binding.passwordInput.text.toString()

        // TODO: Replace with actual API call
        // For now, we'll simulate a successful login
        val mockToken = "mock_token_${System.currentTimeMillis()}"
        tokenManager.saveToken(mockToken)
        tokenManager.saveUserInfo("1", email, "Test User")

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
} 