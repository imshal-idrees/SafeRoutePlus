package com.imshal.saferouteplus

data class Report(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val issueType: String = "",
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)