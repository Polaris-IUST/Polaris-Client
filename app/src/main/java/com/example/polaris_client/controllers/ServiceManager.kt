package com.example.polaris_client.controllers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat

object ServiceManager {
    
    fun startBackgroundService(context: Context) {
        // Check if all required permissions are granted
        if (!hasRequiredPermissions(context)) {
            Toast.makeText(context, "Location and Phone permissions are required for background service", Toast.LENGTH_LONG).show()
            return
        }
        
        try {
            BackgroundService.startService(context)
            Log.d("ServiceManager", "Background service started successfully")
        } catch (e: Exception) {
            Log.e("ServiceManager", "Failed to start background service", e)
            Toast.makeText(context, "Failed to start background service", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun stopBackgroundService(context: Context) {
        try {
            BackgroundService.stopService(context)
            Log.d("ServiceManager", "Background service stopped successfully")
        } catch (e: Exception) {
            Log.e("ServiceManager", "Failed to stop background service", e)
        }
    }
    
    fun isBackgroundServiceRunning(): Boolean {
        return BackgroundService.isRunning()
    }
    
    fun restartBackgroundService(context: Context) {
        stopBackgroundService(context)
        // Add a small delay to ensure the service is properly stopped
        Thread.sleep(100)
        startBackgroundService(context)
    }
    
    private fun hasRequiredPermissions(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
    
    fun getMissingPermissions(context: Context): List<String> {
        val missingPermissions = mutableListOf<String>()
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        
        return missingPermissions
    }
} 