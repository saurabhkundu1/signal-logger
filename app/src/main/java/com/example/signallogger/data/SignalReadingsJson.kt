package com.example.signallogger.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun signalReadingsJson(
    readings: List<SignalReading>,
    exportedAt: Long = System.currentTimeMillis()
): String {
    val json = Json { prettyPrint = true }
    val root = JsonObject(mapOf(
        "schema_version" to JsonPrimitive(1),
        "exported_at" to JsonPrimitive(exportedAt),
        "total_readings" to JsonPrimitive(readings.size),
        "readings" to JsonArray(readings.map { reading ->
            JsonObject(mapOf(
                "timestamp" to JsonPrimitive(reading.timestamp),
                "operator" to JsonPrimitive(reading.operatorName),
                "network_type" to JsonPrimitive(reading.networkType),
                "signal_dbm" to JsonPrimitive(reading.signalStrengthDbm),
                "area_cell" to JsonPrimitive(reading.areaCell)
            ))
        })
    ))
    return json.encodeToString(JsonObject.serializer(), root)
}
