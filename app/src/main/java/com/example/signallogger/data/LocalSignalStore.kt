package com.example.signallogger.data

import android.content.Context
import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class SignalReading(
    val operatorName: String,
    val networkType: String,
    val signalStrengthDbm: Int,
    val areaCell: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

class LocalSignalStore(context: Context) {
    private val preferences = context.getSharedPreferences("signal_atlas_readings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    @SuppressLint("UseKtx")
    fun append(reading: SignalReading) {
        val items = read().toMutableList().apply {
            add(reading)
            if (size > 2000) removeAt(0)
        }
        // Keep the commit result so a full storage failure is not reported as a successful save.
        check(preferences.edit().putString(KEY, json.encodeToString(items)).commit()) {
            "Could not save reading locally"
        }
    }

    @Synchronized
    fun read(): List<SignalReading> = runCatching {
        json.decodeFromString<List<SignalReading>>(preferences.getString(KEY, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    @Synchronized
    fun toCsv(): String = buildString {
        appendLine("timestamp,operator,network_type,signal_dbm")
        read().forEach { reading ->
            appendLine(listOf(
                reading.timestamp.toString(), csvText(reading.operatorName), csvText(reading.networkType),
                reading.signalStrengthDbm.toString()
            ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
        }
    }

    @Synchronized
    fun toGeoJson(): String = JsonObject(mapOf(
        "type" to JsonPrimitive("FeatureCollection"),
        "features" to JsonArray(read().map { reading ->
            JsonObject(mapOf(
                "type" to JsonPrimitive("Feature"),
                "geometry" to JsonObject(mapOf(
                    "type" to JsonPrimitive("Point"),
                    "coordinates" to JsonArray(listOf(JsonPrimitive(reading.longitude), JsonPrimitive(reading.latitude)))
                )),
                "properties" to JsonObject(mapOf(
                    "operator" to JsonPrimitive(reading.operatorName),
                    "networkType" to JsonPrimitive(reading.networkType),
                    "signalStrengthDbm" to JsonPrimitive(reading.signalStrengthDbm),
                    "areaCell" to JsonPrimitive(reading.areaCell),
                    "timestamp" to JsonPrimitive(reading.timestamp)
                ))
            ))
        })
    )).toString()

    private fun csvText(value: String) = if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value

    companion object { private const val KEY = "readings" }
}
