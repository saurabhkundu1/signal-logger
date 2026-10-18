package com.example.signallogger.repository

import com.example.signallogger.model.SignalAreaSummary
import com.example.signallogger.model.SignalMetric
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Persistent anonymous crowdsourced signal samples, grouped by coarse area cell. */
class SignalRepository(
    private val dataFile: File = File(System.getenv("DATA_DIR") ?: "data", "signal-metrics.jsonl")
) {
    private val metrics = mutableListOf<SignalMetric>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        if (dataFile.isFile) {
            val fileLineCount = dataFile.useLines { it.count(String::isNotBlank) }
            val retentionCutoff = System.currentTimeMillis() - RETENTION_MS
            dataFile.useLines { lines ->
                lines.filter(String::isNotBlank).forEach { line ->
                    runCatching { json.decodeFromString<SignalMetric>(line) }
                        .getOrNull()?.takeIf { it.timestamp >= retentionCutoff }?.let(metrics::add)
                }
            }
            if (metrics.size != fileLineCount || fileLineCount > 0) writeDataFile()
        }
    }

    @Synchronized
    fun addMetric(metric: SignalMetric): SignalMetric {
        validate(metric)
        val entry = metric.copy(
            operatorName = metric.operatorName.trim(),
            areaCell = metric.areaCell.lowercase(),
            timestamp = System.currentTimeMillis()
        )
        dataFile.parentFile?.mkdirs()
        dataFile.appendText(json.encodeToString(entry) + "\n", Charsets.UTF_8)
        metrics += entry
        if (metrics.size % 1_000 == 0) {
            val retentionCutoff = entry.timestamp - RETENTION_MS
            metrics.removeAll { it.timestamp < retentionCutoff }
            writeDataFile()
        }
        return entry
    }

    @Synchronized
    fun getAreaSummaries(): List<SignalAreaSummary> = metrics
        .asSequence()
        .filter { it.timestamp >= System.currentTimeMillis() - SUMMARY_WINDOW_MS }
        .filter { it.areaCell.matches(GEOHASH) }
        .groupBy { Triple(it.areaCell, it.operatorName, it.networkType) }
        .map { (key, samples) ->
            SignalAreaSummary(
                areaCell = key.first,
                operatorName = key.second,
                networkType = key.third,
                averageSignalDbm = samples.map { it.signalStrengthDbm }.average(),
                sampleCount = samples.size,
                lastSeen = samples.maxOf(SignalMetric::timestamp)
            )
        }
        .sortedByDescending(SignalAreaSummary::lastSeen)
        .take(500)

    private fun validate(metric: SignalMetric) {
        require(metric.operatorName.isNotBlank() && metric.operatorName.length <= 80) {
            "Operator name must be 1 to 80 characters"
        }
        require(metric.signalStrengthDbm in -160..0) { "Signal strength is outside the valid range" }
        require(metric.signalLevel in 0..4) { "Signal level must be from 0 to 4" }
        require(metric.areaCell == "Unknown area" || metric.areaCell.matches(GEOHASH)) {
            "Area must be a five-character coarse geohash"
        }
        require(metric.networkType.length <= 24) { "Network type is too long" }
    }

    private fun writeDataFile() {
        dataFile.parentFile?.mkdirs()
        dataFile.writeText(metrics.joinToString(separator = "\n", postfix = if (metrics.isEmpty()) "" else "\n") {
            json.encodeToString(it)
        }, Charsets.UTF_8)
    }

    companion object {
        private val GEOHASH = Regex("[0123456789bcdefghjkmnpqrstuvwxyz]{5}")
        private const val SUMMARY_WINDOW_MS = 30L * 24 * 60 * 60 * 1_000
        private const val RETENTION_MS = 180L * 24 * 60 * 60 * 1_000
    }
}
