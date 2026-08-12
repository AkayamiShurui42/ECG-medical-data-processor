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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

class EcgAutoWorkbenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) { AutoWorkbenchScreen() }
            }
        }
    }
}

private data class AutoImportedFile(
    val uri: Uri,
    val name: String,
    val mime: String,
    val sizeBytes: Long?,
    val pageCount: Int? = null,
    val durationMs: Long? = null
)

private data class AutoSessionAnalysis(
    val label: String,
    val timestamp: Long,
    val modality: RecordingModality,
    val context: RecordingEventContext,
    val calibration: AutoCalibrationDetector.DetectionResult,
    val result: LocalWaveformDigitizer.LocalTraceResult
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoWorkbenchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFile by remember { mutableStateOf<AutoImportedFile?>(null) }
    var result by remember { mutableStateOf<LocalWaveformDigitizer.LocalTraceResult?>(null) }
    var calibration by remember { mutableStateOf<AutoCalibrationDetector.DetectionResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Import a tracing or report. Calibration is detected automatically from the selected strip.") }
    var error by remember { mutableStateOf<String?>(null) }

    var modality by remember { mutableStateOf(RecordingModality.IMPLANTABLE_LOOP_RECORDER) }
    var lead by remember { mutableStateOf(LeadConfiguration.SINGLE_LEAD) }
    var position by remember { mutableStateOf(BodyPosition.UNKNOWN) }
    var activity by remember { mutableStateOf(ActivityState.UNKNOWN) }
    var motion by remember { mutableStateOf(MotionLevel.UNKNOWN) }
    var symptoms by remember { mutableStateOf("") }
    var narrative by remember { mutableStateOf("") }

    var pdfPageText by remember { mutableStateOf("1") }
    var videoSecondText by remember { mutableStateOf("0") }
    var useManualOverride by remember { mutableStateOf(false) }
    var manualPxSec by remember { mutableStateOf("") }
    var manualPxMv by remember { mutableStateOf("") }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableFloatStateOf(0f) }
    var showSamples by remember { mutableStateOf(true) }
    var showAllRhythms by remember { mutableStateOf(false) }
    val timeline = remember { mutableStateListOf<AutoSessionAnalysis>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                error = null
                result = null
                calibration = null
                selectedFile = inspectAutoFile(context, uri)
                status = "Loaded ${selectedFile?.name ?: "record"}. Select the correct page/frame and tap Auto-calibrate & analyze."
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ECG / IEGM Signal Workbench", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Automatic strip calibration • exact sample output • no fixed R/T blanking", fontSize = 12.sp)

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Import", fontWeight = FontWeight.Bold)
                Button(onClick = {
                    picker.launch(arrayOf("image/*", "application/pdf", "video/*", "text/plain", "text/csv", "application/json", "application/octet-stream"))
                }) { Text("Choose ECG / interrogation / Holter record") }

                selectedFile?.let { f ->
                    Text(f.name, fontWeight = FontWeight.SemiBold)
                    Text("${f.mime} • ${autoFormatBytes(f.sizeBytes)}", fontSize = 11.sp)
                    f.pageCount?.let { Text("PDF pages: $it", fontSize = 11.sp) }
                    f.durationMs?.let { Text("Video duration: ${"%.1f".format(it / 1000.0)} s", fontSize = 11.sp) }
                    if (f.mime.startsWith("image/")) {
                        AsyncImage(f.uri, "Imported tracing", Modifier.fillMaxWidth().heightIn(max = 250.dp))
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Recording context", fontWeight = FontWeight.Bold)
                AutoEnumSelector("Modality", modality, RecordingModality.entries) { modality = it }
                AutoEnumSelector("Lead configuration", lead, LeadConfiguration.entries) { lead = it }
                AutoEnumSelector("Body position", position, BodyPosition.entries) { position = it }
                AutoEnumSelector("Activity", activity, ActivityState.entries) { activity = it }
                AutoEnumSelector("Motion", motion, MotionLevel.entries) { motion = it }
                OutlinedTextField(symptoms, { symptoms = it }, label = { Text("Symptoms at event") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(narrative, { narrative = it }, label = { Text("What was happening? lying still, asleep, walking, position change…") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

                selectedFile?.pageCount?.let { pages ->
                    OutlinedTextField(
                        pdfPageText,
                        { pdfPageText = it.filter(Char::isDigit) },
                        label = { Text("PDF page containing the tracing (1–$pages)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                selectedFile?.durationMs?.let {
                    OutlinedTextField(
                        videoSecondText,
                        { videoSecondText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Video second containing the tracing") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Automatic calibration", fontWeight = FontWeight.Bold)
                Text(
                    "The app finds the tracing region first, then reads printed references such as 25 mm/s, 10 mm/mV, 1 s, or 0.5 mV. The report/page boundary is not used as the tracing time origin.",
                    fontSize = 11.sp
                )

                calibration?.let { c ->
                    Text("Trace region: x ${c.traceRegionSourcePx.left}–${c.traceRegionSourcePx.right}, y ${c.traceRegionSourcePx.top}–${c.traceRegionSourcePx.bottom} px", fontSize = 11.sp)
                    Text("Time: ${c.timeReferenceSource}", fontSize = 11.sp)
                    Text("Amplitude: ${c.amplitudeReferenceSource}", fontSize = 11.sp)
                    Text("Detected: ${c.pixelsPerSecond?.let { "%.2f px/s".format(it) } ?: "time unresolved"} • ${c.pixelsPerMv?.let { "%.2f px/mV".format(it) } ?: "amplitude unresolved"}", fontSize = 11.sp)
                    c.recognizedCalibrationText.forEach { Text("• $it", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f)) }
                    c.warnings.forEach { Text(it, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.error) }
                }

                TextButton(onClick = { useManualOverride = !useManualOverride }) {
                    Text(if (useManualOverride) "Hide advanced manual override" else "Advanced manual override")
                }
                if (useManualOverride) {
                    Text("Only use these if automatic calibration cannot find a printed reference.", fontSize = 10.5.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(manualPxSec, { manualPxSec = autoNumeric(it) }, label = { Text("pixels / second") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(manualPxMv, { manualPxMv = autoNumeric(it) }, label = { Text("pixels / mV") }, modifier = Modifier.weight(1f))
                    }
                }

                Button(
                    enabled = selectedFile != null && !busy,
                    onClick = {
                        val file = selectedFile ?: return@Button
                        scope.launch {
                            busy = true
                            error = null
                            result = null
                            status = "Finding tracing region and reading calibration reference…"
                            try {
                                val imageUri = prepareAutoImage(
                                    context,
                                    file,
                                    ((pdfPageText.toIntOrNull() ?: 1) - 1),
                                    ((videoSecondText.toDoubleOrNull() ?: 0.0) * 1000.0).toLong()
                                ) ?: error("This source contains report/metric evidence but no rendered waveform on the selected input.")

                                val detected = AutoCalibrationDetector.detect(context, imageUri)
                                calibration = detected

                                val pxSec = if (useManualOverride) manualPxSec.toFloatOrNull() ?: detected.pixelsPerSecond else detected.pixelsPerSecond
                                val pxMv = if (useManualOverride) manualPxMv.toFloatOrNull() ?: detected.pixelsPerMv else detected.pixelsPerMv

                                if (pxSec == null || pxSec <= 0f) {
                                    error("No reliable time calibration was detected on this strip. Open Advanced manual override only if the source truly lacks a printed time reference.")
                                }
                                if (pxMv == null || pxMv <= 0f) {
                                    error("No reliable amplitude calibration was detected on this strip. Open Advanced manual override only if the source truly lacks a printed amplitude reference.")
                                }

                                val analyzed = LocalWaveformDigitizer.digitizeImage(
                                    context,
                                    imageUri,
                                    LocalWaveformDigitizer.DigitizerConfig(
                                        pixelsPerSecond = pxSec,
                                        pixelsPerMv = pxMv,
                                        paperSpeedMmPerSec = detected.paperSpeedMmPerSec,
                                        gainMmPerMv = detected.gainMmPerMv,
                                        sourceCropRect = detected.traceRegionSourcePx,
                                        calibrationSource = if (useManualOverride) ValueProvenance.REPORTED else ValueProvenance.MEASURED,
                                        leadName = autoLeadName(lead)
                                    )
                                )
                                result = analyzed
                                zoom = 1f
                                pan = 0f

                                val eventContext = RecordingEventContext(
                                    bodyPosition = position,
                                    activityState = activity,
                                    motionLevel = motion,
                                    symptoms = autoParseSymptoms(symptoms),
                                    symptomNarrative = symptoms,
                                    userNarrative = narrative,
                                    eventTriggerSource = EventTriggerSource.IMPORTED_REPORT,
                                    contextSource = ValueProvenance.REPORTED
                                )
                                timeline += AutoSessionAnalysis(
                                    label = file.name + file.pageCount?.let { " • p.$pdfPageText" }.orEmpty(),
                                    timestamp = System.currentTimeMillis(),
                                    modality = modality,
                                    context = eventContext,
                                    calibration = detected,
                                    result = analyzed
                                )
                                status = "Analyzed ${analyzed.waveformWindow.samples.size} samples • ${analyzed.events.size} candidate deflections • ${analyzed.intervals.size} RR intervals."
                            } catch (t: Throwable) {
                                error = t.message ?: t.javaClass.simpleName
                                status = "Analysis stopped because calibration or waveform extraction was not reliable enough."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Analyzing…" else "Auto-calibrate & analyze") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(status, fontSize = 11.sp)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
            }
        }

        result?.let { r ->
            AutoSignalInspector(r, zoom, pan, { zoom = it }, { pan = it }, showSamples) { showSamples = !showSamples }
            AutoDifferentialPanel(r, modality, showAllRhythms) { showAllRhythms = !showAllRhythms }
        }

        if (timeline.isNotEmpty()) AutoLongitudinalPanel(timeline)
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> AutoEnumSelector(label: String, value: T, values: List<T>, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(
            value.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            values.forEach { item -> DropdownMenuItem({ Text(item.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) }, {
                onChange(item); expanded = false
            }) }
        }
    }
}

@Composable
private fun AutoSignalInspector(
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
    val end = min(samples.size, start + visibleCount)
    val visible = samples.subList(start, end)
    val rrVisible = result.intervals.filter { it.toRPeakTimeMs >= (visible.firstOrNull()?.timeMs ?: 0.0) && it.fromRPeakTimeMs <= (visible.lastOrNull()?.timeMs ?: Double.MAX_VALUE) }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("4. Signal inspector", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onToggleSamples) { Text(if (showSamples) "Hide numbers" else "Show numbers") }
            }
            Text("Visible: ${autoFmtMs(visible.firstOrNull()?.timeMs)} → ${autoFmtMs(visible.lastOrNull()?.timeMs)}", fontSize = 11.sp)
            Text("Zoom ${"%.1f".format(zoom)}×", fontSize = 11.sp)
            Slider(zoom, onZoom, valueRange = 1f..32f)
            Text("Pan", fontSize = 11.sp)
            Slider(pan, onPan, valueRange = 0f..1f)
            AutoWaveformCanvas(visible)

            Text("Beat-to-beat milliseconds — no blanking", fontWeight = FontWeight.SemiBold)
            if (rrVisible.isEmpty()) Text("No accepted ventricular pair in this view; candidate deflections are still retained.", fontSize = 11.sp)
            rrVisible.take(40).forEach { rr ->
                Text("R${rr.fromBeatIndex + 1} → R${rr.toBeatIndex + 1}: ${"%.3f".format(rr.rrMs)} ms • ${rr.instantaneousRateBpm?.let { "%.2f bpm".format(it) } ?: "rate n/a"}", fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
            }

            if (showSamples) {
                HorizontalDivider()
                Text("Exact visible waveform samples", fontWeight = FontWeight.SemiBold)
                Text("index     time(ms)       amplitude(mV)   quality   artifact", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                val step = max(1, visible.size / 160)
                visible.filterIndexed { index, _ -> index % step == 0 }.take(180).forEach { s ->
                    Text("${s.sampleIndex.toString().padEnd(9)} ${"%.3f".format(s.timeMs).padStart(11)} ${"%+.5f".format(s.amplitudeMv).padStart(15)}   ${"%.2f".format(s.signalQuality ?: 0f)}      ${if (s.artifactFlag) "yes" else "no"}", fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun AutoWaveformCanvas(samples: List<WaveformSample>) {
    Canvas(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF07131C), RoundedCornerShape(8.dp))) {
        if (samples.size < 2) return@Canvas
        for (i in 0..10) {
            val x = size.width * i / 10f
            drawLine(Color(0x2238BDF8), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        for (i in 0..8) {
            val y = size.height * i / 8f
            drawLine(Color(0x2238BDF8), Offset(0f, y), Offset(size.width, y), 1f)
        }
        val minV = samples.minOf { it.amplitudeMv }
        val maxV = samples.maxOf { it.amplitudeMv }
        val span = (maxV - minV).takeIf { abs(it) > 1e-9 } ?: 1.0
        val path = Path()
        samples.forEachIndexed { i, s ->
            val x = i.toFloat() / samples.lastIndex * size.width
            val y = ((maxV - s.amplitudeMv) / span * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF2DD4BF), style = Stroke(2.2f))
    }
}

@Composable
private fun AutoDifferentialPanel(result: LocalWaveformDigitizer.LocalTraceResult, modality: RecordingModality, showAll: Boolean, onToggle: () -> Unit) {
    val candidates = autoPreviewCandidates(result)
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("5. Rhythm differential", fontWeight = FontWeight.Bold)
            Text("Measured RR/morphology preview. Device labels are not treated as ground truth.", fontSize = 11.sp)
            candidates.forEachIndexed { index, item ->
                Text("${index + 1}. ${item.first}", fontWeight = if (index == 0) FontWeight.Bold else FontWeight.SemiBold, fontSize = 12.sp)
                Text(item.second, fontSize = 10.5.sp)
            }

            ArrhythmiaReferenceLibrary.cards.take(4).forEach { card ->
                HorizontalDivider()
                Text(card.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(card.shortDescription, fontSize = 10.5.sp)
                card.morphology.take(3).forEach { Text("• ${it.label}: ${it.whatToLookFor}", fontSize = 10.sp) }
                card.modalityCaveats.firstOrNull { it.modality == modality }?.let { Text("Modality: ${it.note}", fontSize = 10.sp, color = MaterialTheme.colorScheme.tertiary) }
            }

            TextButton(onClick = onToggle) { Text(if (showAll) "Hide exhaustive rhythm catalog" else "Show every catalogued rhythm") }
            if (showAll) {
                RhythmMechanismCatalog.displayNames.values.sorted().forEach { Text("• $it", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun AutoLongitudinalPanel(timeline: List<AutoSessionAnalysis>) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("6. Longitudinal timeline", fontWeight = FontWeight.Bold)
            timeline.sortedBy { it.timestamp }.forEach { item ->
                val rr = item.result.intervals.map { it.rrMs }
                val rates = item.result.intervals.mapNotNull { it.instantaneousRateBpm }
                val amp = item.result.waveformWindow.samples
                Text("${autoDate(item.timestamp)} • ${item.label}", fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp)
                Text("${item.modality.name.replace('_', ' ')} • mean rate ${rates.takeIf { it.isNotEmpty() }?.average()?.let { "%.1f bpm".format(it) } ?: "n/a"} • RR ${if (rr.isNotEmpty()) "%.1f–%.1f ms".format(rr.min(), rr.max()) else "n/a"} • amplitude ${if (amp.isNotEmpty()) "%.3f…%.3f mV".format(amp.minOf { it.amplitudeMv }, amp.maxOf { it.amplitudeMv }) else "n/a"}", fontSize = 10.sp)
                Text("Calibration: ${item.calibration.timeReferenceSource}; ${item.calibration.amplitudeReferenceSource}", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                HorizontalDivider()
            }
        }
    }
}

private fun autoPreviewCandidates(result: LocalWaveformDigitizer.LocalTraceResult): List<Pair<String, String>> {
    val rates = result.intervals.mapNotNull { it.instantaneousRateBpm }
    if (rates.isEmpty()) return listOf(
        "Unclassified rhythm" to "insufficient accepted ventricular beats for RR-based ranking",
        "Artifact / sensing mimic" to "retained because unresolved deflections remain visible"
    )
    val mean = rates.average()
    val rr = result.intervals.map { it.rrMs }
    val rrMean = rr.average()
    val rrSd = kotlin.math.sqrt(rr.map { (it - rrMean) * (it - rrMean) }.average())
    val cv = if (rrMean > 0) rrSd / rrMean else 0.0
    return when {
        mean >= 100 && cv < .06 -> listOf(
            "Regular tachycardia family" to "best fit from RR timing: ${"%.1f".format(mean)} bpm, RR CV ${"%.3f".format(cv)}",
            "Sinus tachycardia" to "compatible until atrial activity and onset behavior are resolved",
            "AVNRT / AVRT / focal atrial tachycardia" to "compatible regular supraventricular mechanisms",
            "Atrial flutter with fixed conduction" to "compatible if organized atrial activity supports it",
            "Artifact / oversensing mimic" to "must remain in the differential until extra deflections prove cardiac"
        )
        mean >= 100 -> listOf(
            "Irregular tachycardia family" to "best fit from RR variability: ${"%.1f".format(mean)} bpm, RR CV ${"%.3f".format(cv)}",
            "Atrial fibrillation" to "compatible irregular mechanism",
            "Flutter with variable conduction / ectopy" to "compatible",
            "Undersensing / noise" to "can create false irregularity in implanted-device data"
        )
        mean < 60 -> listOf(
            "Bradycardic rhythm family" to "mean ventricular rate ${"%.1f".format(mean)} bpm",
            "Sinus bradycardia" to "compatible if organized atrial activity is present",
            "AV block / escape rhythm" to "requires P-QRS timing and dropped-beat review",
            "ILR undersensing / false pause" to "must be excluded when QRS-like morphology persists through apparent gaps"
        )
        else -> listOf(
            "Normofrequent rhythm family" to "mean ventricular rate ${"%.1f".format(mean)} bpm",
            "Sinus rhythm" to "compatible pending atrial organization",
            "Atrial/ventricular ectopy" to "evaluate premature deflections",
            "Artifact / sensing mimic" to "retained until morphology and source pixels exclude it"
        )
    }
}

private suspend fun inspectAutoFile(context: Context, uri: Uri): AutoImportedFile = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    var name = uri.lastPathSegment ?: "imported-record"
    var size: Long? = null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val ni = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val si = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (ni >= 0) name = cursor.getString(ni) ?: name
            if (si >= 0 && !cursor.isNull(si)) size = cursor.getLong(si)
        }
    }
    val pages = if (mime == "application/pdf") resolver.openFileDescriptor(uri, "r")?.use { pfd -> PdfRenderer(pfd).use { it.pageCount } } else null
    val duration = if (mime.startsWith("video/")) MediaMetadataRetriever().use { r -> r.setDataSource(context, uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() } else null
    AutoImportedFile(uri, name, mime, size, pages, duration)
}

private suspend fun prepareAutoImage(context: Context, file: AutoImportedFile, pdfPage: Int, videoTimeMs: Long): Uri? = withContext(Dispatchers.IO) {
    when {
        file.mime.startsWith("image/") -> file.uri
        file.mime == "application/pdf" -> autoRenderPdf(context, file.uri, pdfPage)
        file.mime.startsWith("video/") -> autoRenderFrame(context, file.uri, videoTimeMs)
        else -> null
    }
}

private fun autoRenderPdf(context: Context, uri: Uri, pageIndex: Int): Uri {
    val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Unable to open PDF")
    pfd.use { descriptor -> PdfRenderer(descriptor).use { renderer ->
        val safe = pageIndex.coerceIn(0, renderer.pageCount - 1)
        renderer.openPage(safe).use { page ->
            val targetWidth = min(2800, max(1400, page.width * 2))
            val scale = targetWidth.toFloat() / page.width
            val bitmap = Bitmap.createBitmap(targetWidth, max(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return autoSaveBitmap(context, bitmap, "auto-pdf-${safe + 1}.png")
        }
    } }
}

private fun autoRenderFrame(context: Context, uri: Uri, timeMs: Long): Uri = MediaMetadataRetriever().use { r ->
    r.setDataSource(context, uri)
    val bitmap = r.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST) ?: error("Unable to extract video frame")
    autoSaveBitmap(context, bitmap, "auto-video-$timeMs.png")
}

private fun autoSaveBitmap(context: Context, bitmap: Bitmap, name: String): Uri {
    val dir = File(context.cacheDir, "ecg-auto-import").apply { mkdirs() }
    val f = File(dir, name)
    f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return Uri.fromFile(f)
}

private fun autoLeadName(lead: LeadConfiguration): String = when (lead) {
    LeadConfiguration.TWELVE_LEAD -> "Selected 12-lead tracing"
    LeadConfiguration.DEVICE_SPECIFIC_VECTOR -> "Device-specific subcutaneous vector"
    LeadConfiguration.SINGLE_LEAD -> "Single lead"
    else -> lead.name.replace('_', ' ')
}

private fun autoParseSymptoms(text: String): List<SymptomType> {
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

private fun autoNumeric(s: String) = s.filter { it.isDigit() || it == '.' }.take(12)
private fun autoFmtMs(v: Double?) = v?.let { "%.3f ms".format(it) } ?: "n/a"
private fun autoDate(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(t))
private fun autoFormatBytes(v: Long?) = when {
    v == null -> "size unknown"
    v < 1024 -> "$v B"
    v < 1024 * 1024 -> "%.1f KB".format(v / 1024.0)
    else -> "%.1f MB".format(v / (1024.0 * 1024.0))
}