package com.example.polaris_client.models

/**
 * Data class for download speed test result to be sent to the server.
 */
data class DownloadTestResult(
    val longitude: Double,
    val latitude: Double,
    val DownloadSpeed: Float,
    val Duration: String, // e.g., "00:02:30" or seconds as string
    val time: String // ISO 8601 string
) 