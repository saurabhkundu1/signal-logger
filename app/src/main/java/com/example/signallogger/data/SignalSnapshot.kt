package com.example.signallogger.data

fun signalSnapshot(carrier: String, network: String, dbm: Int, bars: Int): String =
    "Signal Atlas reading\nCarrier: $carrier\nNetwork: $network" +
        "\nSignal: ${bars.coerceIn(0, 4)}/4 bars ($dbm dBm)" +
        "\nLocation and saved area names are not included."
