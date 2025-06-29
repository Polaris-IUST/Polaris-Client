package com.example.polaris_client.controllers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat

class LocationService(private val context: Context) {
    private var locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var currentLocationListener: LocationListener? = null

    companion object {
        var lastKnownLocation: Location? = null
    }

    @SuppressLint("MissingPermission")
    fun startListening(locationListener: LocationListener) {
        // Check if we have location permissions before starting
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("LocationService", "Location permissions not granted, cannot start listening")
            return
        }
        
        currentLocationListener = locationListener
        // Use shorter intervals for more frequent updates (30 seconds, 10 meters)
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000L, 10f, locationListener)
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000L, 10f, locationListener)
        
        // Try to get initial location
        lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }
    
    fun stopListening() {
        currentLocationListener?.let { listener ->
            locationManager.removeUpdates(listener)
            currentLocationListener = null
        }
    }
    
    // Default location listener that updates the lastKnownLocation
    val defaultLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastKnownLocation = location
        }
        
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
}
