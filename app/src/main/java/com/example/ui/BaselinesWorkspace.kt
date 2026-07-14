package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*

@Composable
fun BaselinesWorkspace(
    viewModel: EcgViewModel,
    modifier: Modifier = Modifier
) {
    val baselines by viewModel.allBaselines.collectAsStateWithLifecycle()
    val activeReport by viewModel.selectedReport.collectAsStateWithLifecycle()
    val activeWaveform by viewModel.selectedWaveform.collectAsStateWithLifecycle()
    val activeInterpretation by viewModel.activeInterpretation.collectAsStateWithLifecycle()

    // Editor state variables
    var title by remember { mutableStateOf("My Health Baseline") }
    var selectedDevice by remember { mutableStateOf("Wearable Devices") }
    var pWaveMs by remember { mutableStateOf("90") }
    var prMs by remember { mutableStateOf("160") }
    var qrsMs by remember { mutableStateOf("85") }
    var qtMs by remember { mutableStateOf("400") }
    var stMv by remember { mutableStateOf("0.0") }
    var tWaveMorphology by remember { mutableStateOf("Upright") }

    // Echocardiogram State
    var efPercent by remember { mutableStateOf("60") }
    var lvIddMm by remember { mutableStateOf("45.0") }
    var leftAtrialMm by remember { mutableStateOf("35.0") }
    var ivsMm by remember { mutableStateOf("10.0") }
    var pwMm by remember { mutableStateOf("10.0") }
    var valveFunction by remember { mutableStateOf("Normal") }

    // Selection State
    var selectedBaselineProfile by remember { mutableStateOf<EcgBaselineEntity?>(null) }
    var selectedReferenceRhythm by remember { mutableStateOf<ReferenceRhythm?>(null) }
    var expandedLibraryRhythm by remember { mutableStateOf<String?>(null) }

    val devices = listOf("Wearable Devices", "Single-Lead", "Holter Monitor", "Loop Recorder", "12-Lead")
    val morphologies = listOf("Upright", "Inverted", "Biphasic", "Flat")
    val valveStates = listOf("Normal", "Mild regurgitation", "Moderate regurgitation", "Severe regurgitation", "Mild stenosis", "Moderate stenosis", "Severe stenosis")

    var showMorphologyDropdown by remember { mutableStateOf(false) }
    var showDeviceDropdown by remember { mutableStateOf(false) }
    var showValveDropdown by remember { mutableStateOf(false) }

    // Default to the first predefined rhythm as baseline if none is selected
    val comparisonBaselineTitle = selectedBaselineProfile?.title
        ?: selectedReferenceRhythm?.name
        ?: "Normal Sinus Rhythm (Reference Standard)"

    val comparisonBaselineWaveform = remember(selectedBaselineProfile, selectedReferenceRhythm) {
        if (selectedBaselineProfile != null) {
            // Re-synthesize or use stored JSON
            if (selectedBaselineProfile!!.traceDataJson.isNotEmpty()) {
                deserializeTracePoints(selectedBaselineProfile!!.traceDataJson)
            } else {
                PredefinedRhythmLibrary.synthesizeWaveformForRhythm("Normal Sinus Rhythm (NSR)")
            }
        } else if (selectedReferenceRhythm != null) {
            PredefinedRhythmLibrary.synthesizeWaveformForRhythm(selectedReferenceRhythm!!.name)
        } else {
            PredefinedRhythmLibrary.synthesizeWaveformForRhythm("Normal Sinus Rhythm (NSR)")
        }
    }

    val baseP = selectedBaselineProfile?.pWaveDuration ?: selectedReferenceRhythm?.pWaveMs ?: 90
    val basePR = selectedBaselineProfile?.prInterval ?: selectedReferenceRhythm?.prIntervalMs ?: 160
    val baseQRS = selectedBaselineProfile?.qrsDuration ?: selectedReferenceRhythm?.qrsDurationMs ?: 80
    val baseQT = selectedBaselineProfile?.qtInterval ?: selectedReferenceRhythm?.qtMs ?: 400
    val baseST = selectedBaselineProfile?.stElevation ?: selectedReferenceRhythm?.stElevationMv ?: 0.0f
    val baseT = selectedBaselineProfile?.tWaveMorphology ?: selectedReferenceRhythm?.tWaveMorphology ?: "Upright"

    val baseEF = selectedBaselineProfile?.ejectionFraction ?: 60f
    val baseLVIDD = selectedBaselineProfile?.lvIdd ?: 45f
    val baseLA = selectedBaselineProfile?.leftAtrialSize ?: 35f
    val baseIVS = selectedBaselineProfile?.ivsThickness ?: 10f
    val basePW = selectedBaselineProfile?.pwThickness ?: 10f
    val baseValve = selectedBaselineProfile?.valveFunction ?: "Normal"

    // Derive active report metrics if loaded
    val activeP = 90 // default representing standard
    val activePR = activeInterpretation?.segments?.firstOrNull { it.type == "P" }?.let {
        activeInterpretation?.segments?.firstOrNull { it.type == "QRS" }?.let { qrs ->
            // PR interval approx = QRS start - P peak + constant
            (qrs.startIndex - it.peakIndex) * 10 + 60
        }
    } ?: 160

    val activeQRS = activeInterpretation?.segments?.firstOrNull { it.type == "QRS" }?.durationMs ?: 80
    val activeQT = activeInterpretation?.segments?.firstOrNull { it.type == "T" }?.let { t ->
        activeInterpretation?.segments?.firstOrNull { it.type == "QRS" }?.let { qrs ->
            (t.endIndex - qrs.startIndex) * 10
        }
    } ?: 400

    val activeST = if (activeInterpretation?.rhythmType?.contains("STEMI", true) == true || activeInterpretation?.rhythmType?.contains("ST-Elevation", true) == true) 3.5f else 0.0f
    val activeT = if (activeInterpretation?.rhythmType?.contains("AFib", true) == true || activeInterpretation?.rhythmType?.contains("Stenosis", true) == true) "Inverted" else "Upright"

    val activeEF = activeInterpretation?.ejectionFraction ?: baseEF
    val activeLVIDD = activeInterpretation?.lvCavitySize?.let { it * 10f } ?: baseLVIDD // Convert from cm if stored in report
    val activeLA = baseLA
    val activeIVS = baseIVS
    val activePW = basePW
    val activeValve = baseValve

    // Run alerts engine on the active reading utilizing the baseline / entered echocardiogram metrics
    val activeAlerts = remember(activeP, activePR, activeQRS, activeQT, activeST, activeT, activeEF, activeLVIDD, activeLA, activeIVS, activePW, activeValve) {
        CardiacAlertEngine.analyzeMetrics(
            pWaveMs = activeP,
            prMs = activePR,
            qrsMs = activeQRS,
            qtMs = activeQT,
            stElevationMv = activeST,
            tWaveMorphology = activeT,
            efPercent = activeEF,
            lvIddMm = activeLVIDD,
            leftAtrialMm = activeLA,
            ivsMm = activeIVS,
            pwMm = activePW,
            valveFunction = activeValve
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Explanatory Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF0F766E).copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HealthAndSafety,
                            contentDescription = "Alert Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "My Personal Cardiac Baselines",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Establish your healthy baseline parameters, select your device, and input echocardiogram dimensions to compare live scans against clinical guidelines.",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Live ECG Comparison Overlay Graph
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Superimposed Waveform Comparison",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF0F766E), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Active Reading", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF94A3B8), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Baseline", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Comparing: ${activeReport?.title ?: "Standard ECG Trace"} vs $comparisonBaselineTitle",
                        fontSize = 11.sp,
                        color = Color(0xFF0F766E),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DualEcgVisualizerCanvas(
                        activePoints = activeWaveform,
                        baselinePoints = comparisonBaselineWaveform,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )
                }
            }
        }

        // Split Layout: 1. Creation Form & 2. Personal Baselines List
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Baseline Creation Form
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Setup Custom Baseline Template",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Load active reading metrics button
                            if (activeInterpretation != null) {
                                Button(
                                    onClick = {
                                        title = "Baseline from ${activeReport?.rhythmType ?: "Active Scan"}"
                                        pWaveMs = activeP.toString()
                                        prMs = activePR.toString()
                                        qrsMs = activeQRS.toString()
                                        qtMs = activeQT.toString()
                                        stMv = activeST.toString()
                                        tWaveMorphology = activeT
                                        efPercent = activeEF.toInt().toString()
                                        lvIddMm = activeLVIDD.toString()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Filled.CopyAll, contentDescription = "Copy", modifier = Modifier.size(12.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Current", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Template Title
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Profile/Template Name", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Selected Device dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedDevice,
                                onValueChange = {},
                                label = { Text("ECG Device Type Used", fontSize = 11.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDeviceDropdown = true },
                                enabled = false,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = "Dropdown",
                                        modifier = Modifier.clickable { showDeviceDropdown = true }
                                    )
                                },
                                textStyle = TextStyle(fontSize = 13.sp)
                            )
                            DropdownMenu(
                                expanded = showDeviceDropdown,
                                onDismissRequest = { showDeviceDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                devices.forEach { dev ->
                                    DropdownMenuItem(
                                        text = { Text(dev, fontSize = 13.sp) },
                                        onClick = {
                                            selectedDevice = dev
                                            showDeviceDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "ECG Metric Baseline Values",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F766E),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 2x3 Grid for ECG Metrics
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pWaveMs,
                                onValueChange = { pWaveMs = it },
                                label = { Text("P Duration (ms)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                            OutlinedTextField(
                                value = prMs,
                                onValueChange = { prMs = it },
                                label = { Text("PR Interval (ms)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                            OutlinedTextField(
                                value = qrsMs,
                                onValueChange = { qrsMs = it },
                                label = { Text("QRS Duration (ms)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = qtMs,
                                onValueChange = { qtMs = it },
                                label = { Text("QT Interval (ms)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                            OutlinedTextField(
                                value = stMv,
                                onValueChange = { stMv = it },
                                label = { Text("ST Elev/Dep (mV)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = tWaveMorphology,
                                    onValueChange = {},
                                    label = { Text("T-Wave Morph", fontSize = 10.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showMorphologyDropdown = true },
                                    enabled = false,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    trailingIcon = {
                                        Icon(Icons.Filled.ArrowDropDown, "Dropdown", modifier = Modifier.clickable { showMorphologyDropdown = true })
                                    },
                                    textStyle = TextStyle(fontSize = 12.sp)
                                )
                                DropdownMenu(
                                    expanded = showMorphologyDropdown,
                                    onDismissRequest = { showMorphologyDropdown = false }
                                ) {
                                    morphologies.forEach { morph ->
                                        DropdownMenuItem(
                                            text = { Text(morph, fontSize = 12.sp) },
                                            onClick = {
                                                tWaveMorphology = morph
                                                showMorphologyDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Echocardiogram Structural Metrics",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F766E),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 3x2 Grid for Echo Metrics
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = efPercent,
                                onValueChange = { efPercent = it },
                                label = { Text("Ejection Fraction (%)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                            OutlinedTextField(
                                value = lvIddMm,
                                onValueChange = { lvIddMm = it },
                                label = { Text("LV IDD (mm / cavity)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = leftAtrialMm,
                                onValueChange = { leftAtrialMm = it },
                                label = { Text("LA Size (mm)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                            OutlinedTextField(
                                value = ivsMm,
                                onValueChange = { ivsMm = it },
                                label = { Text("Septal IVS (mm)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pwMm,
                                onValueChange = { pwMm = it },
                                label = { Text("Post Wall PW (mm)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1.5f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )

                            Box(modifier = Modifier.weight(2.5f)) {
                                OutlinedTextField(
                                    value = valveFunction,
                                    onValueChange = {},
                                    label = { Text("Valve Assessment", fontSize = 10.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showValveDropdown = true },
                                    enabled = false,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    trailingIcon = {
                                        Icon(Icons.Filled.ArrowDropDown, "Dropdown", modifier = Modifier.clickable { showValveDropdown = true })
                                    },
                                    textStyle = TextStyle(fontSize = 12.sp)
                                )
                                DropdownMenu(
                                    expanded = showValveDropdown,
                                    onDismissRequest = { showValveDropdown = false }
                                ) {
                                    valveStates.forEach { state ->
                                        DropdownMenuItem(
                                            text = { Text(state, fontSize = 12.sp) },
                                            onClick = {
                                                valveFunction = state
                                                showValveDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val baseline = EcgBaselineEntity(
                                    title = title,
                                    deviceType = selectedDevice,
                                    pWaveDuration = pWaveMs.toIntOrNull() ?: 90,
                                    prInterval = prMs.toIntOrNull() ?: 160,
                                    qrsDuration = qrsMs.toIntOrNull() ?: 85,
                                    qtInterval = qtMs.toIntOrNull() ?: 400,
                                    stElevation = stMv.toFloatOrNull() ?: 0.0f,
                                    tWaveMorphology = tWaveMorphology,
                                    ejectionFraction = efPercent.toFloatOrNull() ?: 60f,
                                    lvIdd = lvIddMm.toFloatOrNull() ?: 45.0f,
                                    leftAtrialSize = leftAtrialMm.toFloatOrNull() ?: 35.0f,
                                    ivsThickness = ivsMm.toFloatOrNull() ?: 10.0f,
                                    pwThickness = pwMm.toFloatOrNull() ?: 10.0f,
                                    valveFunction = valveFunction,
                                    traceDataJson = if (activeWaveform.isNotEmpty()) serializeTracePoints(activeWaveform) else ""
                                )
                                viewModel.saveBaseline(baseline)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = "Save Baseline", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Baseline Profile", fontSize = 13.sp, color = Color.White)
                        }
                    }
                }

                // Personal Baselines List Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Saved Personal Baselines",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (baselines.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No custom baselines created yet. Use the form above to save your first profile.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            baselines.forEach { baseline ->
                                val isSelected = selectedBaselineProfile?.id == baseline.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) Color(0xFF0F766E) else Color(0xFF334155),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .background(
                                            color = if (isSelected) Color(0xFF0F766E).copy(alpha = 0.05f) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedBaselineProfile = baseline
                                            selectedReferenceRhythm = null
                                        }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = baseline.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color(0xFF0F766E) else Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = "Device: ${baseline.deviceType} • EF: ${baseline.ejectionFraction.toInt()}% • LV IDD: ${baseline.lvIdd}mm",
                                            fontSize = 10.sp,
                                            color = Color(0xFFCBD5E1)
                                        )
                                        Text(
                                            text = "PR: ${baseline.prInterval}ms • QRS: ${baseline.qrsDuration}ms • QT: ${baseline.qtInterval}ms",
                                            fontSize = 10.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteBaseline(baseline.id) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Comparative Metrics Matrix Table
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Side-by-Side Comparative Analysis",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Table Headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Parameter", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.weight(2f))
                        Text("Active ECG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E), modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
                        Text("Baseline", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1), modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
                        Text("Deviation", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val rows = listOf(
                        Triple("P-Wave Duration", "${activeP} ms", "${baseP} ms"),
                        Triple("PR Interval", "${activePR} ms", "${basePR} ms"),
                        Triple("QRS Duration", "${activeQRS} ms", "${baseQRS} ms"),
                        Triple("QT Interval", "${activeQT} ms", "${baseQT} ms"),
                        Triple("ST Elevation/Dep", "${activeST} mV", "${baseST} mV"),
                        Triple("T-Wave Morphology", activeT, baseT),
                        Triple("Ejection Fraction", "${activeEF.toInt()}%", "${baseEF.toInt()}%"),
                        Triple("LV Internal Diam (IDD)", "${activeLVIDD} mm", "${baseLVIDD} mm"),
                        Triple("Left Atrial Size", "${activeLA} mm", "${baseLA} mm"),
                        Triple("Septal (IVS) Thickness", "${activeIVS} mm", "${baseIVS} mm"),
                        Triple("Post Wall (PW) Thickness", "${activePW} mm", "${basePW} mm"),
                        Triple("Valvular Function", activeValve, baseValve)
                    )

                    rows.forEach { (param, activeVal, baseVal) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(param, fontSize = 11.sp, color = Color(0xFF334155), modifier = Modifier.weight(2f))
                            Text(activeVal, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F766E), modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
                            Text(baseVal, fontSize = 11.sp, color = Color(0xFFCBD5E1), modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)

                            // Deviation calculation
                            val deviationText = when (param) {
                                "P-Wave Duration" -> {
                                    val delta = activeP - baseP
                                    if (delta == 0) "0 ms" else if (delta > 0) "+$delta ms" else "$delta ms"
                                }
                                "PR Interval" -> {
                                    val delta = activePR - basePR
                                    if (delta == 0) "0 ms" else if (delta > 0) "+$delta ms" else "$delta ms"
                                }
                                "QRS Duration" -> {
                                    val delta = activeQRS - baseQRS
                                    if (delta == 0) "0 ms" else if (delta > 0) "+$delta ms" else "$delta ms"
                                }
                                "QT Interval" -> {
                                    val delta = activeQT - baseQT
                                    if (delta == 0) "0 ms" else if (delta > 0) "+$delta ms" else "$delta ms"
                                }
                                "ST Elevation/Dep" -> {
                                    val delta = activeST - baseST
                                    if (delta == 0f) "0.0 mV" else if (delta > 0) "+$delta mV" else "$delta mV"
                                }
                                "Ejection Fraction" -> {
                                    val delta = activeEF.toInt() - baseEF.toInt()
                                    if (delta == 0) "0%" else if (delta > 0) "+$delta%" else "$delta%"
                                }
                                "LV Internal Diam (IDD)" -> {
                                    val delta = activeLVIDD - baseLVIDD
                                    if (delta == 0f) "0.0 mm" else if (delta > 0) "+$delta mm" else "$delta mm"
                                }
                                "Left Atrial Size" -> {
                                    val delta = activeLA - baseLA
                                    if (delta == 0f) "0.0 mm" else if (delta > 0) "+$delta mm" else "$delta mm"
                                }
                                "Septal (IVS) Thickness" -> {
                                    val delta = activeIVS - baseIVS
                                    if (delta == 0f) "0.0 mm" else if (delta > 0) "+$delta mm" else "$delta mm"
                                }
                                "Post Wall (PW) Thickness" -> {
                                    val delta = activePW - basePW
                                    if (delta == 0f) "0.0 mm" else if (delta > 0) "+$delta mm" else "$delta mm"
                                }
                                else -> "Matched"
                            }

                            val deviationColor = if (deviationText.startsWith("+") || deviationText.startsWith("-")) {
                                if (param == "Ejection Fraction" && deviationText.startsWith("-")) Color(0xFFEF4444)
                                else if (param != "Ejection Fraction" && (deviationText.startsWith("+") || deviationText.startsWith("-"))) Color(0xFFEF4444)
                                else Color(0xFF10B981)
                            } else {
                                Color(0xFF475569)
                            }

                            Text(
                                text = deviationText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = deviationColor,
                                modifier = Modifier.weight(1.5f),
                                textAlign = TextAlign.End
                            )
                        }
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                }
            }
        }

        // clinical Alert Cards with support literature
        if (activeAlerts.isNotEmpty()) {
            item {
                Text(
                    text = "AHA/NIH 2026 Clinical Alerts",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(activeAlerts) { alert ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (alert.severity == "CRITICAL") Color(0xFFFEF2F2) else Color(0xFFFFFBEB)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (alert.severity == "CRITICAL") Color(0xFFFCA5A5) else Color(0xFFFDE68A)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (alert.severity == "CRITICAL") Icons.Filled.Error else Icons.Filled.Warning,
                                contentDescription = "Severity",
                                tint = if (alert.severity == "CRITICAL") Color(0xFFDC2626) else Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = alert.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (alert.severity == "CRITICAL") Color(0xFF991B1B) else Color(0xFF92400E)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Triggered by: ${alert.triggeredBy}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = alert.description,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AHA recommendation: ${alert.recommendation}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F766E)
                        )

                        // Accompanying literature supporting diagnosis
                        if (alert.literatureCitations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = if (alert.severity == "CRITICAL") Color(0xFFFEE2E2) else Color(0xFFFEF3C7))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Supporting Medical Literature (Newest to Oldest):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            alert.literatureCitations.forEach { lit ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                        .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = lit.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F766E),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF334155))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = lit.year.toString(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Authors: ${lit.authors} • Source: ${lit.source}",
                                        fontSize = 9.sp,
                                        color = Color(0xFFCBD5E1),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = lit.summary,
                                        fontSize = 10.sp,
                                        color = Color(0xFF334155),
                                        lineHeight = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Evidence Level: ${lit.levelOfEvidence}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, "Normal", tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "All physiological and structural parameters within normal healthy ranges. No diagnostic alerts triggered.",
                            fontSize = 12.sp,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            }
        }

        // Predefined Rhythms Reference Library Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Clinical Reference Rhythm Baselines",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Browse typical intervals and waveforms for major cardiac pathologies to compare against your logs.",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    PredefinedRhythmLibrary.rhythms.forEach { rhythm ->
                        val isExpanded = expandedLibraryRhythm == rhythm.name
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedLibraryRhythm = if (isExpanded) null else rhythm.name
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(
                                                color = when (rhythm.category) {
                                                    "Normal" -> Color(0xFFECFDF5)
                                                    "Atrial" -> Color(0xFFEFF6FF)
                                                    "Ventricular" -> Color(0xFFFEF2F2)
                                                    else -> Color(0xFFFFFBEB)
                                                },
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (rhythm.category) {
                                                "Normal" -> Icons.Filled.CheckCircle
                                                "Atrial" -> Icons.Filled.MonitorHeart
                                                "Ventricular" -> Icons.Filled.Warning
                                                else -> Icons.Filled.ElectricBolt
                                            },
                                            contentDescription = "Rhythm icon",
                                            tint = when (rhythm.category) {
                                                "Normal" -> Color(0xFF10B981)
                                                "Atrial" -> Color(0xFF3B82F6)
                                                "Ventricular" -> Color(0xFFEF4444)
                                                else -> Color(0xFFF59E0B)
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = rhythm.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = "Expand",
                                    tint = Color(0xFF64748B)
                                )
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = rhythm.description,
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        lineHeight = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "AHA 2026 Treatment Guidelines:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F766E)
                                    )
                                    Text(
                                        text = rhythm.guidelines2026,
                                        fontSize = 10.sp,
                                        color = Color(0xFF334155),
                                        lineHeight = 14.sp
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                                .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                                .padding(6.dp)
                                        ) {
                                            Column {
                                                Text("HR: ${rhythm.typicalHr} bpm", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                                Text("PR: ${if (rhythm.prIntervalMs > 0) "${rhythm.prIntervalMs}ms" else "N/A"}", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                                .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                                .padding(6.dp)
                                        ) {
                                            Column {
                                                Text("QRS: ${rhythm.qrsDurationMs} ms", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                                Text("QT: ${if (rhythm.qtMs > 0) "${rhythm.qtMs}ms" else "N/A"}", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                                .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                                .padding(6.dp)
                                        ) {
                                            Column {
                                                Text("ST: ${rhythm.stElevationMv} mV", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                                Text("T Morph: ${rhythm.tWaveMorphology}", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            selectedReferenceRhythm = rhythm
                                            selectedBaselineProfile = null
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.Compare, "Compare", tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Compare Active with this Rhythm", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DualEcgVisualizerCanvas(
    activePoints: List<EcgWavePoint>,
    baselinePoints: List<EcgWavePoint>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFFDFD), shape = RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF334155), shape = RoundedCornerShape(12.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val maxPoints = 500

            val gridColorMinor = Color(0xFFFFF1F1) // Red medical grid minor lines
            val gridColorMajor = Color(0xFFFDDCDC) // Red medical grid major lines

            // 1. Draw Medical ECG Grid Lines
            val minorInterval = 15f
            val majorInterval = 75f

            // Vertical minor grid lines
            var xGrid = 0f
            while (xGrid < width) {
                drawLine(
                    color = gridColorMinor,
                    start = Offset(xGrid, 0f),
                    end = Offset(xGrid, height),
                    strokeWidth = 0.5f
                )
                xGrid += minorInterval
            }

            // Horizontal minor grid lines
            var yGrid = 0f
            while (yGrid < height) {
                drawLine(
                    color = gridColorMinor,
                    start = Offset(0f, yGrid),
                    end = Offset(width, yGrid),
                    strokeWidth = 0.5f
                )
                yGrid += minorInterval
            }

            // Vertical major grid lines
            xGrid = 0f
            while (xGrid < width) {
                drawLine(
                    color = gridColorMajor,
                    start = Offset(xGrid, 0f),
                    end = Offset(xGrid, height),
                    strokeWidth = 1.0f
                )
                xGrid += majorInterval
            }

            // Horizontal major grid lines
            yGrid = 0f
            while (yGrid < height) {
                drawLine(
                    color = gridColorMajor,
                    start = Offset(0f, yGrid),
                    end = Offset(width, yGrid),
                    strokeWidth = 1.0f
                )
                yGrid += majorInterval
            }

            fun getCoordinate(index: Int, value: Float): Offset {
                val pointX = (index.toFloat() / maxPoints) * width
                val pointY = centerY - (value * (height / 5.0f))
                return Offset(pointX, pointY)
            }

            // 2. Draw Baseline Waveform (Slate Gray, semi-transparent)
            if (baselinePoints.isNotEmpty()) {
                val baseWavePath = Path()
                baselinePoints.forEachIndexed { idx, pt ->
                    val coord = getCoordinate(pt.index, pt.value)
                    if (idx == 0) {
                        baseWavePath.moveTo(coord.x, coord.y)
                    } else {
                        baseWavePath.lineTo(coord.x, coord.y)
                    }
                }
                drawPath(
                    path = baseWavePath,
                    color = Color(0xFF94A3B8),
                    style = Stroke(width = 2.0f)
                )
            }

            // 3. Draw Active Waveform (Clinical Teal)
            if (activePoints.isNotEmpty()) {
                val activeWavePath = Path()
                activePoints.forEachIndexed { idx, pt ->
                    val coord = getCoordinate(pt.index, pt.value)
                    if (idx == 0) {
                        activeWavePath.moveTo(coord.x, coord.y)
                    } else {
                        activeWavePath.lineTo(coord.x, coord.y)
                    }
                }
                drawPath(
                    path = activeWavePath,
                    color = Color(0xFF0F766E),
                    style = Stroke(width = 3.0f)
                )
            }
        }
    }
}

private fun serializeTracePoints(points: List<EcgWavePoint>): String {
    return points.joinToString("|") { "${it.index},${it.value},${it.xaiHeatmapWeight}" }
}

private fun deserializeTracePoints(serialized: String): List<EcgWavePoint> {
    if (serialized.isEmpty()) return emptyList()
    return try {
        serialized.split("|").map {
            val tokens = it.split(",")
            EcgWavePoint(tokens[0].toInt(), tokens[1].toFloat(), tokens[2].toFloat())
        }
    } catch (e: Exception) {
        emptyList()
    }
}
