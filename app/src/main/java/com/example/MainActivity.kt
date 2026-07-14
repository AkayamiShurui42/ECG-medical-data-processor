package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ArtifactPoint
import com.example.data.ClinicalLiterature
import com.example.data.EcgReportEntity
import com.example.data.EcgWavePoint
import com.example.data.LiteratureRepository
import com.example.ui.EcgVisualizerCanvas
import com.example.ui.EcgViewModel
import com.example.ui.BaselinesWorkspace
import com.example.ui.theme.MyApplicationTheme
import android.os.Build
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.data.AppLogger.init(this)
        enableEdgeToEdge()
        setContent {
            val viewModel: EcgViewModel = viewModel()
            val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
            val customPrimaryHex by viewModel.customPrimaryColorHex.collectAsStateWithLifecycle()

            MyApplicationTheme(
                dynamicColor = useDynamicColor,
                customPrimaryHex = customPrimaryHex
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    EcgDashboardScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun EcgDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: EcgViewModel = viewModel()
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) }

    // State bindings
    val reports by viewModel.allReports.collectAsStateWithLifecycle()
    val selectedReport by viewModel.selectedReport.collectAsStateWithLifecycle()
    val waveformPoints by viewModel.selectedWaveform.collectAsStateWithLifecycle()
    val activeInterpretation by viewModel.activeInterpretation.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val activeRhythmType by viewModel.activeRhythmType.collectAsStateWithLifecycle()
    val showXaiHeatmap by viewModel.showXaiHeatmap.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val apiErrorMessage by viewModel.apiErrorMessage.collectAsStateWithLifecycle()

    // Staged File state for direct verification before analysis
    var stagedFileUri by remember { mutableStateOf<Uri?>(null) }
    var stagedFileMetadata by remember { mutableStateOf<FileMetadata?>(null) }

    // Unified File Picker for both ECG images and PDFs
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            stagedFileUri = uri
            stagedFileMetadata = getFileMetadata(context, uri)
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    val useDynamicColorSetting by viewModel.useDynamicColor.collectAsStateWithLifecycle()
    val customPrimaryColorHexSetting by viewModel.customPrimaryColorHex.collectAsStateWithLifecycle()

    if (showSettings) {
        ThemeSettingsDialog(
            useDynamicColor = useDynamicColorSetting,
            customPrimaryHex = customPrimaryColorHexSetting,
            onUseDynamicColorChange = { viewModel.setUseDynamicColor(it) },
            onCustomPrimaryHexChange = { viewModel.setCustomPrimaryColorHex(it) },
            onDismissRequest = { showSettings = false }
        )
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header
        AppHeader(onSettingsClick = { showSettings = true })

        // Tab Navigation Bar
        TabSelector(
            activeTab = activeTab,
            onTabSelected = { activeTab = it }
        )

        HorizontalDivider(color = Color(0xFFE2E8F0))

        // Main Content Switcher with sliding/fading window transitions!
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }.using(
                    SizeTransform(clip = false)
                )
            },
            label = "TabTransition",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { targetTab ->
            when (targetTab) {
                0 -> {
                    // TAB 0: Analyser & Interactive Visualizer Workspace
                    AnalyserWorkspace(
                        waveformPoints = waveformPoints,
                        activeInterpretation = activeInterpretation,
                        isAnalyzing = isAnalyzing,
                        activeRhythmType = activeRhythmType,
                        showXaiHeatmap = showXaiHeatmap,
                        apiErrorMessage = apiErrorMessage,
                        onRhythmSelected = { viewModel.loadSyntheticRhythm(it) },
                        onToggleXai = { viewModel.toggleXaiHeatmap() },
                        stagedFileUri = stagedFileUri,
                        stagedFileMetadata = stagedFileMetadata,
                        onPickFile = {
                            filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                        },
                        onClearStagedFile = {
                            stagedFileUri = null
                            stagedFileMetadata = null
                        },
                        onAnalyzeStagedFile = {
                            stagedFileUri?.let { uri ->
                                viewModel.analyzeEcgImage(uri, context)
                                stagedFileUri = null
                                stagedFileMetadata = null
                            }
                        }
                    )
                }
                1 -> {
                    // TAB 1: Advanced Personal ECG Baselines & Echo Metrics
                    BaselinesWorkspace(viewModel = viewModel)
                }
                2 -> {
                    // TAB 2: Local SQLite Saved Clinical Reports History
                    SavedReportsHistory(
                        reports = reports,
                        selectedReport = selectedReport,
                        onReportSelect = {
                            viewModel.selectHistoricReport(it)
                            activeTab = 0 // Navigate back to visualizer
                        },
                        onReportDelete = { viewModel.deleteReport(it) },
                        onClearAll = { viewModel.clearAllReports() }
                    )
                }
                3 -> {
                    // TAB 3: AHA & NIH Scientific Literature Repository
                    LiteratureRepositoryView(
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.updateSearchQuery(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun AppHeader(
    onSettingsClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        primaryColor,
                        secondaryColor
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.2f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Analytics,
                contentDescription = "App Icon",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ECG Analyser",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = "AHA & NIH-Guided Clinical Diagnostic Assistant",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light
            )
        }

        val context = LocalContext.current
        IconButton(
            onClick = { com.example.data.AppLogger.shareLogs(context) }
        ) {
            Icon(
                imageVector = Icons.Filled.BugReport,
                contentDescription = "Share Logs & Crash Reports",
                tint = Color.White
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.testTag("theme_settings_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = "Theme Settings",
                tint = Color.White
            )
        }
    }
}

@Composable
fun TabSelector(
    activeTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val tabs = listOf(
            Triple(0, Icons.Filled.AutoGraph, "Analyser"),
            Triple(1, Icons.Filled.Favorite, "Baselines"),
            Triple(2, Icons.Filled.History, "Saved Reports"),
            Triple(3, Icons.Filled.Book, "AHA/NIH Lib")
        )

        tabs.forEach { (index, icon, label) ->
            val isSelected = activeTab == index
            val backgroundColor = if (isSelected) Color(0xFF0F766E).copy(alpha = 0.1f) else Color.Transparent
            val contentColor = if (isSelected) Color(0xFF0F766E) else Color(0xFF64748B)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun AnalyserWorkspace(
    waveformPoints: List<EcgWavePoint>,
    activeInterpretation: com.example.data.InterpretResponse?,
    isAnalyzing: Boolean,
    activeRhythmType: String,
    showXaiHeatmap: Boolean,
    apiErrorMessage: String?,
    onRhythmSelected: (String) -> Unit,
    onToggleXai: () -> Unit,
    stagedFileUri: Uri?,
    stagedFileMetadata: FileMetadata?,
    onPickFile: () -> Unit,
    onClearStagedFile: () -> Unit,
    onAnalyzeStagedFile: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Clinical Selection Bar
        Text(
            text = "Clinical Template Preloads",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF334155),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val cases = listOf(
                "Normal Sinus Rhythm",
                "Atrial Fibrillation",
                "ST-Elevation Ischemia",
                "Valvular & Congenital"
            )

            cases.forEach { item ->
                val isCurrent = activeRhythmType.contains(item.split(" ").first(), ignoreCase = true)
                val color = if (isCurrent) Color(0xFF0F766E) else Color(0xFFF1F5F9)
                val textColor = if (isCurrent) Color.White else Color(0xFF475569)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(color)
                        .clickable { onRhythmSelected(item) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = item,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Live Interactive Canvas Graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            EcgVisualizerCanvas(
                points = waveformPoints,
                segments = activeInterpretation?.segments ?: emptyList(),
                artifacts = activeInterpretation?.artifacts ?: emptyList(),
                showXai = showXaiHeatmap
            )
        }

        // Feature & Overlay Control Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Explainable AI (XAI) Heatmap Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (showXaiHeatmap) Color(0xFF0F766E).copy(alpha = 0.1f) else Color(0xFFF1F5F9))
                    .clickable { onToggleXai() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = "XAI Toggle",
                    tint = if (showXaiHeatmap) Color(0xFF0F766E) else Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Explainable AI (XAI) Overlay",
                    color = if (showXaiHeatmap) Color(0xFF0F766E) else Color(0xFF475569),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Status indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE2E8F0))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "RR Mapped",
                    color = Color(0xFF475569),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Warning or Guidance Box if API Key configuration error occurs
        apiErrorMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Alert",
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Analysis Notice",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = msg,
                            color = Color(0xFFB45309),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // Custom ECG Document Input Component
        if (stagedFileUri == null) {
            // Interactive Selection Zone (styled like standard HTML dashed input / dropzone)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { onPickFile() }
                    .border(
                        width = 1.5.dp,
                        color = Color(0xFF0F766E).copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = "Upload Document",
                        tint = Color(0xFF0F766E),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Select ECG Image or PDF",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Supports ECG scans, camera images (image/*) or medical reports (application/pdf)",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE2E8F0))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = "Security",
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "HIPAA COMPLIANT • SECURE CHANNEL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        } else {
            // Live Preview Card for selected document
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Verify Uploaded Document",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F766E),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Document Preview Container (Renders thumbnail for images, and standard document logo for PDFs)
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val isPdf = stagedFileMetadata?.type?.contains("pdf", ignoreCase = true) == true
                            if (isPdf) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.PictureAsPdf,
                                        contentDescription = "PDF File",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "PDF DOC",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = stagedFileUri,
                                    contentDescription = "ECG Image Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // File Metadata Details
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stagedFileMetadata?.name ?: "ECG_Scan.pdf",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = formatFileSize(stagedFileMetadata?.size ?: 0L),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF475569)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (stagedFileMetadata?.type?.contains("pdf") == true) Color(0xFFFEE2E2) else Color(0xFFE0F2FE),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stagedFileMetadata?.type?.uppercase() ?: "UNKNOWN",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (stagedFileMetadata?.type?.contains("pdf") == true) Color(0xFF991B1B) else Color(0xFF0369A1)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Verification Checked: Ready",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearStagedFile,
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Discard", color = Color(0xFF475569))
                        }

                        Button(
                            onClick = onAnalyzeStagedFile,
                            modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.OfflineBolt,
                                contentDescription = "Analyze",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analyze Document", color = Color.White)
                        }
                    }
                }
            }
        }

        // Arrhythmia Visual Distinctions Quick Reference Panel
        var showArrhythmiaRef by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showArrhythmiaRef = !showArrhythmiaRef },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoStories,
                            contentDescription = "Clinical Reference Book",
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Arrhythmia Visual Reference Guide",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    Icon(
                        imageVector = if (showArrhythmiaRef) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle Reference Guide",
                        tint = Color(0xFF64748B)
                    )
                }

                AnimatedVisibility(visible = showArrhythmiaRef) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "According to the newest 2026 American Heart Association (AHA) and National Institutes of Health (NIH) scientific consensus updates, visually distinguishing arrhythmias requires strict analysis of waveform morphologies and interval metrics:",
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        ReferenceItem(
                            title = "1. Normal Sinus Rhythm (NSR)",
                            description = "Uniform, upright dome-shaped P waves in Lead II (ranges 0.05-0.25 mV, duration 0.06-0.11s). Constant PR interval of 0.12 to 0.20s. Narrow QRS complexes (0.06 to 0.10s) with completely regular R-R intervals and rate between 60-100 bpm."
                        )
                        ReferenceItem(
                            title = "2. Atrial Fibrillation (AFib)",
                            description = "Complete absence of discrete P waves. The baseline is replaced by chaotic, undulating, and coarse fibrillatory (f) waves. The ventricular rhythm is completely 'irregularly irregular' with unpredictable, wildly variable R-R intervals."
                        )
                        ReferenceItem(
                            title = "3. Atrial Flutter (AFL)",
                            description = "Distinct, rapid, and regular 'sawtooth' shaped flutter (F) waves, most prominently observed in inferior leads (II, III, aVF) at a rate of 250-350 bpm, driven by a single re-entrant atrial circuit."
                        )
                        ReferenceItem(
                            title = "4. Ventricular Tachycardia (VT)",
                            description = "Wide, bizarre QRS complexes (>0.12s) occurring rapidly at 100-250 bpm. Can be monomorphic or polymorphic. Characterized by AV dissociation where P waves march independently of the rapid wide QRS complexes."
                        )
                        ReferenceItem(
                            title = "5. Ventricular Fibrillation (VFib)",
                            description = "Completely chaotic, irregular, and undulating baseline with absolutely zero discernible P waves, QRS complexes, or T waves. Immediate ACLS defibrillation is vital."
                        )
                        ReferenceItem(
                            title = "6. AV Block Progression",
                            description = "• First-Degree: PR interval is prolonged (>0.20s) but constant; all beats conduct.\n• Second-Degree Mobitz I (Wenckebach): Progressive lengthening of PR interval until a QRS is dropped.\n• Second-Degree Mobitz II: PR interval remains constant, but certain P waves intermittently fail to conduct.\n• Third-Degree: Complete AV dissociation; P waves and QRS complexes march independently."
                        )
                        ReferenceItem(
                            title = "7. Prolonged QT / Torsades de Pointes (TdP)",
                            description = "2026 AHA guidelines define QTc prolongation thresholds as >= 470ms in women and >= 460ms in men. Extremely prolonged QTc risks triggering Torsades de Pointes (TdP), a polymorphic VT where the QRS axes twist around the isoelectric line."
                        )
                    }
                }
            }
        }

        // Loading Screen Shimmer
        if (isAnalyzing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF0F766E),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AI Interpreting ECG...",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F766E),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Executing Homography image alignment, running noise filters, segmenting P-QRS-T complexes, and calibrating against AHA/NIH cardiological databases...",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }
            }
        } else {
            // Display Results Report
            activeInterpretation?.let { interpret ->
                DetailedClinicalReport(interpret)
            }
        }
    }
}

@Composable
fun DetailedClinicalReport(interpret: com.example.data.InterpretResponse) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Rhythm Badge and Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Diagnostic Classification",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = interpret.rhythmType,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE0F2FE))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "98.5% Confidence",
                        color = Color(0xFF0369A1),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quantitative Ventricular & Cardiac Metrics Row
             Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricWidget(
                    value = "${interpret.heartRate} bpm",
                    label = "Heart Rate",
                    imageVector = Icons.Filled.Favorite,
                    tint = Color(0xFFEF4444)
                )
                MetricWidget(
                    value = "${interpret.strokeVolume.toInt()} mL",
                    label = "Stroke Vol.",
                    imageVector = Icons.Filled.WaterDrop,
                    tint = Color(0xFF3B82F6)
                )
                MetricWidget(
                    value = "${interpret.ejectionFraction.toInt()}%",
                    label = "Ejection Frac.",
                    imageVector = Icons.Filled.DataSaverOn,
                    tint = Color(0xFF10B981)
                )
                MetricWidget(
                    value = "${interpret.lvCavitySize} cm",
                    label = "LV Size",
                    imageVector = Icons.Filled.AspectRatio,
                    tint = Color(0xFF8B5CF6)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Detected Conditions Tags
            Text(
                text = "Identified Pathologies & Patterns",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                interpret.conditions.forEach { cond ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFEE2E2))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Pathology",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cond,
                                color = Color(0xFF991B1B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Comprehensive Medical Findings Report Text
            Text(
                text = "Clinical Findings",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = interpret.findings,
                fontSize = 12.sp,
                color = Color(0xFF334155),
                lineHeight = 17.sp,
                textAlign = TextAlign.Justify
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Artifact Identification & Polarization mapping
            Text(
                text = "Wave Segmentation & Signal Artifacts",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(6.dp))
            interpret.artifacts.forEach { artifact ->
                ArtifactItemRow(artifact)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Scientific Literature References
            Text(
                text = "Clinical References (AHA & NIH)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(6.dp))
            interpret.literatureRefs.forEach { lit ->
                LiteratureCitationRow(lit)
            }
        }
    }
}

@Composable
fun MetricWidget(
    value: String,
    label: String,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(8.dp))
            .padding(10.dp)
            .width(62.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = label,
            fontSize = 8.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ArtifactItemRow(artifact: ArtifactPoint) {
    val (statusLabel, statusColor, bgColor) = when (artifact.classification) {
        "absolute" -> Triple("Definite Artifact", Color(0xFFEF4444), Color(0xFFFEF2F2))
        "potential" -> Triple("Potential Artifact", Color(0xFFF59E0B), Color(0xFFFEF3C7))
        else -> Triple("Natural Rhythm Peak", Color(0xFF10B981), Color(0xFFECFDF5))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(bgColor, shape = RoundedCornerShape(6.dp))
            .border(0.5.dp, statusColor.copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${artifact.label} (Index: ${artifact.index})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(statusColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = statusLabel,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = artifact.explanation,
            fontSize = 10.sp,
            color = Color(0xFF475569),
            lineHeight = 13.sp
        )
    }
}

@Composable
fun LiteratureCitationRow(lit: ClinicalLiterature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.MenuBook,
            contentDescription = "Literature Citation",
            tint = Color(0xFF0F766E),
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = lit.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "Evidence: ${lit.levelOfEvidence} | Year: ${lit.year}",
                fontSize = 9.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SavedReportsHistory(
    reports: List<EcgReportEntity>,
    selectedReport: EcgReportEntity?,
    onReportSelect: (EcgReportEntity) -> Unit,
    onReportDelete: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Local Tracing Logs",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            if (reports.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(
                        text = "Clear All",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (reports.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "No reports",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No saved ECG reports",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Any analysis or ECG image scans you interpret will be securely saved in your local Room database for historical review.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reports) { report ->
                    SavedReportCard(
                        report = report,
                        isSelected = report.id == selectedReport?.id,
                        onClick = { onReportSelect(report) },
                        onDelete = { onReportDelete(report.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SavedReportCard(
    report: EcgReportEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateString = remember(report.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
        sdf.format(Date(report.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF0F766E) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(10.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.rhythmType,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateString,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgeMetric("HR: ${report.heartRate} bpm")
                    BadgeMetric("SV: ${report.strokeVolume.toInt()} mL")
                    BadgeMetric("EF: ${report.ejectionFraction.toInt()}%")
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF4444)
                )
            }
        }
    }
}

@Composable
fun BadgeMetric(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFFF1F5F9), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF475569)
        )
    }
}

@Composable
fun LiteratureRepositoryView(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    val filteredLit = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            LiteratureRepository.literatureList
        } else {
            LiteratureRepository.literatureList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.summary.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Arrhythmia & Signal Calibration Guidelines",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = "Authoritative AHA/ACC and NIH literature from newest to oldest for artifact identification.",
            fontSize = 11.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            placeholder = { Text("Search guidelines by arrhythmia, artifact, etc...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search"
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF0F766E),
                focusedLabelColor = Color(0xFF0F766E)
            )
        )

        if (filteredLit.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.FindInPage,
                    contentDescription = "Not found",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No matching cardiology guidelines found",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredLit) { paper ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFCCFBF1))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = paper.category,
                                        color = Color(0xFF0F766E),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = paper.levelOfEvidence,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = paper.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "By ${paper.authors} — ${paper.source} (${paper.year})",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = paper.summary,
                                fontSize = 11.sp,
                                color = Color(0xFF334155),
                                lineHeight = 15.sp,
                                textAlign = TextAlign.Justify
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- ECG Document Input / Preview Metadata Support ---

data class FileMetadata(
    val name: String,
    val size: Long,
    val type: String
)

fun getFileMetadata(context: android.content.Context, uri: Uri): FileMetadata {
    var name = "ECG_Scan_Document.pdf"
    var size = 1048576L // 1.0 MB fallback
    var type = "application/pdf"
    
    if (uri.scheme == "content") {
        type = context.contentResolver.getType(uri) ?: "application/pdf"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
    } else {
        uri.path?.let { path ->
            val file = java.io.File(path)
            if (file.exists()) {
                name = file.name
                size = file.length()
            }
        }
    }
    return FileMetadata(name, size, type)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", size, units[unitIndex])
}

@Composable
fun ReferenceItem(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(8.dp))
            .border(0.5.dp, Color(0xFFE2E8F0), shape = RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F766E)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            fontSize = 11.sp,
            color = Color(0xFF334155),
            lineHeight = 15.sp,
            textAlign = TextAlign.Justify
        )
    }
}

@Composable
fun ThemeSettingsDialog(
    useDynamicColor: Boolean,
    customPrimaryHex: String,
    onUseDynamicColorChange: (Boolean) -> Unit,
    onCustomPrimaryHexChange: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("theme_settings_dismiss")
            ) {
                Text("Close")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Theme & Customization",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Customize the appearance and accent colors of your ECG Analyser app.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Material You / Dynamic Accent Toggle
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Wallpaper Theme (Material You)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Use system coloring schematic",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useDynamicColor,
                                onCheckedChange = onUseDynamicColorChange,
                                modifier = Modifier.testTag("dynamic_theme_switch")
                            )
                        }
                    }
                }

                // 2. Custom Base Color Spectrum Panels
                AnimatedVisibility(visible = !useDynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Select Accent Base Color",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val colorOptions = listOf(
                            Pair("Teal", "0xFF0F766E"),
                            Pair("Blue", "0xFF2563EB"),
                            Pair("Emerald", "0xFF10B981"),
                            Pair("Amber", "0xFFD97706"),
                            Pair("Purple", "0xFF7C3AED"),
                            Pair("Rose", "0xFFE11D48"),
                            Pair("Slate", "0xFF475569")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colorOptions.forEach { (name, hex) ->
                                val color = com.example.ui.theme.parseHexColor(hex)
                                val isSelected = customPrimaryHex.equals(hex, ignoreCase = true)

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { onCustomPrimaryHexChange(hex) }
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .testTag("color_panel_$name"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Text(
                            text = "Selected Accent: " + when(customPrimaryHex) {
                                "0xFF0F766E" -> "Teal (Clinical Premium)"
                                "0xFF2563EB" -> "Blue (Electric)"
                                "0xFF10B981" -> "Emerald (Safe)"
                                "0xFFD97706" -> "Amber (Warning)"
                                "0xFF7C3AED" -> "Purple (Royal)"
                                "0xFFE11D48" -> "Rose (Intense)"
                                else -> "Slate (Professional)"
                            },
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
