package com.example.polaris_client.models

/**
 * Data class for measured latency result to be sent to the server.
 */
data class MeasuredLatencyResult(
    val longitude: Double,
    val latitude: Double,
    val avgLatency: Float,
    val jitter: Float,
    val time: String // ISO 8601 string
) 