package com.example.signallogger.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class NamedAreaDailySignal(
    val date: LocalDate,
    val medianDbm: Int?,
    val readingCount: Int,
)

internal fun summarizeNamedAreas(
    readings: List<SignalReading>,
    labels: Map<String, String>,
    now: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<NamedAreaSignalSummary> {
    val weekAgo = now - 7L * 24 * 60 * 60 * 1000
    val startDate = Instant.ofEpochMilli(weekAgo).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    return readings.asSequence()
        .filter { it.timestamp in weekAgo..now && it.areaCell in labels && it.signalStrengthDbm != Int.MAX_VALUE }
        .groupBy { it.areaCell to it.operatorName }
        .map { (key, group) ->
            val byDate = group.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate() }
            val dailyTrend = generateSequence(startDate) { if (it < today) it.plusDays(1) else null }
                .map { date ->
                    val dailyReadings = byDate[date].orEmpty()
                    NamedAreaDailySignal(
                        date = date,
                        medianDbm = dailyReadings.takeIf { it.isNotEmpty() }?.let { medianDbm(it.map(SignalReading::signalStrengthDbm)) },
                        readingCount = dailyReadings.size,
                    )
                }.toList()
            NamedAreaSignalSummary(
                labels.getValue(key.first),
                key.second,
                medianDbm(group.map(SignalReading::signalStrengthDbm)),
                group.size,
                group.maxOf { it.timestamp },
                dailyTrend,
            )
        }
        .sortedWith(compareBy<NamedAreaSignalSummary> { it.medianDbm }.thenBy { it.name }.thenBy { it.operatorName })
}

private fun medianDbm(values: List<Int>): Int {
    val strengths = values.sorted()
    val middle = strengths.size / 2
    return if (strengths.size % 2 == 1) strengths[middle] else {
        ((strengths[middle - 1] + strengths[middle]) / 2f).roundToInt()
    }
}
