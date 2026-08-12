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

class EcgWorkbenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EcgWorkbenchScreen()
                }
            }
        }
    }
}

private data class ImportedFileInfo(
    val uri: Uri,
    val name: String,
    val mime: String,
    val sizeBytes: Long?,
    val pageCount: Int? = null,
    val durationMs: Long? = null
)

private data class SessionAnalysis(
    val label: String,
    val timestamp: Long,
    val modality: RecordingModality,
    val leadConfiguration: LeadConfiguration,
    val context: RecordingEventContext,
    val result: LocalWaveformDigitizer.LocalTraceResult
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcgWorkbenchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFile by remember { mutableStateOf<ImportedFileInfo?>(null) }
    var result by remember { mutableStateOf<LocalWaveformDigitizer.LocalTraceResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Import a tracing, interrogation report, Holter PDF, image, or monitor video.") }

    var modality by remember { mutableStateOf(RecordingModality.IMPLANTABLE_LOOP_RECORDER) }
    var leadConfig by remember { mutableStateOf(LeadConfiguration.SINGLE_LEAD) }
    var bodyPosition by remember { mutableStateOf(BodyPosition.UNKNOWN) }
    var activityState by remember { mutableStateOf(ActivityState.UNKNOWN) }
    var motionLevel by remember { mutableStateOf(MotionLevel.UNKNOWN) }
    var symptomsText by remember { mutableStateOf("") }
    var narrative by remember { mutableStateOf("") }

    var pixelsPerSecondText by remember { mutableStateOf("250") }
    var pixelsPerMvText by remember { mutableStateOf("100") }
    var pdfPageText by remember { mutableStateOf("1") }
    var videoSecondText by remember { mutableStateOf("0") }

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableFloatStateOf(0f) }
    var showAllRhythms by remember { mutableStateOf(false) }
    var showSamples by remember { mutableStateOf(true) }
    var showGuidelines by remember { mutableStateOf(true) }
    val timeline = remember { mutableStateListOf<SessionAnalysis>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                result = null
                errorText = null
                selectedFile = inspectImportedFile(context, uri)
                statusText = "Loaded ${selectedFile?.name ?: "document"}. Choose context/calibration, then analyze."
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ECG / IEGM Signal Workbench", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Tracing-first analysis • exact sample output • no fixed R/T blanking • exhaustive rhythm differential",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Import evidence", fontWeight = FontWeight.Bold)
                Button(onClick = {
                    picker.launch(arrayOf(
                        "image/*",
                        "application/pdf",
                        "video/*",
                        "text/plain",
                        "text/csv",
                        "application/json",
                        "application/octet-stream"
                    ))
                }) { Text("Choose medical record / tracing") }

                selectedFile?.let { file ->
                    Text(file.name, fontWeight = FontWeight.SemiBold)
                    Text("${file.mime}  •  ${formatBytes(file.sizeBytes)}", fontSize = 12.sp)
                    file.pageCount?.let { Text("PDF pages: $it", fontSize = 12.sp) }
                    file.durationMs?.let { Text("Video duration: ${"%.1f".format(it / 1000.0)} s", fontSize = 12.sp) }

                    if (file.mime.startsWith("image/")) {
                        AsyncImage(
                            model = file.uri,
                            contentDescription = "Imported tracing",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                        )
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("2. Recording type & event context", fontWeight = FontWeight.Bold)
                EnumSelector("Modality", modality, RecordingModality.entries) { modality = it }
                EnumSelector("Lead configuration", leadConfig, LeadConfiguration.entries) { leadConfig = it }
                EnumSelector("Body position", bodyPosition, BodyPosition.entries) { bodyPosition = it }
                EnumSelector("Activity", activityState, ActivityState.entries) { activityState = it }
                EnumSelector("Motion", motionLevel, MotionLevel.entries) { motionLevel = it }

                OutlinedTextField(
                    value = symptomsText,
                    onValueChange = { symptomsText = it },
                    label = { Text("Symptoms at event") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = narrative,
                    onValueChange = { narrative = it },
                    label = { Text("What was happening? lying still, asleep, walking, position change, etc.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                selectedFile?.pageCount?.let { pages ->
                    OutlinedTextField(
                        value = pdfPageText,
                        onValueChange = { pdfPageText = it.filter(Char::isDigit) },
                        label = { Text("PDF page to inspect (1–$pages)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                selectedFile?.durationMs?.let {
                    OutlinedTextField(
                        value = videoSecondText,
                        onValueChange = { videoSecondText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Video time to inspect (seconds)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Pixel calibration", fontWeight = FontWeight.Bold)
                Text(
                    "For image/PDF digitization, these convert pixels into milliseconds and mV. Adjust them when the source contains a known grid/calibration mark.",
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pixelsPerSecondText,
                        onValueChange = { pixelsPerSecondText = numericInput(it) },
                        label = { Text("pixels / second") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pixelsPerMvText,
                        onValueChange = { pixelsPerMvText = numericInput(it) },
                        label = { Text("pixels / mV") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    enabled = selectedFile != null && !busy,
                    onClick = {
                        val file = selectedFile ?: return@Button
                        scope.launch {
                            busy = true
                            errorText = null
                            statusText = "Extracting the actual waveform…"
                            try {
                                val analyzableUri = prepareWaveformImage(
                                    context = context,
                                    file = file,
                                    pdfPage = (pdfPageText.toIntOrNull() ?: 1) - 1,
                                    videoTimeMs = ((videoSecondText.toDoubleOrNull() ?: 0.0) * 1000.0).toLong()
                                )
                                if (analyzableUri == null) {
                                    statusText = "This file is imported as report/metric evidence. Waveform digitization requires an image, PDF page, or video frame."
                                } else {
                                    val config = LocalWaveformDigitizer.DigitizerConfig(
                                        pixelsPerSecond = pixelsPerSecondText.toFloatOrNull() ?: 250f,
                                        pixelsPerMv = pixelsPerMvText.toFloatOrNull() ?: 100f,
                                        leadName = when (leadConfig) {
                                            LeadConfiguration.TWELVE_LEAD -> "Selected 12-lead region"
                                            LeadConfiguration.DEVICE_SPECIFIC_VECTOR -> "Device-specific vector"
                                            else -> "Single channel"
                                        }
                                    )
                                    val analyzed = LocalWaveformDigitizer.digitizeImage(context, analyzableUri, config)
                                    result = analyzed
                                    zoom = 1f
                                    pan = 0f
                                    val eventContext = RecordingEventContext(
                                        bodyPosition = bodyPosition,
                                        activityState = activityState,
                                        motionLevel = motionLevel,
                                        symptoms = parseSymptoms(symptomsText),
                                        symptomNarrative = symptomsText,
                                        userNarrative = narrative,
                                        eventTriggerSource = EventTriggerSource.IMPORTED_REPORT,
                                        contextSource = ValueProvenance.REPORTED
                                    )
                                    timeline += SessionAnalysis(
                                        label = file.name + file.pageCount?.let { " • p.${pdfPageText}" }.orEmpty(),
                                        timestamp = System.currentTimeMillis(),
                                        modality = modality,
                                        leadConfiguration = leadConfig,
                                        context = eventContext,
                                        result = analyzed
                                    )
                                    statusText = "Waveform extracted: ${analyzed.waveformWindow.samples.size} calibrated samples, ${analyzed.events.size} candidate deflections, ${analyzed.intervals.size} accepted RR intervals."
                                }
                            } catch (t: Throwable) {
                                errorText = t.message ?: t.javaClass.simpleName
                                statusText = "Analysis failed."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "Analyzing…" else "Digitize & analyze tracing") }

                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(statusText, fontSize = 12.sp)
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }

        result?.let { analyzed ->
            SignalInspector(
                result = analyzed,
                zoom = zoom,
                pan = pan,
                onZoom = { zoom = it },
                onPan = { pan = it },
                showSamples = showSamples,
                onToggleSamples = { showSamples = !showSamples }
            )

            RhythmDifferentialPanel(
                result = analyzed,
                modality = modality,
                showAll = showAllRhythms,
                onToggleAll = { showAllRhythms = !showAllRhythms },
                showGuidelines = showGuidelines,
                onToggleGuidelines = { showGuidelines = !showGuidelines }
            )
        }

        if (timeline.isNotEmpty()) {
            LongitudinalPanel(timeline)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumSelector(label: String, value: T, values: List<T>, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) },
                    onClick = {
                        onChange(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SignalInspector(
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
    val intervalVisible = result.intervals.filter { rr ->
        rr.toRPeakTimeMs >= (visible.firstOrNull()?.timeMs ?: 0.0) &&
            rr.fromRPeakTimeMs <= (visible.lastOrNull()?.timeMs ?: Double.MAX_VALUE)
    }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("4. Signal Inspector", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onToggleSamples) { Text(if (showSamples) "Hide sample printout" else "Show sample printout") }
            }
            Text(
                "Visible window: ${formatMs(visible.firstOrNull()?.timeMs)} → ${formatMs(visible.lastOrNull()?.timeMs)} • ${visible.size} samples",
                fontSize = 12.sp
            )
            Text("Zoom ${"%.1f".format(zoom)}×", fontSize = 12.sp)
            Slider(value = zoom, onValueChange = onZoom, valueRange = 1f..24f)
            Text("Pan", fontSize = 12.sp)
            Slider(value = pan, onValueChange = onPan, valueRange = 0f..1f)

            WaveformCanvas(visible)

            Text("Beat-to-beat timing — no blanking", fontWeight = FontWeight.SemiBold)
            if (intervalVisible.isEmpty()) {
                Text("No accepted RR pair in this window. Candidate deflections are still retained.", fontSize = 12.sp)
            } else {
                intervalVisible.take(30).forEach { rr ->
                    Text(
                        "R${rr.fromBeatIndex + 1} → R${rr.toBeatIndex + 1}: ${"%.3f".format(rr.rrMs)} ms  (${rr.instantaneousRateBpm?.let { "%.2f bpm".format(it) } ?: "rate unavailable"})  • intervening=${rr.interveningEvents.size}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            if (showSamples) {
                HorizontalDivider()
                Text("Exact visible waveform samples", fontWeight = FontWeight.SemiBold)
                Text("index      time_ms      amplitude_mV   quality   artifact", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                val step = max(1, visible.size / 160)
                visible.filterIndexed { index, _ -> index % step == 0 }.take(160).forEach { sample ->
                    Text(
                        "%6d  %11.3f  %+12.5f   %6.3f   %s".format(
                            sample.sampleIndex,
                            sample.timeMs,
                            sample.amplitudeMv,
                            sample.signalQuality ?: 0f,
                            if (sample.artifactFlag) "yes" else "no"
                        ),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveformCanvas(samples: List<WaveformSample>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(Color(0xFF07111D), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        if (samples.size < 2) return@Canvas

        // Grid is visual only; measurements come from calibrated samples.
        val minorX = size.width / 25f
        val minorY = size.height / 10f
        var x = 0f
        while (x <= size.width) {
            drawLine(Color(0x2238BDF8), Offset(x, 0f), Offset(x, size.height), 1f)
            x += minorX
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(Color(0x2238BDF8), Offset(0f, y), Offset(size.width, y), 1f)
            y += minorY
        }

        val minAmp = samples.minOf { it.amplitudeMv }
        val maxAmp = samples.maxOf { it.amplitudeMv }
        val span = (maxAmp - minAmp).takeIf { abs(it) > 1e-9 } ?: 1.0
        val path = Path()
        samples.forEachIndexed { index, sample ->
            val px = index.toFloat() / (samples.lastIndex.coerceAtLeast(1)) * size.width
            val py = ((maxAmp - sample.amplitudeMv) / span * size.height).toFloat()
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, Color(0xFF5EEAD4), style = Stroke(width = 2.2f))
    }
}

@Composable
private fun RhythmDifferentialPanel(
    result: LocalWaveformDigitizer.LocalTraceResult,
    modality: RecordingModality,
    showAll: Boolean,
    onToggleAll: () -> Unit,
    showGuidelines: Boolean,
    onToggleGuidelines: () -> Unit
) {
    val preview = previewCandidates(result)
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("5. Rhythm differential", fontWeight = FontWeight.Bold)
            Text(
                "Likely candidates are only a preview scorer in this build. The complete catalog remains visible so a low-ranked mechanism is not silently discarded.",
                fontSize = 12.sp
            )

            preview.forEachIndexed { index, item ->
                Text("${index + 1}. ${item.first} — ${item.second}", fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal)
            }

            TextButton(onClick = onToggleAll) { Text(if (showAll) "Hide exhaustive catalog" else "Show every catalogued rhythm / mimic") }
            if (showAll) {
                RhythmMechanismCatalog.displayNames.values.sorted().forEach { name ->
                    Text("• $name", fontSize = 11.sp)
                }
            }

            HorizontalDivider()
            Text("Visual comparison cards", fontWeight = FontWeight.SemiBold)
            val cards = chooseReferenceCards(preview)
            cards.forEach { card -> RhythmReferenceCardView(card, modality) }

            TextButton(onClick = onToggleGuidelines) { Text(if (showGuidelines) "Hide international guideline summary" else "Show international guideline summary") }
            if (showGuidelines) {
                Text(
                    "Guideline layer: region-neutral synthesis across ESC/EHRA, HRS/ACC/AHA, APHRS, LAHRS, JCS/JHRS, CCS/CHRS, CSANZ/NHFA, NICE, ISHNE and other registered sources. Conflicts stay visible instead of one region automatically winning.",
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun RhythmReferenceCardView(card: RhythmReferenceCard, modality: RecordingModality) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(card.name, fontWeight = FontWeight.Bold)
            Text(card.shortDescription, fontSize = 11.sp)
            MiniReferenceWaveform(card.generatedExample)
            Text("What to look for", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            card.morphology.take(4).forEach { Text("• ${it.label}: ${it.whatToLookFor}", fontSize = 10.5.sp) }
            card.expectedNumbers.take(4).forEach { Text("• ${it.label}: ${it.expected}${it.unit?.let { u -> " $u" }.orEmpty()}", fontSize = 10.5.sp) }
            card.modalityCaveats.firstOrNull { it.modality == modality }?.let {
                Text("Modality note: ${it.note}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun MiniReferenceWaveform(points: List<EcgWavePoint>) {
    Canvas(Modifier.fillMaxWidth().height(90.dp).background(Color(0xFF08131F), RoundedCornerShape(8.dp))) {
        if (points.size < 2) return@Canvas
        val minV = points.minOf { it.value }
        val maxV = points.maxOf { it.value }
        val span = (maxV - minV).takeIf { abs(it) > 1e-6f } ?: 1f
        val path = Path()
        points.forEachIndexed { index, pt ->
            val x = index.toFloat() / points.lastIndex * size.width
            val y = (maxV - pt.value) / span * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF60A5FA), style = Stroke(2f))
    }
}

@Composable
private fun LongitudinalPanel(timeline: List<SessionAnalysis>) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("6. Longitudinal change timeline", fontWeight = FontWeight.Bold)
            Text("Each analysis is preserved in this session so changes in rate, RR spread, amplitude and sensing quality can be compared across reports/pages.", fontSize = 11.sp)
            timeline.sortedBy { it.timestamp }.forEach { item ->
                val intervals = item.result.intervals
                val meanRate = intervals.mapNotNull { it.instantaneousRateBpm }.takeIf { it.isNotEmpty() }?.average()
                val rrValues = intervals.map { it.rrMs }
                val rrRange = if (rrValues.isNotEmpty()) "${"%.1f".format(rrValues.min())}–${"%.1f".format(rrValues.max())} ms" else "n/a"
                val ampRange = item.result.waveformWindow.samples.let { samples ->
                    if (samples.isEmpty()) "n/a" else "${"%.3f".format(samples.minOf { it.amplitudeMv })}…${"%.3f".format(samples.maxOf { it.amplitudeMv })} mV"
                }
                Text(
                    "${formatDate(item.timestamp)} • ${item.label}\n${item.modality.name.replace('_', ' ')} • mean rate ${meanRate?.let { "%.1f bpm".format(it) } ?: "n/a"} • RR $rrRange • amplitude $ampRange",
                    fontSize = 10.5.sp
                )
                HorizontalDivider()
            }
        }
    }
}

private fun previewCandidates(result: LocalWaveformDigitizer.LocalTraceResult): List<Pair<String, String>> {
    val rates = result.intervals.mapNotNull { it.instantaneousRateBpm }
    if (rates.isEmpty()) {
        return listOf(
            "Unclassified rhythm" to "insufficient accepted ventricular beats for RR-based preview",
            "Artifact / sensing mimic" to "retained because candidate deflections remain unresolved"
        )
    }
    val mean = rates.average()
    val rr = result.intervals.map { it.rrMs }
    val rrMean = rr.average()
    val rrSd = kotlin.math.sqrt(rr.map { (it - rrMean) * (it - rrMean) }.average())
    val cv = if (rrMean > 0) rrSd / rrMean else 0.0

    return when {
        mean >= 100 && cv < 0.06 -> listOf(
            "Regular tachycardia" to "best-fit family from measured RR timing (${mean.format1()} bpm, RR CV ${cv.format3()})",
            "Sinus tachycardia" to "compatible until atrial activity/onset behavior is evaluated",
            "AVNRT / orthodromic AVRT / focal atrial tachycardia" to "compatible regular supraventricular mechanisms",
            "Atrial flutter with fixed conduction" to "compatible if organized atrial activity supports it",
            "Artifact / oversensing mimic" to "must remain in differential when extra deflections are not cardiac"
        )
        mean >= 100 && cv >= 0.06 -> listOf(
            "Irregular tachycardia" to "best-fit family from measured RR variability (${mean.format1()} bpm, RR CV ${cv.format3()})",
            "Atrial fibrillation" to "compatible irregular mechanism",
            "Atrial flutter with variable conduction" to "compatible",
            "Frequent atrial/ventricular ectopy" to "can produce irregular RR without AF",
            "Undersensing / noise" to "can create false irregularity in device recordings"
        )
        mean < 60 -> listOf(
            "Bradycardic rhythm family" to "measured mean rate ${mean.format1()} bpm",
            "Sinus bradycardia" to "compatible if organized atrial activity is present",
            "AV block / escape rhythm" to "requires P-QRS timing and dropped-beat analysis",
            "ILR undersensing / false pause" to "must be evaluated when QRS-like morphology persists through device-labelled gaps"
        )
        else -> listOf(
            "Normofrequent rhythm family" to "measured mean rate ${mean.format1()} bpm",
            "Sinus rhythm" to "compatible pending P-QRS organization",
            "Atrial/ventricular ectopy" to "evaluate candidate premature deflections",
            "Artifact / sensing mimic" to "retained until morphology and source-pixel review exclude it"
        )
    }
}

private fun chooseReferenceCards(preview: List<Pair<String, String>>): List<RhythmReferenceCard> {
    val text = preview.joinToString(" ") { it.first }.lowercase()
    val cards = ArrhythmiaReferenceLibrary.cards
    val preferred = buildList {
        if ("fibrillation" in text || "irregular" in text) cards.firstOrNull { it.id == "atrial_fibrillation" }?.let(::add)
        if ("flutter" in text) cards.firstOrNull { it.id == "atrial_flutter" }?.let(::add)
        if ("tachy" in text) cards.firstOrNull { it.id == "sinus_tachycardia" }?.let(::add)
        cards.firstOrNull { it.name.contains("Supraventricular", true) || it.id.contains("svt") }?.let(::add)
        cards.firstOrNull { it.name.contains("Ventricular Tachycardia", true) }?.let(::add)
    }.distinctBy { it.id }
    return if (preferred.isNotEmpty()) preferred.take(4) else cards.take(3)
}

private suspend fun inspectImportedFile(context: Context, uri: Uri): ImportedFileInfo = withContext(Dispatchers.IO) {
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

    ImportedFileInfo(uri, name, mime, size, pages, duration)
}

private suspend fun prepareWaveformImage(
    context: Context,
    file: ImportedFileInfo,
    pdfPage: Int,
    videoTimeMs: Long
): Uri? = withContext(Dispatchers.IO) {
    when {
        file.mime.startsWith("image/") -> file.uri
        file.mime == "application/pdf" -> renderPdfPage(context, file.uri, pdfPage)
        file.mime.startsWith("video/") -> renderVideoFrame(context, file.uri, videoTimeMs)
        else -> null
    }
}

private fun renderPdfPage(context: Context, uri: Uri, pageIndex: Int): Uri {
    val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Unable to open PDF")
    pfd.use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(safeIndex).use { page ->
                val targetWidth = min(2600, max(1200, page.width * 2))
                val scale = targetWidth.toFloat() / page.width.toFloat()
                val bitmap = Bitmap.createBitmap(targetWidth, max(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return saveBitmapToCache(context, bitmap, "pdf-page-${safeIndex + 1}.png")
            }
        }
    }
}

private fun renderVideoFrame(context: Context, uri: Uri, timeMs: Long): Uri {
    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(context, uri)
        val bitmap = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: error("Unable to extract video frame at ${timeMs} ms")
        return saveBitmapToCache(context, bitmap, "video-frame-${timeMs}.png")
    }
}

private fun saveBitmapToCache(context: Context, bitmap: Bitmap, name: String): Uri {
    val dir = File(context.cacheDir, "ecg-import").apply { mkdirs() }
    val file = File(dir, name)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return Uri.fromFile(file)
}

private fun parseSymptoms(text: String): List<SymptomType> {
    if (text.isBlank()) return emptyList()
    val lower = text.lowercase()
    val result = mutableListOf<SymptomType>()
    if ("palp" in lower) result += SymptomType.PALPITATIONS
    if ("presyn" in lower || "near faint" in lower) result += SymptomType.PRESYNCOPE
    if ("syncope" in lower || "faint" in lower || "passed out" in lower) result += SymptomType.SYNCOPE
    if ("dizz" in lower) result += SymptomType.DIZZINESS
    if ("weak" in lower) result += SymptomType.WEAKNESS
    if ("shortness" in lower || "dysp" in lower) result += SymptomType.DYSPNEA
    if ("chest" in lower) result += SymptomType.CHEST_PAIN
    if ("vision" in lower) result += SymptomType.VISION_CHANGE
    if (result.isEmpty()) result += SymptomType.OTHER
    return result
}

private fun numericInput(value: String): String = value.filter { it.isDigit() || it == '.' }.take(12)
private fun Double.format1(): String = "%.1f".format(this)
private fun Double.format3(): String = "%.3f".format(this)
private fun formatMs(value: Double?): String = value?.let { "%.3f ms".format(it) } ?: "n/a"
private fun formatDate(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
private fun formatBytes(bytes: Long?): String = when {
    bytes == null -> "size unknown"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
