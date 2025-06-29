package com.example.polaris_client.views.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.polaris_client.views.MainActivity
import com.example.polaris_client.R
import com.example.polaris_client.utils.ThemeManager
import com.example.polaris_client.utils.TokenManager

class SplashActivity : AppCompatActivity() {
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize theme before setting content view
        ThemeManager.applyTheme(ThemeManager.isDarkMode(this))
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        tokenManager = TokenManager(this)

        // Add a small delay to show the splash screen
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthStatus()
        }, 1000)
    }

    private fun checkAuthStatus() {
        if (tokenManager.isLoggedIn()) {
            // User is already logged in, go to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // User is not logged in, go to LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
} 