package com.example.signallogger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalReadingsJsonTest {

    @Test
    fun exportsStructuredJsonWithDocumentedFields() {
        val reading1 = SignalReading("Verizon", "LTE", -85, "c1a2b", 37.7749, -122.4194, 1700000000000L)
        val reading2 = SignalReading("T-Mobile", "5G", -70, "d3e4f", 37.7750, -122.4195, 1700000060000L)
        val jsonString = signalReadingsJson(listOf(reading1, reading2), exportedAt = 1700000100000L)

        assertTrue(jsonString.contains("\"schema_version\": 1"))
        assertTrue(jsonString.contains("\"total_readings\": 2"))
        assertTrue(jsonString.contains("\"operator\": \"Verizon\""))
        assertTrue(jsonString.contains("\"network_type\": \"LTE\""))
        assertTrue(jsonString.contains("\"signal_dbm\": -85"))
        assertTrue(jsonString.contains("\"area_cell\": \"c1a2b\""))
        assertTrue(jsonString.contains("\"operator\": \"T-Mobile\""))

        // Excludes raw coordinates for privacy in standard json export
        assertFalse(jsonString.contains("37.7749"))
        assertFalse(jsonString.contains("-122.4194"))
        assertFalse(jsonString.contains("latitude"))
        assertFalse(jsonString.contains("longitude"))
    }

    @Test
    fun emptyList_exportsValidJsonWithZeroCount() {
        val jsonString = signalReadingsJson(emptyList(), exportedAt = 1000L)
        assertTrue(jsonString.contains("\"total_readings\": 0"))
        assertTrue(jsonString.contains("\"readings\": []"))
    }
}
