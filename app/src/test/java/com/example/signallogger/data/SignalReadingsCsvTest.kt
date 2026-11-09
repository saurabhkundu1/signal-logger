package com.example.signallogger.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalReadingsCsvTest {
    @Test fun exportsOnlyDocumentedFieldsAndEscapesSpreadsheetFormulas() {
        val reading = SignalReading("=WEBSERVICE(\"x\")", "LTE", -91, "abcde", 12.3, 45.6, 123L)
        val csv = signalReadingsCsv(listOf(reading))
        assertTrue(csv.startsWith("timestamp,operator,network_type,signal_dbm"))
        assertTrue(csv.contains("'=WEBSERVICE(\"\"x\"\")"))
        assertTrue(csv.contains("LTE"))
        assertFalse(csv.contains("abcde"))
        assertFalse(csv.contains("12.3"))
        assertFalse(csv.contains("45.6"))
        assertFalse(csv.contains("latitude"))
        assertFalse(csv.contains("longitude"))
    }
}
