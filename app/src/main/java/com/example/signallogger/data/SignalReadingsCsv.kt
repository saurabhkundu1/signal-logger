package com.example.signallogger.data

internal fun signalReadingsCsv(readings: List<SignalReading>): String = buildString {
    appendLine("timestamp,operator,network_type,signal_dbm")
    readings.forEach { reading ->
        val fields = listOf(
            reading.timestamp.toString(), csvText(reading.operatorName), csvText(reading.networkType),
            reading.signalStrengthDbm.toString(),
        )
        appendLine(fields.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
    }
}

private fun csvText(value: String) = if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
