package com.example.signallogger.model

import kotlinx.serialization.Serializable

@Serializable
data class SignalMetric(
    val operatorName: String,
    val signalStrengthDbm: Int,
    val signalLevel: Int,
    val networkType: String,
    val areaCell: String = "Unknown area",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SignalAreaSummary(
    val areaCell: String,
    val operatorName: String,
    val networkType: String,
    val averageSignalDbm: Double,
    val sampleCount: Int,
    val lastSeen: Long
)
