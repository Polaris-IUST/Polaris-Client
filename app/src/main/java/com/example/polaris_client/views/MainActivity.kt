package com.example.polaris_client.views
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.polaris_client.views.ui.tests.DnsTestFragment
import com.example.polaris_client.views.ui.tests.HttpTestFragment
import com.example.polaris_client.views.ui.tests.PingTestFragment
import com.example.polaris_client.views.ui.tests.SmsTestFragment
import com.example.polaris_client.views.ui.tests.SpeedTestFragment
import com.example.polaris_client.views.ui.tests.WebTestFragment
import com.example.polaris_client.controllers.LocationService
import com.example.polaris_client.controllers.CellularService
import com.example.polaris_client.controllers.ServiceManager
import com.example.polaris_client.utils.ThemeManager
import com.example.polaris_client.R
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val PERMISSIONS_REQUEST_CODE = 123
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var locationService: LocationService
    private var waitingForBackgroundServicePermissions = false
    private var isFirstLaunch = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize theme before setting content view
        ThemeManager.applyTheme(ThemeManager.isDarkMode(this))
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Initialize location service (but don't start listening yet)
        locationService = LocationService(this)

        // Check if we're coming from MapActivity with a specific navigation item
        val navigationItemId = intent.getIntExtra("navigationItemId", -1)
        if (navigationItemId != -1) {
            navigationView.setCheckedItem(navigationItemId)
            handleNavigationItemSelected(navigationItemId)
        } else if (savedInstanceState == null) {
            // If coming from a fresh start, check permissions first, then open map
            checkPermissionsAndOpenMap()
        } else {
            // If restoring from saved state, just check permissions
            checkAndRequestPermissions()
        }
        
        // Check if we need to request permissions (coming from MapActivity)
        val requestPermissions = intent.getBooleanExtra("requestPermissions", false)
        if (requestPermissions) {
            checkAndRequestPermissions()
        }
        
        // Update navigation drawer theme item text
        updateThemeToggleText(navigationView.menu.findItem(R.id.nav_theme_toggle))
        
        // Update background service toggle text
        updateBackgroundServiceToggleText(navigationView.menu.findItem(R.id.nav_background_service))
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        // Check location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        // Check phone state permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        
        // Check SMS permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
        }
        
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Request permissions if any are missing
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            startServices()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val deniedPermissions = mutableListOf<String>()
            
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i])
                }
            }
            
            if (deniedPermissions.isEmpty()) {
                // All permissions granted
                Toast.makeText(this, "All permissions granted! App is ready to use.", Toast.LENGTH_LONG).show()
                startServices()
                
                // Restart services if needed (e.g., background service was running without permissions)
                restartServicesIfNeeded()
                
                // If user was waiting to start background service, start it now
                if (waitingForBackgroundServicePermissions) {
                    waitingForBackgroundServicePermissions = false
                    ServiceManager.startBackgroundService(this)
                    Toast.makeText(this, "Background service started", Toast.LENGTH_SHORT).show()
                    
                    // Update the menu item text
                    val navigationView: NavigationView = findViewById(R.id.nav_view)
                    updateBackgroundServiceToggleText(navigationView.menu.findItem(R.id.nav_background_service))
                } else {
                    // This was the initial permission request, open the map
                    val navigationView: NavigationView = findViewById(R.id.nav_view)
                    navigationView.setCheckedItem(R.id.nav_map)
                    openMapActivity()
                }
            } else {
                // Some permissions denied
                waitingForBackgroundServicePermissions = false
                val deniedList = deniedPermissions.joinToString(", ") { 
                    when (it) {
                        Manifest.permission.ACCESS_FINE_LOCATION -> "Location Access"
                        Manifest.permission.ACCESS_COARSE_LOCATION -> "Location Access"
                        Manifest.permission.READ_PHONE_STATE -> "Phone State"
                        Manifest.permission.SEND_SMS -> "SMS"
                        android.Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
                        else -> it
                    }
                }
                Toast.makeText(this, "Some permissions denied: $deniedList. Some features may not work properly.", Toast.LENGTH_LONG).show()
                
                // Still try to start services with available permissions
                startServices()
                
                // Open map even with partial permissions
                val navigationView: NavigationView = findViewById(R.id.nav_view)
                navigationView.setCheckedItem(R.id.nav_map)
                openMapActivity()
            }
        }
    }

    private fun startServices() {
        Log.d("MainActivity", "Starting services")
        
        // Start location service only if we have location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationService.startListening(locationService.defaultLocationListener)
        }
        
        // Start cellular service only if we have phone state permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val cellularService = CellularService(this)
            cellularService.startCollectingData()
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update background service toggle text when activity resumes
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        updateBackgroundServiceToggleText(navigationView.menu.findItem(R.id.nav_background_service))
    }

    // Helper method to handle navigation item selection
    private fun handleNavigationItemSelected(itemId: Int) {
        when (itemId) {
            R.id.nav_map -> {
                openMapActivity()
            }
            R.id.nav_http_test -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, HttpTestFragment())
                    .commit()
                supportActionBar?.title = "HTTP Throughput Test"
            }
            R.id.nav_ping_test -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, PingTestFragment())
                    .commit()
                supportActionBar?.title = "Ping Test"
            }
            R.id.nav_dns_test -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, DnsTestFragment())
                    .commit()
                supportActionBar?.title = "DNS Test"
            }
            R.id.nav_web_test -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, WebTestFragment())
                    .commit()
                supportActionBar?.title = "Web Test"
            }
            R.id.nav_sms_test -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SmsTestFragment())
                    .commit()
                supportActionBar?.title = "SMS Test"
            }
            R.id.nav_speed_test -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SpeedTestFragment())
                    .commit()
                supportActionBar?.title = "Speed Test"
            }
            R.id.nav_theme_toggle -> {
                // Toggle dark mode
                ThemeManager.toggleDarkMode(this)
                // The activity will be recreated, no need to update UI here
                return
            }
            R.id.nav_background_service -> {
                toggleBackgroundService()
                return
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        handleNavigationItemSelected(item.itemId)
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun openMapActivity() {
        val intent = Intent(this, MapActivity::class.java)
        startActivity(intent)
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
            }
        }
        
        // Update the menu item text
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        updateBackgroundServiceToggleText(navigationView.menu.findItem(R.id.nav_background_service))
    }

    private fun showPermissionExplanationDialog(missingPermissions: List<String>) {
        val permissionNames = missingPermissions.joinToString(", ") { 
            when (it) {
                Manifest.permission.ACCESS_FINE_LOCATION -> "Location Access"
                Manifest.permission.ACCESS_COARSE_LOCATION -> "Location Access"
                Manifest.permission.READ_PHONE_STATE -> "Phone State"
                Manifest.permission.SEND_SMS -> "SMS"
                android.Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
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
                // Set flag to indicate we're waiting for background service permissions
                waitingForBackgroundServicePermissions = true
                // Request the missing permissions
                ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toTypedArray(),
                    PERMISSIONS_REQUEST_CODE
                )
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

    private fun restartServicesIfNeeded() {
        // Check if background service is running but permissions were just granted
        if (ServiceManager.isBackgroundServiceRunning()) {
            // Restart the background service to ensure it has the new permissions
            ServiceManager.stopBackgroundService(this)
            ServiceManager.startBackgroundService(this)
            Toast.makeText(this, "Background service restarted with new permissions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndOpenMap() {
        // Check if we have the minimum required permissions for the map to work
        val hasLocationPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                   ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (hasLocationPermission) {
            // We have location permissions, open map directly
            val navigationView: NavigationView = findViewById(R.id.nav_view)
            navigationView.setCheckedItem(R.id.nav_map)
            openMapActivity()
        } else if (isFirstLaunch) {
            // We need to request permissions first (only on first launch)
            isFirstLaunch = false
            showInitialPermissionDialog()
        } else {
            // Not first launch, just open map without permissions
            val navigationView: NavigationView = findViewById(R.id.nav_view)
            navigationView.setCheckedItem(R.id.nav_map)
            openMapActivity()
        }
    }
    
    private fun showInitialPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Welcome to polaris_client!")
            .setMessage("This app needs location permissions to show cellular signal data on the map and collect signal information.\n\n" +
                       "• Location Access: To show your position and collect signal data\n" +
                       "• Phone State: To read cellular signal information\n\n" +
                       "Please grant these permissions to get started.")
            .setPositiveButton("Grant Permissions") { _, _ ->
                // Request permissions immediately
                checkAndRequestPermissions()
            }
            .setNegativeButton("Skip for Now") { _, _ ->
                // Open map anyway (it will show empty or limited functionality)
                val navigationView: NavigationView = findViewById(R.id.nav_view)
                navigationView.setCheckedItem(R.id.nav_map)
                openMapActivity()
            }
            .setCancelable(false)
            .show()
    }
}
