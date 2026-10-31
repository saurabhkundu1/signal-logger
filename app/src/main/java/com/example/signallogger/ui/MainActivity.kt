package com.example.signallogger.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.signallogger.data.LocalSignalStore
import com.example.signallogger.data.SignalReading
import com.example.signallogger.service.SignalLoggerForegroundService
import com.example.signallogger.telephony.TelephonySignalManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LightColors = lightColorScheme(primary = Color(0xFF126875), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F0F2), onPrimaryContainer = Color(0xFF123B42),
    secondary = Color(0xFFE1A36D), background = Color(0xFFF3F7F8), onBackground = Color(0xFF172D33),
    surface = Color.White, onSurface = Color(0xFF172D33), surfaceVariant = Color(0xFFE7EFF0),
    onSurfaceVariant = Color(0xFF5A7076))
private val DarkColors = darkColorScheme(primary = Color(0xFF78D1D8), onPrimary = Color(0xFF073A40),
    primaryContainer = Color(0xFF16464D), onPrimaryContainer = Color(0xFFD6F5F6),
    secondary = Color(0xFFF0B47F), background = Color(0xFF101B20), onBackground = Color(0xFFE5F0F1),
    surface = Color(0xFF17262C), onSurface = Color(0xFFE5F0F1), surfaceVariant = Color(0xFF22363D),
    onSurfaceVariant = Color(0xFFB1C5C8))

class MainActivity : ComponentActivity() {
    private lateinit var signalManager: TelephonySignalManager
    private val hasPermissions = mutableStateOf(false)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermissions.value = hasRequiredPermissions()
        if (hasPermissions.value) signalManager.startListening()
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(LocalSignalStore(this).toCsv()) }
                ?: error("Could not open the selected file")
        }.onSuccess { Toast.makeText(this, "Signal history exported", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "Export failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
    }
    private val geoJsonExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/geo+json")) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(LocalSignalStore(this).toGeoJson()) }
                ?: error("Could not open the selected file")
        }.onSuccess { Toast.makeText(this, "Signal map exported", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "Export failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signalManager = TelephonySignalManager(this)
        hasPermissions.value = hasRequiredPermissions()
        if (hasPermissions.value) signalManager.startListening()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
                SignalAtlasScreen(
                    signalManager,
                    hasPermissions.value,
                    ::requestMissingPermissions,
                    exportReadings = { exportLauncher.launch("signal-atlas-readings.csv") },
                    exportGeoJson = { geoJsonExportLauncher.launch("signal-atlas-readings.geojson") }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::signalManager.isInitialized) {
            hasPermissions.value = hasRequiredPermissions()
            if (hasPermissions.value) signalManager.startListening() else signalManager.stopListening()
        }
    }

    override fun onDestroy() {
        if (::signalManager.isInitialized) signalManager.stopListening()
        super.onDestroy()
    }

    private fun hasRequiredPermissions() = listOf(
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_COARSE_LOCATION
    ).all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun requestMissingPermissions() {
        val required = listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_COARSE_LOCATION)
        val missing = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toMutableList()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignalAtlasScreen(manager: TelephonySignalManager, granted: Boolean, requestPermissions: () -> Unit, exportReadings: () -> Unit, exportGeoJson: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LocalSignalStore(context) }
    val metric by manager.currentMetric.collectAsState()
    val latest by SignalLoggerForegroundService.latestLoggedMetric.collectAsState()
    var readings by remember { mutableStateOf(store.read().asReversed()) }
    var selectedOperator by remember { mutableStateOf("All") }
    var logging by remember { mutableStateOf(false) }
    LaunchedEffect(latest) { if (latest != null) readings = store.read().asReversed() }
    val operators = listOf("All") + readings.map { it.operatorName }.distinct()
    val filteredReadings = readings.filter { selectedOperator == "All" || it.operatorName == selectedOperator }
    LaunchedEffect(Unit) {
        while (true) {
            logging = SignalLoggerForegroundService.isRunning
            delay(1500)
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Column {
            Text("Signal Atlas", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("PRIVATE NETWORK FIELD NOTES", fontSize = 9.sp, letterSpacing = 1.3.sp)
        } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF123B49), titleContentColor = Color.White))
    }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp, 20.dp, 18.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("YOUR CONNECTION, IN CONTEXT", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                    Text("Know your signal.", color = MaterialTheme.colorScheme.onBackground, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text("Signal readings stay on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            item { SignalCard(metric.operatorName, metric.signalStrengthDbm, metric.networkType, granted, requestPermissions) }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Coverage map", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Your saved readings are grouped into approximate 5 km areas. Opening the map sends the area center and your IP address to OpenStreetMap to load map tiles.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        if (readings.isNotEmpty()) OpenStreetMapView(readings.first())
                        else Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Text("Start logging to map a reading", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("© OpenStreetMap contributors", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("On-device logging", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("Records signal strength and an approximate area once per minute. No account or reporting server is used.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Button(onClick = {
                            if (!granted) requestPermissions()
                            else {
                                val intent = Intent(context, SignalLoggerForegroundService::class.java)
                                if (logging) context.stopService(intent)
                                else context.startForegroundService(intent)
                                logging = !logging
                            }
                        }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                            Text(if (!granted) "Allow signal and location" else if (logging) "Stop saving readings" else "Start saving readings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Signal history", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("Recent readings · dBm", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            operators.forEach { operator ->
                                FilterChip(selected = selectedOperator == operator, onClick = { selectedOperator = operator }, label = { Text(operator) })
                            }
                        }
                        SignalHistoryChart(filteredReadings.take(60).asReversed())
                        Text("The chart shows up to 60 local readings. Lower (more negative) values mean weaker signal.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        OutlinedButton(onClick = exportReadings, modifier = Modifier.fillMaxWidth()) { Text("Export all readings as CSV") }
                        Text("CSV includes time, carrier, network type and signal strength; it excludes location data.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        OutlinedButton(onClick = exportGeoJson, modifier = Modifier.fillMaxWidth()) { Text("Export mapped readings as GeoJSON") }
                        Text("GeoJSON includes approximate area coordinates, carrier, network type and signal strength.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Text("SAVED ON THIS DEVICE", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                    Text("${readings.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            if (filteredReadings.isEmpty()) item { Text("No readings saved for this carrier yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(filteredReadings.take(100)) { ReadingCard(it) }
        }
    }
}

@Composable
private fun SignalHistoryChart(readings: List<SignalReading>) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        if (readings.size < 2) return@Canvas
        val values = readings.map { it.signalStrengthDbm.toFloat() }
        val min = (values.minOrNull() ?: -120f) - 5f
        val max = (values.maxOrNull() ?: -40f) + 5f
        val range = (max - min).coerceAtLeast(1f)
        val points = values.mapIndexed { index, value ->
            androidx.compose.ui.geometry.Offset(
                x = size.width * index / (values.size - 1),
                y = size.height * (1f - (value - min) / range)
            )
        }
        points.zipWithNext().forEach { (start, end) ->
            drawLine(lineColor, start, end, strokeWidth = 3.dp.toPx())
        }
        points.forEach { drawCircle(lineColor, radius = 3.dp.toPx(), center = it) }
    }
}

@Composable
private fun SignalCard(operator: String, dbm: Int, network: String, granted: Boolean, request: () -> Unit) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF123B49), Color(0xFF176E77), Color(0xFF248B8C)))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("LIVE READING", color = Color(0xFFB9DFDF), fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
            Text(if (granted) operator else "Permission needed", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (granted) "$dbm" else "—", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text(" dBm · ${if (granted) network else "cellular"}", color = Color(0xFFD2E8E8), modifier = Modifier.padding(bottom = 7.dp))
            }
            if (!granted) Text("Allow phone and approximate location access once. Android remembers the choice unless it is revoked or set to one-time access.", color = Color(0xFFD2E8E8), fontSize = 13.sp)
        }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun OpenStreetMapView(reading: SignalReading) {
    val url = remember(reading.latitude, reading.longitude) {
        val lat = reading.latitude; val lon = reading.longitude
        "https://www.openstreetmap.org/export/embed.html?bbox=${lon - 0.025}%2C${lat - 0.015}%2C${lon + 0.025}%2C${lat + 0.015}&layer=mapnik&marker=$lat%2C$lon"
    }
    AndroidView(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)), factory = { ctx ->
        WebView(ctx).apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    val host = uri.host.orEmpty()
                    return uri.scheme != "https" || (host != "openstreetmap.org" && !host.endsWith(".openstreetmap.org"))
                }
            }
            // OpenStreetMap's interactive embed needs JavaScript; the WebView exposes no JavaScript bridge.
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "${settings.userAgentString} SignalAtlas/1.0"
            loadUrl(url)
        }
    }, update = { if (it.url != url) it.loadUrl(url) })
}

@Composable
private fun ReadingCard(reading: SignalReading) {
    val date = remember(reading.timestamp) { SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(reading.timestamp)) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(reading.operatorName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text("${reading.networkType} · Approx. area ${reading.areaCell}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Text("${reading.signalStrengthDbm} dBm", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}
