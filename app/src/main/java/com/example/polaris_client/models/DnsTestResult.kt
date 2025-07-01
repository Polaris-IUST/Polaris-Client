package com.example.polaris_client.models

data class DnsTestResult(
    val responseTime: Float,
    val latitude: Double,
    val longitude: Double,
    val hostname: String
)
