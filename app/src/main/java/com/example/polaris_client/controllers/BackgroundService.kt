package com.example.polaris_client.controllers

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.polaris_client.views.MainActivity
import com.example.polaris_client.R
import com.example.polaris_client.utils.ThemeManager
import java.util.*
import android.location.Location

class BackgroundService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "PolarisClientBackgroundService"
        private const val CHANNEL_NAME = "PolarisClient Background Service"
        private const val CHANNEL_DESCRIPTION = "Background service for cellular data collection"
        private const val DATA_COLLECTION_INTERVAL = 15000L // 15 seconds
        
        private var isServiceRunning = false
        
        fun startService(context: Context) {
            val intent = Intent(context, BackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundService::class.java)
            context.stopService(intent)
        }
        
        fun isRunning(): Boolean = isServiceRunning
    }
    
    private lateinit var locationService: LocationService
    private lateinit var cellularService: CellularService
    private var dataCollectionCount = 0
    private var dataCollectionTimer: Timer? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d("BackgroundService", "Service created")
        createNotificationChannel()
        
        // Initialize services
        locationService = LocationService(this)
        cellularService = CellularService(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BackgroundService", "Service started")
        isServiceRunning = true
        
        // Handle stop action
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Start foreground service with notification (only if we have permission)
        if (ThemeManager.hasNotificationPermission(this)) {
            startForeground(NOTIFICATION_ID, createNotification())
        } else {
            Log.w("BackgroundService", "POST_NOTIFICATIONS permission not granted, starting service without notification")
            // Start the service without notification - this will work but user won't see the notification
            // The service will still run in the background
        }
        
        // Start data collection
        startDataCollection()
        // Try to flush cached datapoints on service start
        com.example.polaris_client.utils.MapDataSender.flushCacheIfNeeded(this)
        
        // Return START_STICKY to restart service if killed
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("BackgroundService", "Service destroyed")
        isServiceRunning = false
        
        // Stop location updates
        locationService.stopListening()
        
        // Stop timer
        dataCollectionTimer?.cancel()
        dataCollectionTimer = null
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        // Check if we have notification permission
        if (!ThemeManager.hasNotificationPermission(this)) {
            Log.w("BackgroundService", "POST_NOTIFICATIONS permission not granted, cannot create notification")
            // Return a minimal notification that won't be displayed
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PolarisClient")
                .setContentText("Notification permission required")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        }
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create stop service intent
        val stopIntent = Intent(this, BackgroundService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PolarisClient Running")
            .setContentText("Collecting cellular data... ($dataCollectionCount samples)")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun updateNotification() {
        if (ThemeManager.hasNotificationPermission(this)) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        } else {
            Log.w("BackgroundService", "POST_NOTIFICATIONS permission not granted, cannot update notification")
        }
    }
    private var lastSampleTime: Long = 0L
    private var lastSampleLocation: Location? = null
    private val MIN_SAMPLE_DISTANCE_M = 20f
    private val MIN_SAMPLE_INTERVAL_MS = 60_000L // 60 seconds

    private fun shouldSample(location: Location): Boolean {
        val now = System.currentTimeMillis()
        val lastLoc = lastSampleLocation
        val lastTime = lastSampleTime

        val timeOk = now - lastTime > MIN_SAMPLE_INTERVAL_MS
        val distOk = lastLoc == null || location.distanceTo(lastLoc) > MIN_SAMPLE_DISTANCE_M

        return timeOk || distOk
    }

    private fun recordSample(location: Location) {
        lastSampleTime = System.currentTimeMillis()
        lastSampleLocation = Location(location)
    }

    private fun startDataCollection() {
        // Check permissions before starting location services
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("BackgroundService", "Location permissions not granted, cannot start data collection")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w("BackgroundService", "Phone state permission not granted, cannot start data collection")
            return
        }

        // Start location service
        locationService.startListening(object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                // Update location in LocationService
                LocationService.lastKnownLocation = location

                if (shouldSample(location)) {
                    cellularService.collectCellularData(location)
                    recordSample(location)
                    dataCollectionCount++
                    updateNotification()
                    Log.d("BackgroundService", "Data collected from location change: $dataCollectionCount samples")
                    com.example.polaris_client.utils.MapDataSender.flushCacheIfNeeded(this@BackgroundService)
                } else {
                    Log.d("BackgroundService", "Sample skipped (not enough time or distance)")
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        })

        // Start cellular data collection
        cellularService.startCollectingData()

        // Start timer for periodic data collection (even when location doesn't change)
        dataCollectionTimer = Timer()
        dataCollectionTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                LocationService.lastKnownLocation?.let { location ->
                    if (shouldSample(location)) {
                        cellularService.collectCellularData(location)
                        recordSample(location)
                        dataCollectionCount++
                        updateNotification()
                        Log.d("BackgroundService", "Periodic data collected: $dataCollectionCount samples")
                        com.example.polaris_client.utils.MapDataSender.flushCacheIfNeeded(this@BackgroundService)
                    } else {
                        Log.d("BackgroundService", "Periodic sample skipped (not enough time or distance)")
                    }
                }
            }
        }, DATA_COLLECTION_INTERVAL, DATA_COLLECTION_INTERVAL)
    }
} 