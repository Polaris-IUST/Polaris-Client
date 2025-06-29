package com.example.polaris_client.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private const val THEME_PREFS = "theme_prefs"
    private const val KEY_DARK_MODE = "dark_mode"

    fun setDarkMode(activity: AppCompatActivity, isDarkMode: Boolean) {
        // Save preference
        getPrefs(activity).edit().apply {
            putBoolean(KEY_DARK_MODE, isDarkMode)
            apply()
        }

        // Apply theme
        applyTheme(isDarkMode)
        
        // Recreate activity to apply theme changes
        activity.recreate()
    }

    fun toggleDarkMode(activity: AppCompatActivity) {
        val isDarkMode = isDarkMode(activity)
        setDarkMode(activity, !isDarkMode)
    }

    fun isDarkMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DARK_MODE, false)
    }

    fun applyTheme(isDarkMode: Boolean) {
        val mode = if (isDarkMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
    }
    
    /**
     * Check if the app has notification permission (required for Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission is automatically granted on older versions
        }
    }
} 