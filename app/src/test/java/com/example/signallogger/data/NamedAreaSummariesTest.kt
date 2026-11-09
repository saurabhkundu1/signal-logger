package com.example.signallogger.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NamedAreaSummariesTest {
    private val now = 1_800_000_000_000L
    private fun reading(area: String, dbm: Int, offset: Long, operator: String = "Carrier") = SignalReading(
        operator, "LTE", dbm, area, 0.0, 0.0, now + offset,
    )
    private fun readingAt(dbm: Int, timestamp: String) = SignalReading(
        "Carrier", "LTE", dbm, "abcde", 0.0, 0.0, Instant.parse(timestamp).toEpochMilli(),
    )

    @Test fun filtersUnknownAreasUnavailableAndOutOfWindowReadings() {
        val result = summarizeNamedAreas(
            listOf(
                reading("abcde", -90, 0), reading("abcde", -70, -1_000),
                reading("abcde", Int.MAX_VALUE, -2_000), reading("abcde", -20, -7L * 24 * 60 * 60 * 1000 - 1),
                reading("fghij", -40, 0),
            ),
            mapOf("abcde" to "Home"), now,
        )
        assertEquals(1, result.size)
        assertEquals(-80, result.single().medianDbm)
        assertEquals(2, result.single().readingCount)
        assertEquals("Home", result.single().name)
    }

    @Test fun sortsWeakestFirstAndKeepsCarriersSeparate() {
        val result = summarizeNamedAreas(
            listOf(reading("abcde", -100, 0, "A"), reading("fghij", -60, 0, "B")),
            mapOf("abcde" to "Home", "fghij" to "Work"), now,
        )
        assertEquals(listOf("Home", "Work"), result.map { it.name })
    }

    @Test fun dailyTrendIsOrderedByLocalDateAndShowsMissingDaysWithoutInvalidSamples() {
        val trendNow = Instant.parse("2026-11-02T12:00:00Z").toEpochMilli()
        val trend = summarizeNamedAreas(
            listOf(
                readingAt(-92, "2026-10-26T12:00:00Z"),
                readingAt(-90, "2026-10-27T06:30:00Z"),
                readingAt(-80, "2026-10-27T07:30:00Z"),
                readingAt(Int.MAX_VALUE, "2026-10-30T12:00:00Z"),
                readingAt(-65, "2026-10-26T11:59:59.999Z"),
                readingAt(-70, "2026-11-02T12:00:00Z"),
            ),
            mapOf("abcde" to "Home"),
            trendNow,
            ZoneId.of("America/Los_Angeles"),
        ).single()

        assertEquals(4, trend.readingCount)
        assertEquals(
            listOf(
                "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29",
                "2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02",
            ).map(LocalDate::parse),
            trend.dailyTrend.map { it.date },
        )
        assertEquals(listOf(-91, -80, null, null, null, null, null, -70), trend.dailyTrend.map { it.medianDbm })
        assertEquals(listOf(2, 1, 0, 0, 0, 0, 0, 1), trend.dailyTrend.map { it.readingCount })
    }
}
