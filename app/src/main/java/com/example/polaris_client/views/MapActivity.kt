package com.example.polaris_client.views

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.maps.model.Marker
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.polaris_client.views.ui.tests.DnsTestFragment
import com.example.polaris_client.views.ui.tests.HttpTestFragment
import com.example.polaris_client.views.ui.tests.PingTestFragment
import com.example.polaris_client.views.ui.tests.SmsTestFragment
import com.example.polaris_client.views.ui.tests.WebTestFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.navigation.NavigationView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.example.polaris_client.controllers.ServiceManager
import com.example.polaris_client.utils.DatabaseHelper
import com.example.polaris_client.utils.ThemeManager
import com.example.polaris_client.R
import com.google.android.gms.maps.model.LatLngBounds
import android.content.Context


@SuppressLint("MissingPermission")
class MapActivity : AppCompatActivity(), OnMapReadyCallback, NavigationView.OnNavigationItemSelectedListener {

    private lateinit var mMap: GoogleMap
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var drawerLayout: DrawerLayout
    private var currentLocationMarker: Marker? = null
    private var locationManager: android.location.LocationManager? = null
    private var locationListener: android.location.LocationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize theme before setting content view
        ThemeManager.applyTheme(ThemeManager.isDarkMode(this))
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val toolbar: Toolbar = findViewById(R.id.map_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Cell Signal Map"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        drawerLayout = findViewById(R.id.map_drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.map_nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Check which item should be selected in navigation drawer
        navigationView.setCheckedItem(R.id.nav_map)
        
        // Update navigation drawer theme item text
        updateThemeToggleText(navigationView.menu.findItem(R.id.nav_theme_toggle))
        
        // Update background service toggle text
        updateBackgroundServiceToggleText(navigationView.menu.findItem(R.id.nav_background_service))

        dbHelper = DatabaseHelper(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_map -> {
                // Already on map, just close drawer
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            R.id.nav_http_test, R.id.nav_ping_test, R.id.nav_dns_test, 
            R.id.nav_web_test, R.id.nav_sms_test, R.id.nav_speed_test -> {
                // Go back to MainActivity and let it handle showing the fragment
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("navigationItemId", item.itemId)
                startActivity(intent)
                finish()
            }
            R.id.nav_theme_toggle -> {
                // Toggle dark mode
                ThemeManager.toggleDarkMode(this)
                // The activity will be recreated, no need to update UI here
                return true
            }
            R.id.nav_background_service -> {
                toggleBackgroundService()
                return true
            }
        }
        return true
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.d("MapActivity", "Map is ready")
        mMap = googleMap
        
        // Apply dark theme to the map
        try {
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this, R.raw.map_style_dark
                )
            )
            if (!success) {
                Log.e("MapActivity", "Style parsing failed")
            } else {
                Log.d("MapActivity", "Map style applied successfully")
            }
        } catch (e: Exception) {
            Log.e("MapActivity", "Can't find style. Error: ", e)
        }

        // Initialize location services
        initializeLocationServices()

        val data = dbHelper.getAllData()
        Log.d("MapActivity", "Data retrieved: ${data.size} entries")

        if (data.isEmpty()) {
            Log.d("MapActivity", "No data found in the database.")
            
            // Check if this is because of missing permissions
            val hasLocationPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                       ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (!hasLocationPermission) {
                showNoDataMessage("No cellular data available.\n\nLocation permissions are required to collect signal data.\n\nPlease grant location permissions in Settings to start collecting data.")
            } else {
                showNoDataMessage("No cellular data available.\n\nStart the background service to begin collecting signal data.\n\nGo to Settings → Start Background Service")
            }
            return
        }

        // Draw only the circle markers without connecting lines
        Log.d("MapActivity", "Displaying ${data.size} data points on map")
        
        // Get current location for camera positioning
        val currentLocation = getCurrentLocation()
        
        // Draw all data points
        for (entry in data) {
            val location = LatLng(entry.latitude, entry.longitude)
            
            // Determine the signal quality based on the technology
            val signalQuality = when(entry.technology) {
                "GSM" -> getGsmSignalQuality(entry.signalStrength)
                "CDMA" -> getCdmaSignalQuality(entry.signalStrength)
                "WCDMA" -> getWcdmaSignalQuality(entry.signalStrength)
                else -> -1
            }
            
            // Add circle marker with larger radius for better visibility
            mMap.addCircle(
                CircleOptions()
                    .center(location)
                    .radius(10.0)  // Increased radius for better visibility
                    .fillColor(getColorBySignalPower(entry.signalStrength))
                    .strokeWidth(2f)  // Add stroke for better definition
                    .strokeColor(Color.WHITE)  // White border for contrast
            )

            // Format snippet with metrics - include higher precision for coordinates
            val markerOptions = MarkerOptions()
                .position(location)
                .alpha(0f)  // Invisible marker for data handling only
                .title("Signal Quality: ${entry.signalQuality}")
                .snippet("""
            Location: (${String.format("%.6f", entry.latitude)}, ${String.format("%.6f", entry.longitude)})
            PLMN ID: ${entry.plmnId}
            LAC: ${entry.lac}
            RAC: ${entry.rac}
            TAC: ${entry.tac}
            Cell ID: ${entry.cellId}
            Band: ${entry.band}
            ARFCAN: ${entry.arfcan}
            Signal Strength: ${entry.signalStrength} [dBm] (${getSignalQualityText(signalQuality)})
            Technology: ${entry.technology}
            Node ID: ${entry.nodeId}
            Scan Tech: ${entry.scanTech}
            Scan Serving Signal Power: ${entry.scanServingSigPow} [dBm] (${getSignalQualityText(signalQuality)})
            Distance Walked: ${String.format("%.2f", entry.distanceWalked)} m
            Timestamp: ${formatTimestamp(entry.timestamp)}
        """.trimIndent())
                .icon(BitmapDescriptorFactory.defaultMarker(0f))  // Actual marker is the circle

            mMap.addMarker(markerOptions)
        }

        // Position camera based on current location or first data point
        if (currentLocation != null) {
            // Start at current location with zoom
            val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
            
            // Add current location marker (blue dot)
            addCurrentLocationMarker(currentLatLng)
            
            Toast.makeText(this, "Showing all ${data.size} data points, centered on your location", Toast.LENGTH_SHORT).show()
        } else {
            // If no current location, center on first data point
            if (data.isNotEmpty()) {
                val firstLocation = LatLng(data[0].latitude, data[0].longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstLocation, 16f))
            }
            Toast.makeText(this, "Showing all ${data.size} data points (location unavailable)", Toast.LENGTH_SHORT).show()
        }

        // Custom info window adapter
        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? {
                return null // Use default frame
            }

            override fun getInfoContents(marker: Marker): View? {
                val infoView = layoutInflater.inflate(R.layout.custom_info_window, null)
                
                // Parse the snippet text to extract data
                val snippetLines = marker.snippet?.split("\n") ?: return infoView
                
                // Map to store the parsed data
                val dataMap = mutableMapOf<String, String>()
                for (line in snippetLines) {
                    val parts = line.trim().split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        dataMap[key] = value
                    }
                }
                
                // Set values to the TextView fields with improved formatting
                infoView.findViewById<TextView>(R.id.time).text = "Time: ${dataMap["Timestamp"] ?: ""}"
                infoView.findViewById<TextView>(R.id.location).text = "Loc: ${dataMap["Location"] ?: ""}"
                infoView.findViewById<TextView>(R.id.distance).text = "Distance: ${dataMap["Distance Walked"] ?: ""}"
                infoView.findViewById<TextView>(R.id.nodeId).text = "Node Id: ${dataMap["Node ID"] ?: ""}"
                
                infoView.findViewById<TextView>(R.id.plmnId).text = "PLMN Id: ${dataMap["PLMN ID"] ?: ""}"
                infoView.findViewById<TextView>(R.id.tac_lac).text = "TAC/LAC: ${dataMap["TAC"] ?: dataMap["LAC"] ?: ""}"
                infoView.findViewById<TextView>(R.id.rac).text = "RAC: ${dataMap["RAC"] ?: ""}"
                infoView.findViewById<TextView>(R.id.cellId).text = "Cell Id: ${dataMap["Cell ID"] ?: ""}"
                infoView.findViewById<TextView>(R.id.technology).text = "Technology: ${dataMap["Technology"] ?: ""}"
                infoView.findViewById<TextView>(R.id.band).text = "Band: ${dataMap["Band"] ?: ""}"
                infoView.findViewById<TextView>(R.id.arfcn).text = "ARFCN: ${dataMap["ARFCAN"] ?: ""}"
                infoView.findViewById<TextView>(R.id.code).text = "Code: ${dataMap["Cell ID"] ?: ""}" 
                
                infoView.findViewById<TextView>(R.id.power).text = "Power: ${dataMap["Signal Strength"] ?: ""}"
                infoView.findViewById<TextView>(R.id.quality).text = "Quality: ${marker.title?.replace("Signal Quality:", "")?.trim() ?: ""}"
                infoView.findViewById<TextView>(R.id.scan_tech).text = "Scan Tech: ${dataMap["Scan Tech"] ?: ""}"
                
                infoView.findViewById<TextView>(R.id.scan_sigpow).text = 
                    "${dataMap["Scan Serving Signal Power"] ?: ""}"

                return infoView
            }
        })

        mMap.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()
            true
        }
    }

    fun formatTimestamp(timestamp: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = Date(timestamp.toLong())
        return sdf.format(date)
    }
    // Define signal quality ranges and corresponding actual color values (not just hue)
    private fun getActualColor(signalQuality: Int): Int {
        return when (signalQuality) {
            4 -> Color.rgb(0, 220, 0)     // Bright Green for very good
            3 -> Color.rgb(220, 220, 0)   // Yellow for good
            2 -> Color.rgb(255, 165, 0)   // Orange for fair
            1 -> Color.rgb(255, 0, 0)     // Red for poor
            else -> Color.rgb(150, 150, 150) // Gray for unknown
        }
    }

    // Define signal quality ranges and corresponding colors (for backward compatibility)
    fun getColorForSignalQuality(signalQuality: Int): Float {
        return when (signalQuality) {
            4 -> BitmapDescriptorFactory.HUE_BLUE   // Very good
            3 -> BitmapDescriptorFactory.HUE_GREEN  // Good
            2 -> BitmapDescriptorFactory.HUE_YELLOW // Fair
            1 -> BitmapDescriptorFactory.HUE_ORANGE // Poor
            else -> BitmapDescriptorFactory.HUE_RED // Very poor or unknown
        }
    }

    fun getGsmSignalQuality(signalStrength: Int): Int {
        return when {
            signalStrength >= -75 -> 4 // Very good
            signalStrength >= -80 -> 3  // Good
            signalStrength >= -90 -> 2  // Fair
            signalStrength > -100 -> 1   // Poor
            else -> -1 // Unknown or no signal
        }
    }

    fun getCdmaSignalQuality(signalStrength: Int): Int {
        return when {
            signalStrength >= -70 -> 4 // Very good
            signalStrength >= -80 -> 3 // Good
            signalStrength >= -90 -> 2 // Fair
            signalStrength > -100 -> 1  // Poor
            else -> -1 // Unknown or no signal
        }
    }

    fun getSignalQualityText(signalQuality: Int): String {
        return when (signalQuality) {
            4 -> "Very good"
            3 -> "Good"
            2 -> "Fair"
            1 -> "Poor"
            else -> "Very poor or unknown"
        }
    }
    fun getWcdmaSignalQuality(signalStrength: Int): Int {
        return when {
            signalStrength >= -70 -> 4 // Very good
            signalStrength >= -80 -> 3 // Good
            signalStrength >= -90 -> 2 // Fair
            signalStrength > -100 -> 1  // Poor
            else -> -1 // Unknown or no signal
        }
    }

    // Generate color based on signal power (dBm) using a continuous gradient
    private fun getColorBySignalPower(signalPower: Int): Int {
        // Signal power ranges typically from -50 dBm (excellent) to -120 dBm (very poor)
        val minPower = -120  // Very poor signal
        val maxPower = -50   // Excellent signal
        
        // Clamp the signal power to our range
        val clampedPower = when {
            signalPower > maxPower -> maxPower
            signalPower < minPower -> minPower
            else -> signalPower
        }
        
        // Calculate where this signal falls in our range (0.0 to 1.0)
        val normalizedPower = (clampedPower - minPower).toFloat() / (maxPower - minPower)
        
        // Use the normalized value to interpolate between red (poor) and green (good)
        val red: Int
        val green: Int
        val blue = 0  // No blue component in our gradient
        
        if (normalizedPower < 0.5) {
            // Poor to moderate (red to yellow)
            red = 255
            green = (normalizedPower * 2 * 255).toInt()
        } else {
            // Moderate to good (yellow to green)
            red = ((1 - normalizedPower) * 2 * 255).toInt()
            green = 255
        }
        
        return Color.rgb(red, green, blue)
    }
    
    private fun toggleBackgroundService() {
        if (ServiceManager.isBackgroundServiceRunning()) {
            ServiceManager.stopBackgroundService(this)
            Toast.makeText(this, "Background service stopped", Toast.LENGTH_SHORT).show()
        } else {
            // Check if we have all required permissions
            val missingPermissions = ServiceManager.getMissingPermissions(this)
            if (missingPermissions.isNotEmpty()) {
                showPermissionExplanationDialog(missingPermissions)
            } else {
                ServiceManager.startBackgroundService(this)
                Toast.makeText(this, "Background service started", Toast.LENGTH_SHORT).show()
                // Refresh the map to show new data
                refreshMapData()
            }
        }
        
        // Update the menu item text
        val navigationView: NavigationView = findViewById(R.id.map_nav_view)
        updateBackgroundServiceToggleText(navigationView.menu.findItem(R.id.nav_background_service))
    }
    
    private fun refreshMapData() {
        // Clear existing markers and circles
        mMap.clear()
        
        // Reload data from database
        val data = dbHelper.getAllData()
        Log.d("MapActivity", "Refreshed data: ${data.size} entries")
        
        if (data.isEmpty()) {
            Log.d("MapActivity", "No data found in the database.")
            return
        }
        
        // Get current location for camera positioning
        val currentLocation = getCurrentLocation()
        
        // Draw all data points
        for (entry in data) {
            val location = LatLng(entry.latitude, entry.longitude)
            val signalQuality = when(entry.technology) {
                "GSM" -> getGsmSignalQuality(entry.signalStrength)
                "CDMA" -> getCdmaSignalQuality(entry.signalStrength)
                "WCDMA" -> getWcdmaSignalQuality(entry.signalStrength)
                else -> -1
            }
            
            // Add circle marker with larger radius for better visibility
            mMap.addCircle(
                CircleOptions()
                    .center(location)
                    .radius(10.0)  // Increased radius for better visibility
                    .fillColor(getColorBySignalPower(entry.signalStrength))
                    .strokeWidth(2f)  // Add stroke for better definition
                    .strokeColor(Color.WHITE)  // White border for contrast
            )

            // Format snippet with metrics
            val markerOptions = MarkerOptions()
                .position(location)
                .alpha(0f)
                .title("Signal Quality: ${entry.signalQuality}")
                .snippet("""
            Location: (${String.format("%.6f", entry.latitude)}, ${String.format("%.6f", entry.longitude)})
            PLMN ID: ${entry.plmnId}
            LAC: ${entry.lac}
            RAC: ${entry.rac}
            TAC: ${entry.tac}
            Cell ID: ${entry.cellId}
            Band: ${entry.band}
            ARFCAN: ${entry.arfcan}
            Signal Strength: ${entry.signalStrength} [dBm] (${getSignalQualityText(signalQuality)})
            Technology: ${entry.technology}
            Node ID: ${entry.nodeId}
            Scan Tech: ${entry.scanTech}
            Scan Serving Signal Power: ${entry.scanServingSigPow} [dBm] (${getSignalQualityText(signalQuality)})
            Distance Walked: ${String.format("%.2f", entry.distanceWalked)} m
            Timestamp: ${formatTimestamp(entry.timestamp)}
        """.trimIndent())
                .icon(BitmapDescriptorFactory.defaultMarker(0f))

            mMap.addMarker(markerOptions)
        }
        
        // Position camera based on current location or first data point
        if (currentLocation != null) {
            // Start at current location with zoom
            val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
            
            // Add current location marker (blue dot)
            addCurrentLocationMarker(currentLatLng)
            
            Toast.makeText(this, "Refreshed: ${data.size} data points, centered on your location", Toast.LENGTH_SHORT).show()
        } else {
            // If no current location, center on first data point
            if (data.isNotEmpty()) {
                val firstLocation = LatLng(data[0].latitude, data[0].longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstLocation, 16f))
            }
            Toast.makeText(this, "Refreshed: ${data.size} data points (location unavailable)", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showPermissionExplanationDialog(missingPermissions: List<String>) {
        val permissionNames = missingPermissions.joinToString(", ") { 
            when (it) {
                android.Manifest.permission.ACCESS_FINE_LOCATION -> "Location Access"
                android.Manifest.permission.ACCESS_COARSE_LOCATION -> "Location Access"
                android.Manifest.permission.READ_PHONE_STATE -> "Phone State"
                android.Manifest.permission.SEND_SMS -> "SMS"
                else -> it
            }
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("The background service needs the following permissions to collect cellular data:\n\n" +
                       "• Location Access: To track your position for signal mapping\n" +
                       "• Phone State: To read cellular signal information\n\n" +
                       "Missing permissions: $permissionNames\n\n" +
                       "Please grant these permissions in Settings to use the background service.")
            .setPositiveButton("Grant Permissions") { _, _ ->
                // Go back to MainActivity to handle permission requests
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("requestPermissions", true)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Helper to update the theme toggle menu item text
    private fun updateThemeToggleText(menuItem: MenuItem?) {
        menuItem?.let {
            if (ThemeManager.isDarkMode(this)) {
                it.title = "Switch to Light Mode"
            } else {
                it.title = "Switch to Dark Mode"
            }
        }
    }
    
    // Helper to update the background service toggle menu item text
    private fun updateBackgroundServiceToggleText(menuItem: MenuItem?) {
        menuItem?.let {
            if (ServiceManager.isBackgroundServiceRunning()) {
                it.title = "Stop Background Service"
            } else {
                it.title = "Start Background Service"
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update background service toggle text when activity resumes
        val navigationView: NavigationView = findViewById(R.id.map_nav_view)
        updateBackgroundServiceToggleText(navigationView.menu.findItem(R.id.nav_background_service))
        
        // Start location updates when activity resumes
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        // Stop location updates when activity pauses to save battery
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up location services
        stopLocationUpdates()
        locationManager = null
        locationListener = null
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.map_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            R.id.action_refresh -> {
                refreshMapData()
                true
            }
            R.id.action_show_stats -> {
                showDataStatistics()
                true
            }
            R.id.action_fit_all -> {
                fitAllPointsToMap()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showNoDataMessage(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("No Data Available")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                // Open navigation drawer to access settings
                drawerLayout.openDrawer(GravityCompat.START)
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun showDataStatistics() {
        val data = dbHelper.getAllData()
        
        if (data.isEmpty()) {
            Toast.makeText(this, "No data available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Calculate statistics
        val totalPoints = data.size
        val technologies = data.groupBy { it.technology }.mapValues { it.value.size }
        val signalRanges = data.groupBy { 
            when {
                it.signalStrength >= -75 -> "Excellent (-50 to -75 dBm)"
                it.signalStrength >= -85 -> "Good (-75 to -85 dBm)"
                it.signalStrength >= -95 -> "Fair (-85 to -95 dBm)"
                it.signalStrength >= -105 -> "Poor (-95 to -105 dBm)"
                else -> "Very Poor (< -105 dBm)"
            }
        }.mapValues { it.value.size }
        
        // Calculate coverage area (approximate)
        val minLat = data.minOf { it.latitude }
        val maxLat = data.maxOf { it.latitude }
        val minLng = data.minOf { it.longitude }
        val maxLng = data.maxOf { it.longitude }
        
        val latDiff = maxLat - minLat
        val lngDiff = maxLng - minLng
        val coverageArea = String.format("%.2f km²", latDiff * lngDiff * 111 * 111) // Rough approximation
        
        // Build statistics message
        val statsMessage = """
            📊 Data Statistics
            
            📍 Total Data Points: $totalPoints
            
            📡 Technologies:
            ${technologies.map { "• ${it.key}: ${it.value} points" }.joinToString("\n")}
            
            📶 Signal Quality Distribution:
            ${signalRanges.map { "• ${it.key}: ${it.value} points" }.joinToString("\n")}
            
            🗺️ Coverage Area: $coverageArea
            📐 Bounds: ${String.format("%.6f", minLat)} to ${String.format("%.6f", maxLat)} lat
                     ${String.format("%.6f", minLng)} to ${String.format("%.6f", maxLng)} lng
            
            🕒 Data Collection Period:
            From: ${formatTimestamp(data.minOf { it.timestamp })}
            To: ${formatTimestamp(data.maxOf { it.timestamp })}
        """.trimIndent()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Data Statistics")
            .setMessage(statsMessage)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun getCurrentLocation(): android.location.Location? {
        return try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            
            // Check if location permissions are granted
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.w("MapActivity", "Location permissions not granted")
                return null
            }
            
            // Try to get last known location from GPS provider
            var location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            
            // If GPS location is not available, try network provider
            if (location == null) {
                location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            }
            
            // If still no location, try passive provider
            if (location == null) {
                location = locationManager.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
            }
            
            location
        } catch (e: Exception) {
            Log.e("MapActivity", "Error getting current location", e)
            null
        }
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun fitAllPointsToMap() {
        val data = dbHelper.getAllData()
        
        if (data.isEmpty()) {
            Toast.makeText(this, "No data available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create bounds builder to fit all points
        val boundsBuilder = LatLngBounds.Builder()
        
        for (entry in data) {
            val location = LatLng(entry.latitude, entry.longitude)
            boundsBuilder.include(location)
        }
        
        // Fit camera to show all data points with padding
        val bounds = boundsBuilder.build()
        val padding = 100 // padding in pixels
        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
        mMap.animateCamera(cameraUpdate)
        
        Toast.makeText(this, "Showing all ${data.size} data points", Toast.LENGTH_SHORT).show()
    }

    private fun initializeLocationServices() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        
        // Check location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("MapActivity", "Location permissions not granted")
            return
        }
        
        // Create location listener for fresh updates
        locationListener = object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                Log.d("MapActivity", "Location updated: ${location.latitude}, ${location.longitude}")
                updateCurrentLocationMarker(location)
            }
            
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                Log.d("MapActivity", "Location status changed: $provider, $status")
            }
            
            override fun onProviderEnabled(provider: String) {
                Log.d("MapActivity", "Location provider enabled: $provider")
            }
            
            override fun onProviderDisabled(provider: String) {
                Log.d("MapActivity", "Location provider disabled: $provider")
            }
        }
        
        // Request location updates
        try {
            // Request GPS updates (most accurate)
            if (locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER,
                    5000, // 5 seconds
                    10f,  // 10 meters
                    locationListener!!
                )
                Log.d("MapActivity", "GPS location updates requested")
            }
            
            // Request network updates (faster, less accurate)
            if (locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    android.location.LocationManager.NETWORK_PROVIDER,
                    3000, // 3 seconds
                    10f,  // 10 meters
                    locationListener!!
                )
                Log.d("MapActivity", "Network location updates requested")
            }
        } catch (e: Exception) {
            Log.e("MapActivity", "Error requesting location updates", e)
        }
    }
    
    private fun addCurrentLocationMarker(location: LatLng) {
        // Remove existing marker
        currentLocationMarker?.remove()
        
        // Add new blue dot marker for current location
        currentLocationMarker = mMap.addMarker(
            MarkerOptions()
                .position(location)
                .title("Your Current Location")
                .snippet("Lat: ${String.format("%.6f", location.latitude)}, Lng: ${String.format("%.6f", location.longitude)}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .zIndex(1000f) // Ensure it's on top
        )
        
        Log.d("MapActivity", "Current location marker added at: ${location.latitude}, ${location.longitude}")
    }
    
    private fun updateCurrentLocationMarker(location: android.location.Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        
        // Update existing marker or create new one
        if (currentLocationMarker != null) {
            currentLocationMarker?.position = latLng
            currentLocationMarker?.snippet = "Lat: ${String.format("%.6f", location.latitude)}, Lng: ${String.format("%.6f", location.longitude)}"
        } else {
            addCurrentLocationMarker(latLng)
        }
        
        Log.d("MapActivity", "Current location marker updated to: ${location.latitude}, ${location.longitude}")
    }

    private fun startLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            try {
                // Check permissions
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.w("MapActivity", "Location permissions not granted")
                    return
                }
                
                // Request GPS updates (most accurate)
                if (locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true) {
                    locationManager?.requestLocationUpdates(
                        android.location.LocationManager.GPS_PROVIDER,
                        5000, // 5 seconds
                        10f,  // 10 meters
                        locationListener!!
                    )
                    Log.d("MapActivity", "GPS location updates started")
                }
                
                // Request network updates (faster, less accurate)
                if (locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true) {
                    locationManager?.requestLocationUpdates(
                        android.location.LocationManager.NETWORK_PROVIDER,
                        3000, // 3 seconds
                        10f,  // 10 meters
                        locationListener!!
                    )
                    Log.d("MapActivity", "Network location updates started")
                }
            } catch (e: Exception) {
                Log.e("MapActivity", "Error starting location updates", e)
            }
        }
    }
    
    private fun stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            try {
                locationManager?.removeUpdates(locationListener!!)
                Log.d("MapActivity", "Location updates stopped")
            } catch (e: Exception) {
                Log.e("MapActivity", "Error stopping location updates", e)
            }
        }
    }
}
