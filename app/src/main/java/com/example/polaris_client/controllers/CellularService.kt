package com.example.polaris_client.controllers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.telephony.*
import android.util.Log
import androidx.core.app.ActivityCompat
import android.os.Bundle
import com.example.polaris_client.utils.DatabaseHelper
import com.example.polaris_client.utils.MapDataSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
private const val PERMISSIONS_REQUEST_CODE = 1001

class CellularService(private val context: Context) {

    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val dbHelper = DatabaseHelper(context)
    private var previousLocation: Location? = null
    private var lastSampleLocation: Location? = null
    private var lastSampleTime: Long = 0L
    private val MIN_SAMPLE_DISTANCE_M = 20f
    private val MIN_SAMPLE_INTERVAL_MS = 60_000L
    fun startCollectingData() {
        // Check for location permissions
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("CellularService", "Location permissions not granted, cannot start collecting data")
            return
        }
        
        // Check for phone state permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w("CellularService", "Phone state permission not granted, cannot start collecting data")
            return
        }

        // Request location updates from both GPS and Network providers with shorter intervals
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000L, 10f, locationListener)
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000L, 10f, locationListener)
        Log.d("CellularService", "Location updates requested.")

        // Get the last known location from both providers
        val gpsLocation: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val networkLocation: Location? = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val lastKnownLocation = gpsLocation ?: networkLocation

        if (lastKnownLocation == null) {
            Log.d("CellularService", "Location data is not available yet.")
            return
        }

        Log.d("CellularService", "Last known location: Lat=${lastKnownLocation.latitude}, Lon=${lastKnownLocation.longitude}")
        // Collect cellular data
        collectCellularData(lastKnownLocation)
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = System.currentTimeMillis()
            val lastLoc = lastSampleLocation
            val lastTime = lastSampleTime

            val timeOk = now - lastTime > MIN_SAMPLE_INTERVAL_MS
            val distOk = lastLoc == null || location.distanceTo(lastLoc) > MIN_SAMPLE_DISTANCE_M

            if (timeOk || distOk) {
                Log.d("CellularService", "Sampling: Lat=${location.latitude}, Lon=${location.longitude}")
                collectCellularData(location)
                lastSampleTime = now
                lastSampleLocation = Location(location) // clone to avoid mutation
            } else {
                Log.d("CellularService", "Sample skipped (not enough time or distance)")
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Log.d("CellularService", "Provider status changed: $provider, status: $status")
        }

        override fun onProviderEnabled(provider: String) {
            Log.d("CellularService", "Provider enabled: $provider")
        }

        override fun onProviderDisabled(provider: String) {
            Log.d("CellularService", "Provider disabled: $provider")
        }
    }

    fun collectCellularData(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude
        val timestamp = System.currentTimeMillis().toString()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("CellularService", "Location permissions not granted, cannot collect cellular data")
            return
        }
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w("CellularService", "Phone state permission not granted, cannot collect cellular data")
            return
        }
        
        val distanceWalked = calculateDistanceWalked(location)
        val cellInfoList = telephonyManager.allCellInfo
        if (cellInfoList != null && cellInfoList.isNotEmpty()) {
            for (cellInfo in cellInfoList) {
                when (cellInfo) {
                    is CellInfoLte -> {
                        val cellIdentityLte = cellInfo.cellIdentity as CellIdentityLte
                        val cellSignalStrengthLte = cellInfo.cellSignalStrength as CellSignalStrengthLte
                        val plmnId = "${cellIdentityLte.mcc}${cellIdentityLte.mnc}"
                        if (isValidPlmnId(plmnId)) {
                            val lac = cellIdentityLte.tac.toString()
                            val cellId = cellIdentityLte.ci.toString()
                            val arfcan = cellIdentityLte.earfcn
                            val bandwidth: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                cellIdentityLte.bandwidth?.let { bandwidth ->
                                    "LTE: ${bandwidth / 1000} MHz"
                                } ?: "LTE: Bandwidth unknown"
                            } else {
                                "LTE: Bandwidth unknown"
                            }
                            val signalStrength: Int
                            val signalQuality: Int
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                signalStrength = cellSignalStrengthLte.rsrp
                                signalQuality = cellSignalStrengthLte.rsrq
                            } else {
                                signalStrength = cellSignalStrengthLte.dbm
                                signalQuality = -1
                            }
                            dbHelper.insertData(latitude, longitude, timestamp, "LTE", plmnId, lac, null, lac, cellId, signalStrength, signalQuality , distanceWalked , cellIdentityLte.ci.toString() , bandwidth , arfcan , "LTE" , signalStrength)
                            MapDataSender.sendDatapoint(context,latitude, longitude, timestamp, "LTE", plmnId, lac, null, lac, cellId, signalStrength, signalQuality , distanceWalked , cellIdentityLte.ci.toString() , bandwidth , arfcan , "LTE" , signalStrength)
                            MapDataSender.flushCacheIfNeeded(context)
                            Log.d("CellularService", "Data inserted: $latitude, $longitude, $plmnId")
                        } else {
//                            Log.d("CellularService", "Invalid PLMN ID detected: $plmnId")
                        }
                    }
                    is CellInfoGsm -> {
                        val cellIdentityGsm = cellInfo.cellIdentity as CellIdentityGsm
                        val cellSignalStrengthGsm = cellInfo.cellSignalStrength as CellSignalStrengthGsm
                        val plmnId = "${cellIdentityGsm.mcc}${cellIdentityGsm.mnc}"
                        val band = "GSM: 200 kHz"
                        if (isValidPlmnId(plmnId)) {
                            val lac = cellIdentityGsm.lac.toString()
                            val cellId = cellIdentityGsm.cid.toString()
                            val signalStrength = cellSignalStrengthGsm.dbm
                            dbHelper.insertData(latitude, longitude, timestamp, "GSM", plmnId, lac, null, lac, cellId, signalStrength, getGsmSignalQuality(cellSignalStrengthGsm.asuLevel) , distanceWalked , cellIdentityGsm.cid.toString() , band , -1 , "GSM" , signalStrength)
                            MapDataSender.sendDatapoint(context,latitude, longitude, timestamp, "GSM", plmnId, lac, null, lac, cellId, signalStrength, getGsmSignalQuality(cellSignalStrengthGsm.asuLevel) , distanceWalked , cellIdentityGsm.cid.toString() , band , -1 , "GSM" , signalStrength)
                            MapDataSender.flushCacheIfNeeded(context)
                            Log.d("CellularService", "Data inserted: $latitude, $longitude, $plmnId")
                        } else {
//                            Log.d("CellularService", "Invalid PLMN ID detected: $plmnId")
                        }
                    }
                    is CellInfoCdma -> {
                        val cellIdentityCdma = cellInfo.cellIdentity as CellIdentityCdma
                        val cellSignalStrengthCdma = cellInfo.cellSignalStrength as CellSignalStrengthCdma
                        val plmnId = cellIdentityCdma.systemId.toString()
                        if (isValidPlmnId(plmnId)) {
                            val cellId = cellIdentityCdma.basestationId.toString()
                            val signalStrength = cellSignalStrengthCdma.dbm
                            val signalQuality = getCdmaSignalQuality(signalStrength)
                            val bandwidth = calculateCdmaBandwidth(cellIdentityCdma)
                            val arfcan = -1
                            val band = "CDMA: 1.25 MHz"
                            dbHelper.insertData(latitude, longitude, timestamp, "CDMA", plmnId, "", null, "", cellId, signalStrength, signalQuality,distanceWalked , cellIdentityCdma.basestationId.toString() , band , arfcan , "CDMA" , signalStrength)
                            MapDataSender.sendDatapoint(context,latitude, longitude, timestamp, "CDMA", plmnId, "", null, "", cellId, signalStrength, signalQuality,distanceWalked , cellIdentityCdma.basestationId.toString() , band , arfcan , "CDMA" , signalStrength)
                            MapDataSender.flushCacheIfNeeded(context)
                            Log.d("CellularService", "Data inserted: $latitude, $longitude, $plmnId")
                        } else {
//                            Log.d("CellularService", "Invalid PLMN ID detected: $plmnId")
                        }
                    }
                    is CellInfoWcdma -> {
                        val cellIdentityWcdma = cellInfo.cellIdentity as CellIdentityWcdma
                        val cellSignalStrengthWcdma = cellInfo.cellSignalStrength as CellSignalStrengthWcdma
                        val plmnId = "${cellIdentityWcdma.mcc}${cellIdentityWcdma.mnc}"
                        if (isValidPlmnId(plmnId)) {
                            val lac = cellIdentityWcdma.lac.toString()
                            val cellId = cellIdentityWcdma.cid.toString()
                            val arfcan = cellIdentityWcdma.uarfcn
                            val band = "WCDMA: 5 MHz"
                            val signalStrength = cellSignalStrengthWcdma.dbm
                            val signalQuality = getWcdmaSignalQuality(signalStrength)
                            dbHelper.insertData(latitude, longitude, timestamp, "WCDMA", plmnId, lac, null, lac, cellId, signalStrength, signalQuality , distanceWalked, cellIdentityWcdma.cid.toString(), band, arfcan , "WCDMA" , signalStrength)
                            MapDataSender.sendDatapoint(context,latitude, longitude, timestamp, "WCDMA", plmnId, lac, null, lac, cellId, signalStrength, signalQuality , distanceWalked, cellIdentityWcdma.cid.toString(), band, arfcan , "WCDMA" , signalStrength)
                            MapDataSender.flushCacheIfNeeded(context)
                            Log.d("CellularService", "Data inserted: $latitude, $longitude, $plmnId")
                        } else {
//                            Log.d("CellularService", "Invalid PLMN ID detected: $plmnId")
                        }
                    }
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (cellInfo is CellInfoNr) {
                                val cellIdentityNr = cellInfo.cellIdentity as CellIdentityNr
                                val cellSignalStrengthNr = cellInfo.cellSignalStrength as CellSignalStrengthNr
                                val plmnId = "${cellIdentityNr.mccString}${cellIdentityNr.mncString}"
                                if (isValidPlmnId(plmnId)) {
                                    val tac = cellIdentityNr.tac.toString()
                                    val cellId = cellIdentityNr.nci.toString()
                                    val arfcan = cellIdentityNr.nrarfcn
                                    val signalStrength = cellSignalStrengthNr.ssRsrp
                                    val signalQuality = cellSignalStrengthNr.ssRsrq
                                    val band: String
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        band = "NR: ${signalQuality / 1000} MHz"
                                    } else {
                                        band = "NR: Bandwidth unknown"
                                    }

                                        dbHelper.insertData(latitude, longitude, timestamp, "NR", plmnId, tac, null, tac, cellId, signalStrength, signalQuality,distanceWalked, cellIdentityNr.nci.toString(),
                                        band,arfcan , "NR" , signalStrength)
                                    MapDataSender.sendDatapoint(context,latitude, longitude, timestamp, "NR", plmnId, tac, null, tac, cellId, signalStrength, signalQuality,distanceWalked, cellIdentityNr.nci.toString(),
                                        band,arfcan , "NR" , signalStrength)
                                    MapDataSender.flushCacheIfNeeded(context)
                                    Log.d("CellularService", "Data inserted: $latitude, $longitude, $plmnId")
                                } else {
//                                    Log.d("CellularService", "Invalid PLMN ID detected: $plmnId")
                                }
                            }
                        } else {
                            dbHelper.insertData(latitude, longitude, timestamp, "Unknown", "", "", null, "", "", 0, 0 , distanceWalked , "" , "" , -1 ,"Unknown", 0 )
                            MapDataSender.sendDatapoint(context,latitude, longitude, timestamp, "Unknown", "", "", null, "", "", 0, 0 , distanceWalked , "" , "" , -1 ,"Unknown", 0 )
                            MapDataSender.flushCacheIfNeeded(context)
                        }
                    }
                }
            }
        } else {
            dbHelper.insertData(latitude, longitude, timestamp, "Unknown", "", "", null, "", "", 0, 0 , distanceWalked , "" , "" , -1 ,"Unknown", 0 )
            MapDataSender.sendDatapoint(context,latitude, longitude, timestamp, "Unknown", "", "", null, "", "", 0, 0 , distanceWalked , "" , "" , -1 ,"Unknown", 0 )
            MapDataSender.flushCacheIfNeeded(context)
        }
    }
    private fun calculateCdmaBandwidth(cellIdentityCdma: CellIdentityCdma): Int? {
        // Implement your logic to calculate the CDMA bandwidth here
        // For the sake of example, we will assume standard bandwidths for CDMA2000
        return when (cellIdentityCdma.networkId) {
            0, 1 -> 1250 // Example: CDMA2000 1xRTT with 1.25 MHz
            else -> 5000 // Example: CDMA2000 EV-DO with 5 MHz
        }
    }
    private fun calculateDistanceWalked(location: Location): Float {
        val distanceWalked: Float

        if (previousLocation != null) {
            distanceWalked = previousLocation!!.distanceTo(location)
        } else {
            distanceWalked = 0.0f
        }

        previousLocation = location
        return distanceWalked
    }
    private fun isValidPlmnId(plmnId: String): Boolean {
        return plmnId.length in 5..6 && plmnId.all { it.isDigit() }
    }

    fun getGsmSignalQuality(signalStrength: Int): Int {
        return when {
            signalStrength >= 12 -> 4 // Excellent
            signalStrength >= 8 -> 3  // Good
            signalStrength >= 5 -> 2  // Fair
            signalStrength > 0 -> 1   // Poor
            else -> -1 // Unknown or no signal
        }
    }

    fun getCdmaSignalQuality(signalStrength: Int): Int {
        return when {
            signalStrength >= -75 -> 4 // Excellent
            signalStrength >= -85 -> 3 // Good
            signalStrength >= -95 -> 2 // Fair
            signalStrength > -100 -> 1  // Poor
            else -> -1 // Unknown or no signal
        }
    }

    fun getWcdmaSignalQuality(signalStrength: Int): Int {
        return when {
            signalStrength >= -75 -> 4 // Excellent
            signalStrength >= -85 -> 3 // Good
            signalStrength >= -95 -> 2 // Fair
            signalStrength > -100 -> 1  // Poor
            else -> -1 // Unknown or no signal
        }
    }

}
