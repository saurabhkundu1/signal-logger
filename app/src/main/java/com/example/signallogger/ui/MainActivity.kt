package com.example.signallogger.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.signallogger.data.SignalMetricDto
import com.example.signallogger.data.SignalAreaSummaryDto
import com.example.signallogger.network.SignalApiClient
import com.example.signallogger.service.SignalLoggerForegroundService
import com.example.signallogger.telephony.TelephonySignalManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var telephonySignalManager: TelephonySignalManager
    private val permissionState = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_PHONE_STATE] == true &&
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        permissionState.value = granted
        if (granted) {
            telephonySignalManager.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        telephonySignalManager = TelephonySignalManager(this)

        setContent {
            SignalLoggerFrontEndApp(
                telephonySignalManager = telephonySignalManager,
                hasPermissions = permissionState.value,
                onRequestPermissions = { checkAndRequestPermissions() }
            )
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        permissionState.value = allGranted
        if (allGranted) {
            telephonySignalManager.startListening()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        telephonySignalManager.stopListening()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalLoggerFrontEndApp(
    telephonySignalManager: TelephonySignalManager,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentMetric by telephonySignalManager.currentMetric.collectAsState()
    val latestLoggedMetric by SignalLoggerForegroundService.latestLoggedMetric.collectAsState()

    val preferences = remember { context.getSharedPreferences("signal_atlas_settings", 0) }
    var serverUrl by remember { mutableStateOf(preferences.getString("server_url", "").orEmpty()) }
    var isServiceRunning by remember { mutableStateOf(false) }
    var metricsHistory by remember { mutableStateOf<List<SignalAreaSummaryDto>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("Ready") }

    val apiClient = remember { SignalApiClient() }
    apiClient.baseUrl = serverUrl

    fun refreshHistory() {
        if (!SignalApiClient.isValidServerUrl(serverUrl)) {
            statusMessage = "Enter a hosted HTTPS server URL to view shared area reports"
            return
        }
        scope.launch {
            val result = apiClient.fetchMetricsHistory()
            if (result.isSuccess) {
                metricsHistory = result.getOrDefault(emptyList())
                statusMessage = "Refreshed ${metricsHistory.size} area summaries"
            } else {
                statusMessage = "Failed to connect: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    LaunchedEffect(latestLoggedMetric) {
        latestLoggedMetric?.let {
            refreshHistory()
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFFF9800))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Signal Atlas", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main Signal Visual Meter Component
                SignalStrengthGaugeCard(
                    metric = currentMetric,
                    hasPermissions = hasPermissions,
                    onRequestPermissions = onRequestPermissions
                )

                // System Settings Data Grid
                TelephonySettingsGrid(metric = currentMetric)

                // Backend REST Service Sync Component
                BackendSyncCard(
                    serverUrl = serverUrl,
                    onServerUrlChange = {
                        serverUrl = it
                        preferences.edit().putString("server_url", it.trim()).apply()
                    },
                    isServiceRunning = isServiceRunning,
                    statusMessage = statusMessage,
                    onToggleService = {
                        val intent = Intent(context, SignalLoggerForegroundService::class.java).apply {
                            putExtra(SignalLoggerForegroundService.EXTRA_SERVER_URL, serverUrl)
                        }
                        if (!isServiceRunning && !hasPermissions) {
                            onRequestPermissions()
                            statusMessage = "Allow phone state and approximate location to start logging"
                        } else if (!isServiceRunning && !SignalApiClient.isValidServerUrl(serverUrl)) {
                            statusMessage = "Enter a hosted HTTPS server URL before starting shared logging"
                        } else if (!isServiceRunning) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            isServiceRunning = true
                            statusMessage = "Logging coarse area and signal readings to shared server"
                        } else {
                            context.stopService(intent)
                            isServiceRunning = false
                            statusMessage = "Foreground Logging Stopped"
                        }
                    },
                    onRefresh = { refreshHistory() }
                )

                // Backend Telemetry Log Stream
                Text(
                    text = "Shared Signal by Area and Operator",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(metricsHistory) { item ->
                        MetricHistoryCard(item)
                    }
                }
            }
        }
    }
}

@Composable
fun SignalStrengthGaugeCard(
    metric: SignalMetricDto,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit
) {
    val dbm = metric.signalStrengthDbm
    val (qualityLabel, gaugeColor) = when {
        dbm >= -85 -> "Excellent Signal" to Color(0xFF2E7D32)
        dbm >= -100 -> "Moderate Signal" to Color(0xFFF57F17)
        else -> "Weak Signal" to Color(0xFFC62828)
    }

    // Normalized progress value for -120 dBm (0%) to -50 dBm (100%)
    val progress = ((dbm + 120).coerceIn(0, 70)) / 70f

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = metric.operatorName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${metric.networkType} Network • ${metric.countryIso}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Visual Signal Bar Graphic (0 to 4 bars)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (barIndex in 1..4) {
                        val barHeight = (barIndex * 8).dp
                        val isActive = barIndex <= metric.signalLevel
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isActive) gaugeColor else Color.LightGray)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$dbm dBm",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = gaugeColor
                )
                Text(
                    text = qualityLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = gaugeColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = gaugeColor,
                trackColor = Color.LightGray.copy(alpha = 0.5f)
            )

            if (!hasPermissions) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Required Telephony & Location Permissions")
                }
            }
        }
    }
}

@Composable
fun TelephonySettingsGrid(metric: SignalMetricDto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "System Settings Telephony Details",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoTile(
                title = "SIM Carrier",
                value = metric.simOperatorName,
                modifier = Modifier.weight(1f)
            )
            InfoTile(
                title = "Data Connection",
                value = metric.dataState,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoTile(
                title = "SIM Card Status",
                value = metric.simState,
                modifier = Modifier.weight(1f)
            )
            InfoTile(
                title = "Roaming State",
                value = if (metric.isRoaming) "Roaming Active" else "Home Network",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun InfoTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun BackendSyncCard(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    isServiceRunning: Boolean,
    statusMessage: String,
    onToggleService: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Crowdsourced signal reports",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text("HTTPS server URL") },
                placeholder = { Text("https://your-signal-server.example") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Logging stays off until you start it. Shared reports include your operator, signal strength, and an approximate 5 km area cell. Exact coordinates and account details are not uploaded. Anyone using this server can view its area summaries.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onToggleService,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isServiceRunning) "Stop Service" else "Start Service")
                }

                Button(onClick = onRefresh) {
                    Text("Fetch Logs")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Status: $statusMessage",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricHistoryCard(metric: SignalAreaSummaryDto) {
    val dateFormat = remember { SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()) }
    val formattedTime = remember(metric.lastSeen) { dateFormat.format(Date(metric.lastSeen)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = metric.operatorName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${metric.networkType} • area ${metric.areaCell} • ${metric.sampleCount} samples",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${metric.averageSignalDbm.toInt()} dBm avg",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Last: $formattedTime",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
