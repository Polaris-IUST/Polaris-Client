package com.example.polaris_client.models

import android.R

data class DnsTestResult(
    val responseTime: Float,
    val latitude: Double,
    val longitude: Double,
    val hostname: String,
    val time: String
)
