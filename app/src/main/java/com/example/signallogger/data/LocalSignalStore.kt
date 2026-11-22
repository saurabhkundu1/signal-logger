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

data class NamedAreaSignalSummary(
    val name: String,
    val operatorName: String,
    val medianDbm: Int,
    val readingCount: Int,
    val latestReadingAt: Long,
    val dailyTrend: List<NamedAreaDailySignal>,
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
    fun areaLabels(): Map<String, String> = runCatching {
        json.decodeFromString<Map<String, String>>(preferences.getString(AREA_LABELS_KEY, "{}") ?: "{}")
    }.getOrDefault(emptyMap())

    @Synchronized
    fun namedAreaSummaries(): List<NamedAreaSignalSummary> {
        val now = System.currentTimeMillis()
        return summarizeNamedAreas(read(), areaLabels(), now)
    }

    @Synchronized
    @SuppressLint("UseKtx")
    fun setAreaLabel(areaCell: String, label: String) {
        require(areaCell.length == 5)
        val labels = areaLabels().toMutableMap()
        // shortcut: area names stop at 32 characters, raise the limit if users need longer labels.
        val name = label.trim().take(32)
        if (name.isEmpty()) labels.remove(areaCell) else labels[areaCell] = name
        check(preferences.edit().putString(AREA_LABELS_KEY, json.encodeToString(labels)).commit()) {
            "Could not save area name locally"
        }
    }

    @Synchronized
    fun toCsv(): String = signalReadingsCsv(read())

    @Synchronized
    fun toJson(): String = signalReadingsJson(read())

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

    companion object {
        private const val KEY = "readings"
        private const val AREA_LABELS_KEY = "area_labels"
    }
}
