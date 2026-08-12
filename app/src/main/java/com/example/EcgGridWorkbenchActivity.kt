package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class EcgGridWorkbenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) { GridWorkbenchScreen() }
            }
        }
    }
}

private data class GridImportedFile(
    val uri: Uri,
    val name: String,
    val mime: String,
    val sizeBytes: Long?,
    val pageCount: Int? = null,
    val durationMs: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridWorkbenchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var file by remember { mutableStateOf<GridImportedFile?>(null) }
    var result by remember { mutableStateOf<LocalWaveformDigitizer.LocalTraceResult?>(null) }
    var calibration by remember { mutableStateOf<AutoCalibrationDetector.DetectionResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Import a tracing/report. The app will locate the grid block before extracting the waveform.") }
    var error by remember { mutableStateOf<String?>(null) }
    var copyStatus by remember { mutableStateOf<String?>(null) }

    var modality by remember { mutableStateOf(RecordingModality.IMPLANTABLE_LOOP_RECORDER) }
    var lead by remember { mutableStateOf(LeadConfiguration.SINGLE_LEAD) }
    var bodyPosition by remember { mutableStateOf(BodyPosition.UNKNOWN) }
    var activity by remember { mutableStateOf(ActivityState.UNKNOWN) }
    var motion by remember { mutableStateOf(MotionLevel.UNKNOWN) }
    var symptoms by remember { mutableStateOf("") }
    var contextNote by remember { mutableStateOf("") }

    var pdfPage by remember { mutableStateOf("1") }
    var videoSecond by remember { mutableStateOf("0") }

    var showFallback by remember { mutableStateOf(false) }
    var traceDurationSec by remember { mutableStateOf("") }
    var verticalRangeMv by remember { mutableStateOf("") }

    var enableAcceptanceGate by remember { mutableStateOf(true) }
    var minimumRrMs by remember { mutableFloatStateOf(180f) }

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableFloatStateOf(0f) }
    var showSamples by remember { mutableStateOf(true) }
    var showCompleteCatalog by remember { mutableStateOf(false) }
    var minimumVisualCompatibility by remember { mutableFloatStateOf(0.20f) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = null
            result = null
            calibration = null
            copyStatus = null
            file = inspectGridFile(context, uri)
            status = "Loaded ${file?.name ?: "record"}. Choose the page/frame containing the tracing and analyze."
            busy = false
        }
    }

    val eventContext = remember(bodyPosition, activity, motion, symptoms, contextNote) {
        RecordingEventContext(
            bodyPosition = bodyPosition,
            activityState = activity,
            motionLevel = motion,
            symptoms = parseGridSymptoms(symptoms),
            symptomNarrative = symptoms,
            userNarrative = contextNote,
            eventTriggerSource = EventTriggerSource.IMPORTED_REPORT,
            contextSource = ValueProvenance.REPORTED
        )
    }

    val gatePolicy = remember(enableAcceptanceGate, minimumRrMs) {
        PhysiologicBeatAcceptanceGate.Policy(
            minimumAcceptedRrMs = if (enableAcceptanceGate) minimumRrMs.toDouble() else 0.0
        )
    }

    val visualCandidates = remember(result, modality) {
        result?.let { RhythmVisualDifferential.build(it, modality) }.orEmpty()
    }
    val shownCandidates = remember(visualCandidates, showCompleteCatalog, minimumVisualCompatibility) {
        if (showCompleteCatalog) visualCandidates
        else visualCandidates.filter { it.metricCompatibility >= minimumVisualCompatibility }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ECG / IEGM Grid Workbench", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Grid-first strip detection • physiologic beat gate • copyable interpretation • visual differential", fontSize = 11.5.sp)
            }
        }

        item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Import evidence", fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        picker.launch(arrayOf("image/*", "application/pdf", "video/*", "text/plain", "text/csv", "application/json", "application/octet-stream"))
                    }) { Text("Choose ECG / ILR / Holter / interrogation record") }
                    file?.let { f ->
                        Text(f.name, fontWeight = FontWeight.SemiBold)
                        Text("${f.mime} • ${gridFormatBytes(f.sizeBytes)}", fontSize = 10.5.sp)
                        f.pageCount?.let { Text("PDF pages: $it", fontSize = 10.5.sp) }
                        f.durationMs?.let { Text("Video duration: ${"%.1f".format(Locale.US, it / 1000.0)} s", fontSize = 10.5.sp) }
                        if (f.mime.startsWith("image/")) {
                            AsyncImage(f.uri, "Imported tracing", Modifier.fillMaxWidth().heightIn(max = 240.dp))
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Recording context", fontWeight = FontWeight.Bold)
                    GridEnumSelector("Modality", modality, RecordingModality.entries) { modality = it }
                    GridEnumSelector("Lead configuration", lead, LeadConfiguration.entries) { lead = it }
                    GridEnumSelector("Body position", bodyPosition, BodyPosition.entries) { bodyPosition = it }
                    GridEnumSelector("Activity", activity, ActivityState.entries) { activity = it }
                    GridEnumSelector("Motion", motion, MotionLevel.entries) { motion = it }
                    OutlinedTextField(symptoms, { symptoms = it }, label = { Text("Symptoms at event") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(contextNote, { contextNote = it }, label = { Text("What was happening? lying still, asleep, walking, position change…") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

                    file?.pageCount?.let { pages ->
                        OutlinedTextField(pdfPage, { pdfPage = it.filter(Char::isDigit) }, label = { Text("PDF page containing tracing (1–$pages)") }, modifier = Modifier.fillMaxWidth())
                    }
                    file?.durationMs?.let {
                        OutlinedTextField(videoSecond, { videoSecond = numericGridInput(it) }, label = { Text("Video second containing tracing") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("3. Grid scanner & calibration", fontWeight = FontWeight.Bold)
                    Text(
                        "The scanner searches the whole page for a rectangular area with repeated horizontal/vertical grid lines plus waveform ink. Calibration text such as seconds, mV, mm/s, or mm/mV is supporting evidence, not the primary strip locator.",
                        fontSize = 10.5.sp
                    )

                    calibration?.let { c ->
                        Text("Selected plotting block: x ${c.traceRegionSourcePx.left}-${c.traceRegionSourcePx.right}, y ${c.traceRegionSourcePx.top}-${c.traceRegionSourcePx.bottom}", fontSize = 10.sp)
                        Text("Time calibration: ${c.timeReferenceSource}", fontSize = 10.sp)
                        Text("Amplitude calibration: ${c.amplitudeReferenceSource}", fontSize = 10.sp)
                        c.warnings.take(5).forEach { Text("• $it", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f)) }
                    }

                    TextButton(onClick = { showFallback = !showFallback }) {
                        Text(if (showFallback) "Hide manual fallback" else "Manual fallback if report scale cannot be read")
                    }
                    if (showFallback) {
                        Text("No pixel math required. Enter only what the report itself tells you.", fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp)
                        OutlinedTextField(
                            traceDurationSec,
                            { traceDurationSec = numericGridInput(it) },
                            label = { Text("Entire detected tracing block duration (seconds)") },
                            supportingText = { Text("Example: enter 30 if the full ILR strip represents 30 seconds.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            verticalRangeMv,
                            { verticalRangeMv = numericGridInput(it) },
                            label = { Text("Full vertical plotting range (mV), if printed") },
                            supportingText = { Text("Example: -1 to +1 mV is a 2 mV full range. Leave blank if the report gives no absolute mV scale.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(enabled = file != null && !busy, onClick = {
                        val chosen = file ?: return@Button
                        scope.launch {
                            busy = true
                            error = null
                            copyStatus = null
                            result = null
                            status = "Scanning for the grid block…"
                            try {
                                val imageUri = prepareGridImage(
                                    context,
                                    chosen,
                                    (pdfPage.toIntOrNull() ?: 1) - 1,
                                    ((videoSecond.toDoubleOrNull() ?: 0.0) * 1000.0).toLong()
                                ) ?: error("Selected input does not contain a renderable waveform.")

                                val detected = GridFirstCalibrationDetector.detect(context, imageUri)
                                calibration = detected
                                val region = detected.traceRegionSourcePx

                                val duration = traceDurationSec.toFloatOrNull()?.takeIf { it > 0f }
                                val verticalRange = verticalRangeMv.toFloatOrNull()?.takeIf { it > 0f }

                                val pxPerSecond = detected.pixelsPerSecond
                                    ?: duration?.let { region.width().toFloat() / it }
                                val pxPerMv = detected.pixelsPerMv
                                    ?: verticalRange?.let { region.height().toFloat() / it }

                                require(pxPerSecond != null && pxPerSecond > 0f) {
                                    "The grid block was found, but its time scale was not readable. Open Manual fallback and enter the total seconds represented by the entire tracing block."
                                }
                                require(pxPerMv != null && pxPerMv > 0f) {
                                    "The grid block was found, but no absolute mV scale was readable. If the report prints the full vertical mV range, enter it in Manual fallback."
                                }

                                val raw = LocalWaveformDigitizer.digitizeImage(
                                    context,
                                    imageUri,
                                    LocalWaveformDigitizer.DigitizerConfig(
                                        pixelsPerSecond = pxPerSecond,
                                        pixelsPerMv = pxPerMv,
                                        paperSpeedMmPerSec = detected.paperSpeedMmPerSec,
                                        gainMmPerMv = detected.gainMmPerMv,
                                        sourceCropRect = region,
                                        calibrationSource = if (detected.pixelsPerSecond != null && detected.pixelsPerMv != null) ValueProvenance.MEASURED else ValueProvenance.REPORTED,
                                        leadName = gridLeadName(lead)
                                    )
                                )

                                val gated = PhysiologicBeatAcceptanceGate.apply(raw, gatePolicy)
                                result = gated.trace
                                zoom = 1f
                                pan = 0f
                                status = "Grid block analyzed: ${gated.trace.waveformWindow.samples.size} samples • ${gated.trace.events.size} candidate deflections • ${gated.trace.intervals.size} RR intervals • ${gated.gatedCandidateCount} close candidates excluded from RR counting but retained for review."
                            } catch (t: Throwable) {
                                error = t.message ?: t.javaClass.simpleName
                                status = "Analysis stopped instead of guessing from unresolved strip/calibration data."
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text(if (busy) "Analyzing…" else "Scan grid & analyze") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(status, fontSize = 10.5.sp)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 10.5.sp) }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("4. Ventricular acceptance gate", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(enableAcceptanceGate, { enableAcceptanceGate = it })
                        Spacer(Modifier.width(8.dp))
                        Text(if (enableAcceptanceGate) "Enabled" else "Disabled")
                    }
                    if (enableAcceptanceGate) {
                        val maxRate = 60_000.0 / minimumRrMs
                        Text("Minimum RR counted as separate ventricular beats: ${minimumRrMs.toInt()} ms (~${"%.0f".format(Locale.US, maxRate)} bpm ceiling)", fontSize = 10.5.sp)
                        Slider(minimumRrMs, { minimumRrMs = it }, valueRange = 140f..240f, steps = 9)
                    }
                    Text("This does not blank the waveform. Close deflections remain visible and classifiable; the gate only controls which candidates contribute to ventricular RR/rate calculations.", fontSize = 9.8.sp)
                }
            }
        }

        result?.let { analyzed ->
            item {
                GridSignalInspector(analyzed, zoom, pan, { zoom = it }, { pan = it }, showSamples) { showSamples = !showSamples }
            }

            item {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("6. Interpretation export", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                val text = AnalysisTextExporter.build(
                                    result = analyzed,
                                    modality = modality,
                                    lead = lead,
                                    calibration = calibration,
                                    context = eventContext,
                                    visualCandidates = visualCandidates,
                                    gatePolicy = gatePolicy,
                                    sourceLabel = file?.name
                                )
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("ECG interpretation", text))
                                copyStatus = "Interpretation copied to clipboard."
                            }) { Text("Copy interpretation") }
                        }
                        Text("Copies calibration, context, RR milliseconds, rates, amplitudes, acceptance-gate settings, and the ranked rhythm differential as plain text.", fontSize = 10.2.sp)
                        copyStatus?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, fontSize = 10.2.sp) }
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("7. Two-layer visual rhythm differential", fontWeight = FontWeight.Bold)
                        Text("Metric compatibility and generated morphology remain separate checks.", fontSize = 10.5.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(showCompleteCatalog, { showCompleteCatalog = it })
                            Text("Show complete rhythm catalog")
                        }
                        if (!showCompleteCatalog) {
                            Text("Minimum metric compatibility: ${"%.0f".format(Locale.US, minimumVisualCompatibility * 100)}%", fontSize = 10.5.sp)
                            Slider(minimumVisualCompatibility, { minimumVisualCompatibility = it }, valueRange = 0f..1f, steps = 9)
                        }
                        Text("Showing ${shownCandidates.size} of ${visualCandidates.size} mechanisms", fontSize = 10.2.sp)
                    }
                }
            }

            items(shownCandidates, key = { it.mechanism.name }) { candidate ->
                GridRhythmCard(candidate)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> GridEnumSelector(label: String, value: T, values: List<T>, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) },
                    onClick = { onChange(item); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun GridSignalInspector(
    result: LocalWaveformDigitizer.LocalTraceResult,
    zoom: Float,
    pan: Float,
    onZoom: (Float) -> Unit,
    onPan: (Float) -> Unit,
    showSamples: Boolean,
    onToggleSamples: () -> Unit
) {
    val samples = result.waveformWindow.samples
    val visibleCount = max(24, (samples.size / zoom.coerceAtLeast(1f)).toInt())
    val maxStart = max(0, samples.size - visibleCount)
    val start = (pan.coerceIn(0f, 1f) * maxStart).toInt().coerceIn(0, maxStart)
    val visible = samples.subList(start, min(samples.size, start + visibleCount))
    val rr = result.intervals.filter {
        it.toRPeakTimeMs >= (visible.firstOrNull()?.timeMs ?: 0.0) &&
            it.fromRPeakTimeMs <= (visible.lastOrNull()?.timeMs ?: Double.MAX_VALUE)
    }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("5. Signal inspector", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onToggleSamples) { Text(if (showSamples) "Hide numbers" else "Show numbers") }
            }
            Text("Visible ${gridFmtMs(visible.firstOrNull()?.timeMs)} → ${gridFmtMs(visible.lastOrNull()?.timeMs)} • zoom ${"%.1f".format(Locale.US, zoom)}×", fontSize = 10.5.sp)
            Slider(zoom, onZoom, valueRange = 1f..32f)
            Slider(pan, onPan, valueRange = 0f..1f)
            GridPatientWaveform(visible)
            Text("Beat-to-beat milliseconds", fontWeight = FontWeight.SemiBold)
            if (rr.isEmpty()) Text("No accepted RR pair in this view. Rejected/ambiguous deflections remain present in the underlying event stream.", fontSize = 10.2.sp)
            rr.take(50).forEach { interval ->
                Text(
                    "R${interval.fromBeatIndex + 1}→R${interval.toBeatIndex + 1}  ${"%.3f".format(Locale.US, interval.rrMs)} ms  ${interval.instantaneousRateBpm?.let { "%.2f bpm".format(Locale.US, it) } ?: ""}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.8.sp
                )
            }
            if (showSamples) {
                HorizontalDivider()
                Text("index      time(ms)       amplitude(mV)  quality artifact", fontFamily = FontFamily.Monospace, fontSize = 9.2.sp)
                val step = max(1, visible.size / 150)
                visible.filterIndexed { i, _ -> i % step == 0 }.take(170).forEach { s ->
                    Text(
                        "${s.sampleIndex.toString().padEnd(10)} ${"%.3f".format(Locale.US, s.timeMs).padStart(11)} ${"%+.5f".format(Locale.US, s.amplitudeMv).padStart(14)}  ${"%.2f".format(Locale.US, s.signalQuality ?: 0f)}    ${if (s.artifactFlag) "yes" else "no"}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.8.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GridPatientWaveform(samples: List<WaveformSample>) {
    Canvas(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF07131C), RoundedCornerShape(8.dp))) {
        if (samples.size < 2) return@Canvas
        repeat(11) { i ->
            val x = size.width * i / 10f
            drawLine(Color(0x2238BDF8), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        repeat(9) { i ->
            val y = size.height * i / 8f
            drawLine(Color(0x2238BDF8), Offset(0f, y), Offset(size.width, y), 1f)
        }
        val minV = samples.minOf { it.amplitudeMv }
        val maxV = samples.maxOf { it.amplitudeMv }
        val span = (maxV - minV).takeIf { abs(it) > 1e-9 } ?: 1.0
        val path = Path()
        samples.forEachIndexed { index, s ->
            val x = index.toFloat() / samples.lastIndex * size.width
            val y = ((maxV - s.amplitudeMv) / span * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF2DD4BF), style = Stroke(2.2f))
    }
}

@Composable
private fun GridRhythmCard(candidate: RhythmVisualCandidate) {
    Card {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(candidate.displayName, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text("${"%.0f".format(Locale.US, candidate.metricCompatibility * 100)}% metric") })
            }
            Text(candidate.metricSummary, fontSize = 10.2.sp)
            Text("Generated comparison morphology", fontWeight = FontWeight.SemiBold, fontSize = 10.2.sp)
            GridGeneratedWaveform(candidate.waveform)
            Text(candidate.visualCaveat, fontSize = 9.3.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f))
            Text("Compare the generated morphology to the extracted patient strip separately from the numeric compatibility score.", fontSize = 9.3.sp)
        }
    }
}

@Composable
private fun GridGeneratedWaveform(points: List<EcgWavePoint>) {
    Canvas(Modifier.fillMaxWidth().height(125.dp).background(Color(0xFF08131F), RoundedCornerShape(8.dp))) {
        if (points.size < 2) return@Canvas
        val minV = points.minOf { it.value }
        val maxV = points.maxOf { it.value }
        val span = (maxV - minV).takeIf { abs(it) > 1e-6f } ?: 1f
        val path = Path()
        val stride = max(1, points.size / 420)
        var plotted = 0
        for (index in points.indices step stride) {
            val pt = points[index]
            val x = index.toFloat() / points.lastIndex * size.width
            val y = (maxV - pt.value) / span * size.height
            if (plotted++ == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF60A5FA), style = Stroke(2f))
    }
}

private suspend fun inspectGridFile(context: Context, uri: Uri): GridImportedFile = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    var name = uri.lastPathSegment ?: "imported-record"
    var size: Long? = null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    val pages = if (mime == "application/pdf") {
        resolver.openFileDescriptor(uri, "r")?.use { pfd -> PdfRenderer(pfd).use { it.pageCount } }
    } else null
    val duration = if (mime.startsWith("video/")) {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }
    } else null
    GridImportedFile(uri, name, mime, size, pages, duration)
}

private suspend fun prepareGridImage(context: Context, file: GridImportedFile, pdfPage: Int, videoTimeMs: Long): Uri? = withContext(Dispatchers.IO) {
    when {
        file.mime.startsWith("image/") -> file.uri
        file.mime == "application/pdf" -> renderGridPdf(context, file.uri, pdfPage)
        file.mime.startsWith("video/") -> renderGridFrame(context, file.uri, videoTimeMs)
        else -> null
    }
}

private fun renderGridPdf(context: Context, uri: Uri, pageIndex: Int): Uri {
    val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Unable to open PDF")
    pfd.use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val safe = pageIndex.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safe).use { page ->
                val targetWidth = min(3000, max(1500, page.width * 2))
                val scale = targetWidth.toFloat() / page.width.toFloat()
                val bitmap = Bitmap.createBitmap(targetWidth, max(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return saveGridBitmap(context, bitmap, "grid-pdf-${safe + 1}.png")
            }
        }
    }
}

private fun renderGridFrame(context: Context, uri: Uri, timeMs: Long): Uri = MediaMetadataRetriever().use { retriever ->
    retriever.setDataSource(context, uri)
    val bitmap = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
        ?: error("Unable to extract video frame")
    saveGridBitmap(context, bitmap, "grid-video-$timeMs.png")
}

private fun saveGridBitmap(context: Context, bitmap: Bitmap, name: String): Uri {
    val dir = File(context.cacheDir, "ecg-grid-import").apply { mkdirs() }
    val out = File(dir, name)
    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return Uri.fromFile(out)
}

private fun gridLeadName(lead: LeadConfiguration): String = when (lead) {
    LeadConfiguration.TWELVE_LEAD -> "Selected 12-lead tracing"
    LeadConfiguration.DEVICE_SPECIFIC_VECTOR -> "Device-specific subcutaneous vector"
    LeadConfiguration.SINGLE_LEAD -> "Single lead"
    else -> lead.name.replace('_', ' ')
}

private fun parseGridSymptoms(text: String): List<SymptomType> {
    if (text.isBlank()) return emptyList()
    val s = text.lowercase()
    val out = mutableListOf<SymptomType>()
    if ("palp" in s) out += SymptomType.PALPITATIONS
    if ("presyn" in s || "near faint" in s) out += SymptomType.PRESYNCOPE
    if ("syncope" in s || "passed out" in s || "faint" in s) out += SymptomType.SYNCOPE
    if ("dizz" in s) out += SymptomType.DIZZINESS
    if ("weak" in s) out += SymptomType.WEAKNESS
    if ("dysp" in s || "shortness" in s) out += SymptomType.DYSPNEA
    if ("chest" in s) out += SymptomType.CHEST_PAIN
    if ("vision" in s) out += SymptomType.VISION_CHANGE
    if (out.isEmpty()) out += SymptomType.OTHER
    return out
}

private fun numericGridInput(value: String): String = value.filter { it.isDigit() || it == '.' }.take(12)
private fun gridFmtMs(value: Double?): String = value?.let { "%.3f ms".format(Locale.US, it) } ?: "n/a"
private fun gridFormatBytes(value: Long?): String = when {
    value == null -> "size unknown"
    value < 1024 -> "$value B"
    value < 1024 * 1024 -> "%.1f KB".format(Locale.US, value / 1024.0)
    else -> "%.1f MB".format(Locale.US, value / (1024.0 * 1024.0))
}
