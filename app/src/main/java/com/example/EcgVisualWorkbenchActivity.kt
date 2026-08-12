package com.example

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class EcgVisualWorkbenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) { VisualWorkbench() }
            }
        }
    }
}

private data class VisualImportedFile(
    val uri: Uri,
    val name: String,
    val mime: String,
    val sizeBytes: Long?,
    val pageCount: Int? = null,
    val durationMs: Long? = null
)

private data class VisualTimelineItem(
    val label: String,
    val whenMs: Long,
    val modality: RecordingModality,
    val context: RecordingEventContext,
    val calibration: AutoCalibrationDetector.DetectionResult,
    val result: LocalWaveformDigitizer.LocalTraceResult
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualWorkbench() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var file by remember { mutableStateOf<VisualImportedFile?>(null) }
    var result by remember { mutableStateOf<LocalWaveformDigitizer.LocalTraceResult?>(null) }
    var calibration by remember { mutableStateOf<AutoCalibrationDetector.DetectionResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Import an ECG/IEGM strip or report. Calibration will be read from the selected tracing.") }
    var error by remember { mutableStateOf<String?>(null) }

    var modality by remember { mutableStateOf(RecordingModality.IMPLANTABLE_LOOP_RECORDER) }
    var lead by remember { mutableStateOf(LeadConfiguration.SINGLE_LEAD) }
    var bodyPosition by remember { mutableStateOf(BodyPosition.UNKNOWN) }
    var activity by remember { mutableStateOf(ActivityState.UNKNOWN) }
    var motion by remember { mutableStateOf(MotionLevel.UNKNOWN) }
    var symptoms by remember { mutableStateOf("") }
    var contextNote by remember { mutableStateOf("") }

    var pdfPage by remember { mutableStateOf("1") }
    var videoSecond by remember { mutableStateOf("0") }
    var manualOverride by remember { mutableStateOf(false) }
    var manualPxSec by remember { mutableStateOf("") }
    var manualPxMv by remember { mutableStateOf("") }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableFloatStateOf(0f) }
    var showSamples by remember { mutableStateOf(true) }
    var showAllVisuals by remember { mutableStateOf(true) }
    var minVisualScore by remember { mutableFloatStateOf(0f) }
    val timeline = remember { mutableStateListOf<VisualTimelineItem>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            result = null
            calibration = null
            error = null
            file = inspectVisualFile(context, uri)
            status = "Loaded ${file?.name ?: "record"}. Choose the page/frame containing the strip, then analyze."
            busy = false
        }
    }

    val visualCandidates = remember(result, modality) {
        result?.let { RhythmVisualDifferential.build(it, modality) }.orEmpty()
    }
    val shownVisuals = remember(visualCandidates, showAllVisuals, minVisualScore) {
        if (showAllVisuals) visualCandidates.filter { it.metricCompatibility >= minVisualScore }
        else visualCandidates.filter { it.metricCompatibility >= max(.50f, minVisualScore) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ECG / IEGM Signal Workbench", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Automatic calibration • no fixed R/T blanking • complete visual rhythm differential", fontSize = 12.sp)
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
                        Text("${f.mime} • ${visualFormatBytes(f.sizeBytes)}", fontSize = 11.sp)
                        f.pageCount?.let { Text("PDF pages: $it", fontSize = 11.sp) }
                        f.durationMs?.let { Text("Video duration: ${"%.1f".format(it / 1000.0)} s", fontSize = 11.sp) }
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
                    Text("2. Recording & event context", fontWeight = FontWeight.Bold)
                    VisualEnumSelector("Modality", modality, RecordingModality.entries) { modality = it }
                    VisualEnumSelector("Lead configuration", lead, LeadConfiguration.entries) { lead = it }
                    VisualEnumSelector("Body position", bodyPosition, BodyPosition.entries) { bodyPosition = it }
                    VisualEnumSelector("Activity", activity, ActivityState.entries) { activity = it }
                    VisualEnumSelector("Motion", motion, MotionLevel.entries) { motion = it }
                    OutlinedTextField(symptoms, { symptoms = it }, label = { Text("Symptoms at event") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(contextNote, { contextNote = it }, label = { Text("What was happening? lying still, asleep, walking, position change…") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    file?.pageCount?.let { pages ->
                        OutlinedTextField(pdfPage, { pdfPage = it.filter(Char::isDigit) }, label = { Text("PDF page containing tracing (1–$pages)") }, modifier = Modifier.fillMaxWidth())
                    }
                    file?.durationMs?.let {
                        OutlinedTextField(videoSecond, { videoSecond = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Video second containing tracing") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("3. Calibration & tracing extraction", fontWeight = FontWeight.Bold)
                    Text("The app locates the strip inside the page and reads its printed calibration. Page margins/header text are not part of the time axis.", fontSize = 11.sp)
                    calibration?.let { c ->
                        Text("Detected strip: x ${c.traceRegionSourcePx.left}–${c.traceRegionSourcePx.right}, y ${c.traceRegionSourcePx.top}–${c.traceRegionSourcePx.bottom}", fontSize = 10.5.sp)
                        Text("Time: ${c.timeReferenceSource}", fontSize = 10.5.sp)
                        Text("Amplitude: ${c.amplitudeReferenceSource}", fontSize = 10.5.sp)
                        c.recognizedCalibrationText.forEach { Text("• $it", fontSize = 9.5.sp) }
                        c.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 10.sp) }
                    }
                    TextButton(onClick = { manualOverride = !manualOverride }) { Text(if (manualOverride) "Hide advanced override" else "Advanced manual calibration override") }
                    if (manualOverride) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(manualPxSec, { manualPxSec = visualNumeric(it) }, label = { Text("pixels / second") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(manualPxMv, { manualPxMv = visualNumeric(it) }, label = { Text("pixels / mV") }, modifier = Modifier.weight(1f))
                        }
                    }
                    Button(enabled = file != null && !busy, onClick = {
                        val chosen = file ?: return@Button
                        scope.launch {
                            busy = true
                            error = null
                            result = null
                            status = "Detecting strip and calibration…"
                            try {
                                val imageUri = prepareVisualImage(
                                    context,
                                    chosen,
                                    (pdfPage.toIntOrNull() ?: 1) - 1,
                                    ((videoSecond.toDoubleOrNull() ?: 0.0) * 1000).toLong()
                                ) ?: error("Selected input does not contain a renderable waveform.")

                                val detected = AutoCalibrationDetector.detect(context, imageUri)
                                calibration = detected
                                val pxSec = if (manualOverride) manualPxSec.toFloatOrNull() ?: detected.pixelsPerSecond else detected.pixelsPerSecond
                                val pxMv = if (manualOverride) manualPxMv.toFloatOrNull() ?: detected.pixelsPerMv else detected.pixelsPerMv
                                require(pxSec != null && pxSec > 0f) { "No reliable time reference was detected. Use Advanced override only if the strip truly has no printed time calibration." }
                                require(pxMv != null && pxMv > 0f) { "No reliable amplitude reference was detected. Use Advanced override only if the strip truly has no printed amplitude calibration." }

                                val analyzed = LocalWaveformDigitizer.digitizeImage(
                                    context,
                                    imageUri,
                                    LocalWaveformDigitizer.DigitizerConfig(
                                        pixelsPerSecond = pxSec,
                                        pixelsPerMv = pxMv,
                                        paperSpeedMmPerSec = detected.paperSpeedMmPerSec,
                                        gainMmPerMv = detected.gainMmPerMv,
                                        sourceCropRect = detected.traceRegionSourcePx,
                                        calibrationSource = if (manualOverride) ValueProvenance.REPORTED else ValueProvenance.MEASURED,
                                        leadName = visualLeadName(lead)
                                    )
                                )
                                result = analyzed
                                zoom = 1f
                                pan = 0f
                                val eventContext = RecordingEventContext(
                                    bodyPosition = bodyPosition,
                                    activityState = activity,
                                    motionLevel = motion,
                                    symptoms = visualParseSymptoms(symptoms),
                                    symptomNarrative = symptoms,
                                    userNarrative = contextNote,
                                    eventTriggerSource = EventTriggerSource.IMPORTED_REPORT,
                                    contextSource = ValueProvenance.REPORTED
                                )
                                timeline += VisualTimelineItem(
                                    chosen.name + chosen.pageCount?.let { " • p.$pdfPage" }.orEmpty(),
                                    System.currentTimeMillis(), modality, eventContext, detected, analyzed
                                )
                                status = "Analyzed ${analyzed.waveformWindow.samples.size} samples • ${analyzed.events.size} candidate deflections • ${analyzed.intervals.size} RR intervals."
                            } catch (t: Throwable) {
                                error = t.message ?: t.javaClass.simpleName
                                status = "Analysis stopped rather than guessing from unresolved calibration."
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text(if (busy) "Analyzing…" else "Auto-calibrate & analyze") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(status, fontSize = 10.5.sp)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 10.5.sp) }
                }
            }
        }

        result?.let { analyzed ->
            item { VisualSignalInspector(analyzed, zoom, pan, { zoom = it }, { pan = it }, showSamples) { showSamples = !showSamples } }

            item {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("5. Two-layer visual differential", fontWeight = FontWeight.Bold)
                        Text("Each rhythm is scored numerically and rendered separately. Visual resemblance and metric compatibility are intentionally kept independent.", fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(showAllVisuals, { showAllVisuals = it })
                            Text("Show the complete rhythm catalog")
                        }
                        Text("Minimum metric compatibility shown: ${"%.0f".format(minVisualScore * 100)}%", fontSize = 10.5.sp)
                        Slider(minVisualScore, { minVisualScore = it }, valueRange = 0f..1f, steps = 9)
                        Text("Showing ${shownVisuals.size} of ${visualCandidates.size} rhythm mechanisms", fontSize = 10.5.sp)
                    }
                }
            }

            items(shownVisuals, key = { it.mechanism.name }) { candidate ->
                VisualRhythmCard(candidate)
            }
        }

        if (timeline.isNotEmpty()) item { VisualLongitudinalTimeline(timeline) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> VisualEnumSelector(label: String, value: T, values: List<T>, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(
            value.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            values.forEach { item -> DropdownMenuItem({ Text(item.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) }, { onChange(item); expanded = false }) }
        }
    }
}

@Composable
private fun VisualSignalInspector(
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
    val visibleRr = result.intervals.filter { it.toRPeakTimeMs >= (visible.firstOrNull()?.timeMs ?: 0.0) && it.fromRPeakTimeMs <= (visible.lastOrNull()?.timeMs ?: Double.MAX_VALUE) }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("4. Signal inspector", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onToggleSamples) { Text(if (showSamples) "Hide numbers" else "Show numbers") }
            }
            Text("Visible ${visualFmtMs(visible.firstOrNull()?.timeMs)} → ${visualFmtMs(visible.lastOrNull()?.timeMs)} • zoom ${"%.1f".format(zoom)}×", fontSize = 10.5.sp)
            Slider(zoom, onZoom, valueRange = 1f..32f)
            Slider(pan, onPan, valueRange = 0f..1f)
            PatientWaveformCanvas(visible)
            Text("Beat-to-beat milliseconds — no blanking", fontWeight = FontWeight.SemiBold)
            if (visibleRr.isEmpty()) Text("No accepted ventricular pair in this view. All candidate deflections remain retained.", fontSize = 10.5.sp)
            visibleRr.take(40).forEach { rr ->
                Text("R${rr.fromBeatIndex + 1}→R${rr.toBeatIndex + 1}  ${"%.3f".format(rr.rrMs)} ms  ${rr.instantaneousRateBpm?.let { "%.2f bpm".format(it) } ?: ""}", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            if (showSamples) {
                HorizontalDivider()
                Text("index      time(ms)       amplitude(mV)  quality artifact", fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                val step = max(1, visible.size / 150)
                visible.filterIndexed { index, _ -> index % step == 0 }.take(170).forEach { s ->
                    Text("${s.sampleIndex.toString().padEnd(10)} ${"%.3f".format(s.timeMs).padStart(11)} ${"%+.5f".format(s.amplitudeMv).padStart(14)}  ${"%.2f".format(s.signalQuality ?: 0f)}    ${if (s.artifactFlag) "yes" else "no"}", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun PatientWaveformCanvas(samples: List<WaveformSample>) {
    Canvas(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF07131C), RoundedCornerShape(8.dp))) {
        if (samples.size < 2) return@Canvas
        repeat(11) { i -> val x = size.width * i / 10f; drawLine(Color(0x2238BDF8), Offset(x, 0f), Offset(x, size.height), 1f) }
        repeat(9) { i -> val y = size.height * i / 8f; drawLine(Color(0x2238BDF8), Offset(0f, y), Offset(size.width, y), 1f) }
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
private fun VisualRhythmCard(candidate: RhythmVisualCandidate) {
    Card {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(candidate.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text("${"%.0f".format(candidate.metricCompatibility * 100)}% metric") })
            }
            Text(candidate.metricSummary, fontSize = 10.5.sp)
            Text("Generated comparison morphology", fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp)
            GeneratedRhythmCanvas(candidate.waveform)
            Text(candidate.visualCaveat, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f))
            Text("Verification rule: compare this visual pattern with the extracted patient strip, then separately verify the measured intervals/morphology. A visual match alone is not treated as confirmation.", fontSize = 9.5.sp)
        }
    }
}

@Composable
private fun GeneratedRhythmCanvas(points: List<EcgWavePoint>) {
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

@Composable
private fun VisualLongitudinalTimeline(items: List<VisualTimelineItem>) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("6. Longitudinal change timeline", fontWeight = FontWeight.Bold)
            items.sortedBy { it.whenMs }.forEach { item ->
                val rr = item.result.intervals.map { it.rrMs }
                val rates = item.result.intervals.mapNotNull { it.instantaneousRateBpm }
                val samples = item.result.waveformWindow.samples
                Text("${visualDate(item.whenMs)} • ${item.label}", fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp)
                Text("${item.modality.name.replace('_', ' ')} • mean rate ${rates.takeIf { it.isNotEmpty() }?.average()?.let { "%.1f bpm".format(it) } ?: "n/a"} • RR ${if (rr.isNotEmpty()) "%.1f–%.1f ms".format(rr.min(), rr.max()) else "n/a"} • amp ${if (samples.isNotEmpty()) "%.3f…%.3f mV".format(samples.minOf { it.amplitudeMv }, samples.maxOf { it.amplitudeMv }) else "n/a"}", fontSize = 10.sp)
                Text("Calibration: ${item.calibration.timeReferenceSource}; ${item.calibration.amplitudeReferenceSource}", fontSize = 9.5.sp)
                HorizontalDivider()
            }
        }
    }
}

private suspend fun inspectVisualFile(context: Context, uri: Uri): VisualImportedFile = withContext(Dispatchers.IO) {
    val r = context.contentResolver
    val mime = r.getType(uri) ?: "application/octet-stream"
    var name = uri.lastPathSegment ?: "imported-record"
    var size: Long? = null
    r.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); val si = c.getColumnIndex(OpenableColumns.SIZE)
            if (ni >= 0) name = c.getString(ni) ?: name
            if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
        }
    }
    val pages = if (mime == "application/pdf") r.openFileDescriptor(uri, "r")?.use { pfd -> PdfRenderer(pfd).use { it.pageCount } } else null
    val duration = if (mime.startsWith("video/")) MediaMetadataRetriever().use { m -> m.setDataSource(context, uri); m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() } else null
    VisualImportedFile(uri, name, mime, size, pages, duration)
}

private suspend fun prepareVisualImage(context: Context, file: VisualImportedFile, pdfPage: Int, videoTimeMs: Long): Uri? = withContext(Dispatchers.IO) {
    when {
        file.mime.startsWith("image/") -> file.uri
        file.mime == "application/pdf" -> renderVisualPdf(context, file.uri, pdfPage)
        file.mime.startsWith("video/") -> renderVisualFrame(context, file.uri, videoTimeMs)
        else -> null
    }
}

private fun renderVisualPdf(context: Context, uri: Uri, pageIndex: Int): Uri {
    val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Unable to open PDF")
    pfd.use { descriptor -> PdfRenderer(descriptor).use { renderer ->
        val safe = pageIndex.coerceIn(0, renderer.pageCount - 1)
        renderer.openPage(safe).use { page ->
            val targetWidth = min(2800, max(1400, page.width * 2))
            val scale = targetWidth.toFloat() / page.width
            val bitmap = Bitmap.createBitmap(targetWidth, max(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return saveVisualBitmap(context, bitmap, "visual-pdf-${safe + 1}.png")
        }
    } }
}

private fun renderVisualFrame(context: Context, uri: Uri, timeMs: Long): Uri = MediaMetadataRetriever().use { m ->
    m.setDataSource(context, uri)
    val bitmap = m.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST) ?: error("Unable to extract video frame")
    saveVisualBitmap(context, bitmap, "visual-video-$timeMs.png")
}

private fun saveVisualBitmap(context: Context, bitmap: Bitmap, name: String): Uri {
    val dir = File(context.cacheDir, "ecg-visual-import").apply { mkdirs() }
    val out = File(dir, name)
    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return Uri.fromFile(out)
}

private fun visualLeadName(lead: LeadConfiguration) = when (lead) {
    LeadConfiguration.TWELVE_LEAD -> "Selected 12-lead tracing"
    LeadConfiguration.DEVICE_SPECIFIC_VECTOR -> "Device-specific subcutaneous vector"
    LeadConfiguration.SINGLE_LEAD -> "Single lead"
    else -> lead.name.replace('_', ' ')
}

private fun visualParseSymptoms(text: String): List<SymptomType> {
    if (text.isBlank()) return emptyList()
    val s = text.lowercase(); val out = mutableListOf<SymptomType>()
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

private fun visualNumeric(s: String) = s.filter { it.isDigit() || it == '.' }.take(12)
private fun visualFmtMs(v: Double?) = v?.let { "%.3f ms".format(it) } ?: "n/a"
private fun visualDate(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(t))
private fun visualFormatBytes(v: Long?) = when {
    v == null -> "size unknown"
    v < 1024 -> "$v B"
    v < 1024 * 1024 -> "%.1f KB".format(v / 1024.0)
    else -> "%.1f MB".format(v / (1024.0 * 1024.0))
}