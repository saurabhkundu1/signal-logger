package com.example.signallogger.repository

import com.example.signallogger.model.SignalMetric
import java.util.Collections
import java.util.UUID

class SignalRepository {
    private val metrics = Collections.synchronizedList(mutableListOf<SignalMetric>())

    fun addMetric(metric: SignalMetric): SignalMetric {
        val entry = metric.copy(
            id = metric.id ?: UUID.randomUUID().toString(),
            timestamp = if (metric.timestamp <= 0) System.currentTimeMillis() else metric.timestamp
        )
        metrics.add(entry)
        return entry
    }

    fun getAllMetrics(): List<SignalMetric> {
        synchronized(metrics) {
            return metrics.toList()
        }
    }

    fun getLatestMetric(): SignalMetric? {
        synchronized(metrics) {
            return metrics.lastOrNull()
        }
    }

    fun clearAll() {
        metrics.clear()
    }
}
