package com.example.polaris_client.models

/**
 * Data class for upload speed test result to be sent to the server.
 */
data class UploadTestResult(
    val longitude: Double,
    val latitude: Double,
    val UploadSpeed: Float,
    val Duration: Float, // seconds
    val time: String // ISO 8601 string
) 