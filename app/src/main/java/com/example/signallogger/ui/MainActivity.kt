package com.example.signallogger.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat as AndroidDateFormat
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.signallogger.data.LocalSignalStore
import com.example.signallogger.data.NamedAreaSignalSummary
import com.example.signallogger.data.signalSnapshot
import com.example.signallogger.data.SignalReading
import com.example.signallogger.service.SignalLoggerForegroundService
import com.example.signallogger.telephony.TelephonySignalManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
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
    private val jsonExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(LocalSignalStore(this).toJson()) }
                ?: error("Could not open the selected file")
        }.onSuccess { Toast.makeText(this, "Signal history exported as JSON", Toast.LENGTH_SHORT).show() }
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
                    exportGeoJson = { geoJsonExportLauncher.launch("signal-atlas-readings.geojson") },
                    exportJson = { jsonExportLauncher.launch("signal-atlas-readings.json") }
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
private fun SignalAtlasScreen(manager: TelephonySignalManager, granted: Boolean, requestPermissions: () -> Unit, exportReadings: () -> Unit, exportGeoJson: () -> Unit, exportJson: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LocalSignalStore(context) }
    val metric by manager.currentMetric.collectAsState()
    val latest by SignalLoggerForegroundService.latestLoggedMetric.collectAsState()
    var readings by remember { mutableStateOf(store.read().asReversed()) }
    var areaLabels by remember { mutableStateOf(store.areaLabels()) }
    var editingArea by remember { mutableStateOf<String?>(null) }
    var areaLabelDraft by remember { mutableStateOf("") }
    var selectedOperator by remember { mutableStateOf("All") }
    val logging by SignalLoggerForegroundService.isRunningFlow.collectAsState()
    val namedAreaSummaries = remember(readings, areaLabels) { store.namedAreaSummaries() }
    LaunchedEffect(latest) { if (latest != null) readings = store.read().asReversed() }
    val operators = listOf("All") + readings.map { it.operatorName }.distinct()
    val filteredReadings = readings.filter { selectedOperator == "All" || it.operatorName == selectedOperator }

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
                    Text("History stays on-device. Share a location-free snapshot when you choose.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            item {
                SignalCard(metric.operatorName, metric.signalStrengthDbm, metric.networkType, granted, requestPermissions) {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            signalSnapshot(metric.operatorName, metric.networkType, metric.signalStrengthDbm, metric.signalLevel),
                        )
                    }
                    context.startActivity(Intent.createChooser(share, "Share signal reading"))
                }
            }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Coverage map", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Your saved readings are grouped into approximate 5 km areas. Opening the map sends the area center and your IP address to OpenStreetMap to load map tiles.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        if (readings.isNotEmpty()) OpenStreetMapView(readings.first())
                        else Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Text("Start logging to map a reading", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (readings.isNotEmpty()) {
                            Text("Name a familiar area", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(readings.map { it.areaCell }.distinct()) { area ->
                                    val label = areaLabels[area]
                                    FilterChip(
                                        selected = label != null,
                                        onClick = { editingArea = area; areaLabelDraft = label.orEmpty() },
                                        label = { Text(label ?: "Area $area") },
                                    )
                                }
                            }
                            Text("Area names stay on this device and are left out of exports.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Text("© OpenStreetMap contributors", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
            item { NamedAreaSummaryCard(namedAreaSummaries, areaLabels.isNotEmpty()) }
            item { NetworkAnalyticsCard(readings) }
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
                        OutlinedButton(onClick = exportJson, modifier = Modifier.fillMaxWidth()) { Text("Export all readings as JSON") }
                        Text("JSON includes structured schema with timestamps, carrier, network type, and signal dBm.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
            items(filteredReadings.take(100)) { ReadingCard(it, areaLabels[it.areaCell]) }
        }
    }

    editingArea?.let { area ->
        AlertDialog(
            onDismissRequest = { editingArea = null },
            title = { Text("Name this area") },
            text = {
                OutlinedTextField(
                    value = areaLabelDraft,
                    onValueChange = { areaLabelDraft = it.take(32) },
                    label = { Text("Place name") },
                    supportingText = { Text("Up to 32 characters. Leave blank to remove the name.") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.setAreaLabel(area, areaLabelDraft)
                    areaLabels = store.areaLabels()
                    editingArea = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingArea = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NamedAreaSummaryCard(summaries: List<NamedAreaSignalSummary>, hasNamedAreas: Boolean) {
    val context = LocalContext.current
    val dateFormat = remember(context) { AndroidDateFormat.getDateFormat(context) }
    val timeFormat = remember(context) { AndroidDateFormat.getTimeFormat(context) }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Past seven days by place", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text("Median signal · weakest areas first", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            if (summaries.isEmpty()) {
                Text(
                    if (hasNamedAreas) "No saved readings from named areas in the past seven days."
                    else "Name a familiar area to start comparing places.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            } else {
                summaries.forEach { summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(summary.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${summary.operatorName} · ${summary.readingCount} ${if (summary.readingCount == 1) "reading" else "readings"}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                                val latestReading = Date(summary.latestReadingAt)
                                Text(
                                    "Latest ${dateFormat.format(latestReading)} · ${timeFormat.format(latestReading)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )
                            }
                            Text("${summary.medianDbm} dBm", fontWeight = FontWeight.Bold)
                        }
                        NamedAreaSignalTrend(summary)
                    }
                }
            }
            Text(
                "Uses only saved readings. Logging gaps and the 2,000-reading history limit can shorten coverage.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun NamedAreaSignalTrend(summary: NamedAreaSignalSummary) {
    val barColor = MaterialTheme.colorScheme.primary
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofPattern("d/M", locale) }
    val spokenDates = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMMM", locale) }
    val values = summary.dailyTrend.mapNotNull { it.medianDbm }
    val min = values.minOrNull() ?: 0
    val max = values.maxOrNull() ?: 0
    val range = (max.toLong() - min.toLong()).coerceAtLeast(1L).toFloat()
    val accessibleSummary = remember(summary) {
        buildString {
            append("Daily median signal in dBm for ${summary.name} on ${summary.operatorName}. Taller bars mean stronger reception. ")
            append(summary.dailyTrend.joinToString(". ") { day ->
                val date = day.date.format(spokenDates)
                if (day.medianDbm == null) "$date: no readings"
                else "$date: ${day.medianDbm} dBm from ${day.readingCount} ${if (day.readingCount == 1) "reading" else "readings"}"
            })
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Daily median dBm · oldest to newest · — means no readings",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = accessibleSummary },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            summary.dailyTrend.forEach { day ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(25.dp), contentAlignment = Alignment.BottomCenter) {
                        day.medianDbm?.let { dbm ->
                            val barHeight = if (max == min) 14.dp else {
                                (4f + 21f * (dbm.toLong() - min.toLong()).toFloat() / range).dp
                            }
                            Box(
                                Modifier.width(8.dp).height(barHeight)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(barColor),
                            )
                        }
                    }
                    Text(day.date.format(dates), fontSize = 8.sp, maxLines = 1)
                    Text(day.medianDbm?.toString() ?: "—", fontSize = 9.sp, maxLines = 1)
                }
            }
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
private fun SignalCard(operator: String, dbm: Int, network: String, granted: Boolean, request: () -> Unit, shareReading: () -> Unit) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF123B49), Color(0xFF176E77), Color(0xFF248B8C)))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("LIVE READING", color = Color(0xFFB9DFDF), fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
            Text(if (granted) operator else "Permission needed", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (granted) "$dbm" else "—", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text(" dBm · ${if (granted) network else "cellular"}", color = Color(0xFFD2E8E8), modifier = Modifier.padding(bottom = 7.dp))
            }
            if (granted) {
                val (qualityLabel, qualityColor) = when {
                    dbm >= -85 -> "● Excellent Reception" to Color(0xFF34D399)
                    dbm >= -95 -> "● Good Reception" to Color(0xFF6EE7B7)
                    dbm >= -105 -> "● Fair Reception" to Color(0xFFFBBF24)
                    else -> "● Poor Reception" to Color(0xFFF87171)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x33000000)
                ) {
                    Text(
                        text = qualityLabel,
                        color = qualityColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            if (!granted) Text("Allow phone and approximate location access once. Android remembers the choice unless it is revoked or set to one-time access.", color = Color(0xFFD2E8E8), fontSize = 13.sp)
            if (granted) TextButton(onClick = shareReading, modifier = Modifier.align(Alignment.End)) {
                Text("Share reading", color = Color.White)
            }
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
private fun ReadingCard(reading: SignalReading, areaLabel: String?) {
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(reading.timestamp, locale) { SimpleDateFormat("d MMM · HH:mm", locale).format(Date(reading.timestamp)) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(reading.operatorName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text("${reading.networkType} · Approx. area ${areaLabel ?: reading.areaCell}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Text("${reading.signalStrengthDbm} dBm", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NetworkAnalyticsCard(readings: List<SignalReading>) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Network & signal analytics", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(
                "Aggregated statistics across your on-device history",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            if (readings.isEmpty()) {
                Text(
                    "Start logging to compute network signal metrics and technology distribution.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            } else {
                val dbmList = readings.map { it.signalStrengthDbm }
                val minDbm = dbmList.minOrNull()
                val maxDbm = dbmList.maxOrNull()
                val avgDbm = if (dbmList.isNotEmpty()) (dbmList.sum().toDouble() / dbmList.size).toInt() else null

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatPill("Strongest", if (maxDbm != null) "$maxDbm dBm" else "—", Color(0xFF10B981))
                    StatPill("Average", if (avgDbm != null) "$avgDbm dBm" else "—", MaterialTheme.colorScheme.primary)
                    StatPill("Weakest", if (minDbm != null) "$minDbm dBm" else "—", Color(0xFFEF4444))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                Text("Technology distribution", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                val totalCount = readings.size
                val lteCount = readings.count { it.networkType.contains("LTE", ignoreCase = true) || it.networkType.contains("4G", ignoreCase = true) }
                val nrCount = readings.count { it.networkType.contains("NR", ignoreCase = true) || it.networkType.contains("5G", ignoreCase = true) }
                val otherCount = (totalCount - lteCount - nrCount).coerceAtLeast(0)

                val ltePct = if (totalCount > 0) (lteCount * 100) / totalCount else 0
                val nrPct = if (totalCount > 0) (nrCount * 100) / totalCount else 0
                val otherPct = (100 - ltePct - nrPct).coerceAtLeast(0)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TechProgressBar("5G (NR)", nrPct, nrCount, Color(0xFF78D1D8))
                    TechProgressBar("4G (LTE)", ltePct, lteCount, MaterialTheme.colorScheme.primary)
                    if (otherCount > 0) {
                        TechProgressBar("3G / Other", otherPct, otherCount, Color(0xFFE1A36D))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun TechProgressBar(label: String, percentage: Int, count: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$label · $percentage%", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("$count samples", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { percentage / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

