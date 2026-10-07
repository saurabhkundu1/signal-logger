package com.example.signallogger.data

import kotlinx.serialization.Serializable

@Serializable
data class SignalMetricDto(
    val id: String? = null,
    val operatorName: String,
    val simOperatorName: String = "",
    val countryIso: String = "",
    val signalStrengthDbm: Int,
    val signalLevel: Int,
    val networkType: String,
    val isRoaming: Boolean = false,
    val dataState: String = "Disconnected",
    val simState: String = "Unknown",
    val timestamp: Long = System.currentTimeMillis()
)
