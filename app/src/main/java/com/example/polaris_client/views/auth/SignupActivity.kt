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

        // TODO: Replace with actual API call
        // For now, we'll simulate a successful signup
        val mockToken = "mock_token_${System.currentTimeMillis()}"
        tokenManager.saveToken(mockToken)
        tokenManager.saveUserInfo("1", email, name)

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
} 