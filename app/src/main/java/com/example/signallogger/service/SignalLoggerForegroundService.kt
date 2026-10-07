package com.example.signallogger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.signallogger.data.SignalMetricDto
import com.example.signallogger.network.SignalApiClient
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
    private val apiClient = SignalApiClient()

    override fun onCreate() {
        super.onCreate()
        telephonySignalManager = TelephonySignalManager(this)
        telephonySignalManager.startListening()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverUrl = intent?.getStringExtra(EXTRA_SERVER_URL) ?: "http://10.0.2.2:8080"
        apiClient.baseUrl = serverUrl

        val notification = createNotification("Signal Logging Active", "Server: $serverUrl")
        startForeground(NOTIFICATION_ID, notification)

        startTelemetryLoggingLoop()

        return START_STICKY
    }

    private fun startTelemetryLoggingLoop() {
        serviceScope.launch {
            while (isActive) {
                val metric = telephonySignalManager.fetchCurrentMetricNow()
                val result = apiClient.postSignalMetric(metric)
                if (result.isSuccess) {
                    val saved = result.getOrNull()
                    saved?.let {
                        _latestLoggedMetric.value = it
                    }
                }
                delay(5000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        telephonySignalManager.stopListening()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Signal Logger Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
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
        const val EXTRA_SERVER_URL = "extra_server_url"

        private val _latestLoggedMetric = MutableStateFlow<SignalMetricDto?>(null)
        val latestLoggedMetric: StateFlow<SignalMetricDto?> = _latestLoggedMetric.asStateFlow()
    }
}
