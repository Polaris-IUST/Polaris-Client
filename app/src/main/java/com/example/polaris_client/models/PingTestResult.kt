package com.example.polaris_client.models

data class PingTestResult(
    val avgResponseTime: Float,
    val successRate: Float,
    val latitude: Double,
    val longitude: Double,
    val hostname: String
)
