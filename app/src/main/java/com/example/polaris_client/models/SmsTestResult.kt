package com.example.polaris_client.models

data class SmsTestResult(
    val longitude: Double,
    val latitude: Double,
    val deliveryTime: Float,
    val phoneNumber: String,
    val time: String
)
