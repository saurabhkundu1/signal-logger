package com.example.signallogger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.signallogger.data.SignalMetricDto
import com.example.signallogger.data.LocalSignalStore
import com.example.signallogger.data.SignalReading
import com.example.signallogger.location.AreaCellProvider
import com.example.signallogger.telephony.TelephonySignalManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SignalLoggerForegroundService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var telephonySignalManager: TelephonySignalManager
    private lateinit var areaCellProvider: AreaCellProvider
    private lateinit var signalStore: LocalSignalStore
    private var loggingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        telephonySignalManager = TelephonySignalManager(this)
        telephonySignalManager.startListening()
        areaCellProvider = AreaCellProvider(this)
        areaCellProvider.start()
        signalStore = LocalSignalStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        val notification = createNotification("Signal Logging Active", "Saving readings on this device")
        startForeground(NOTIFICATION_ID, notification)

        loggingJob?.cancel()
        loggingJob = startTelemetryLoggingLoop()

        return START_STICKY
    }

    private fun startTelemetryLoggingLoop(): Job = serviceScope.launch {
        while (isActive) {
            val areaCell = areaCellProvider.currentAreaCell
            val center = areaCellProvider.currentMapCenter
            if (areaCell != null && center != null) {
                val metric = telephonySignalManager.fetchCurrentMetricNow().copy(areaCell = areaCell)
                signalStore.append(SignalReading(metric.operatorName, metric.networkType,
                    metric.signalStrengthDbm, areaCell, center.first, center.second, metric.timestamp))
                _latestLoggedMetric.value = metric
            }
            delay(60_000)
        }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
        telephonySignalManager.stopListening()
        areaCellProvider.stop()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Signal Logger Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "signal_logger_channel"
        const val NOTIFICATION_ID = 1001
        @Volatile var isRunning = false
            private set
        private val _latestLoggedMetric = MutableStateFlow<SignalMetricDto?>(null)
        val latestLoggedMetric: StateFlow<SignalMetricDto?> = _latestLoggedMetric.asStateFlow()
    }
}
