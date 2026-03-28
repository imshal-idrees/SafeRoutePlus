package com.imshal.saferouteplus

data class StaticRiskPoint (
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val type: String = "",
    val severity: Int = 1
)