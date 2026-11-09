package com.example.signallogger.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalSnapshotTest {
    @Test fun sharedSnapshotContainsSignalButNoLocationFields() {
        val text = signalSnapshot("Carrier", "LTE", -91, 5)
        assertTrue(text.contains("Carrier: Carrier"))
        assertTrue(text.contains("Network: LTE"))
        assertTrue(text.contains("Signal: 4/4 bars (-91 dBm)"))
        assertFalse(text.contains("latitude"))
        assertFalse(text.contains("longitude"))
        assertFalse(text.contains("areaCell"))
    }
}
