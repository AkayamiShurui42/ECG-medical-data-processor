package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class EcgViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        EcgDatabase::class.java,
        "ecg_database"
    ).fallbackToDestructiveMigration().build()

    private val repository = EcgRepository(db.ecgReportDao(), db.ecgBaselineDao())

    val allReports: StateFlow<List<EcgReportEntity>> = repository.allReports
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allBaselines: StateFlow<List<EcgBaselineEntity>> = repository.allBaselines
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedBaseline = MutableStateFlow<EcgBaselineEntity?>(null)
    val selectedBaseline: StateFlow<EcgBaselineEntity?> = _selectedBaseline.asStateFlow()

    fun selectBaseline(baseline: EcgBaselineEntity?) {
        _selectedBaseline.value = baseline
    }

    fun saveBaseline(baseline: EcgBaselineEntity) {
        viewModelScope.launch {
            repository.insertBaseline(baseline)
        }
    }

    fun deleteBaseline(id: Long) {
        viewModelScope.launch {
            repository.deleteBaselineById(id)
            if (_selectedBaseline.value?.id == id) {
                _selectedBaseline.value = null
            }
        }
    }

    fun clearAllBaselines() {
        viewModelScope.launch {
            repository.clearAllBaselines()
            _selectedBaseline.value = null
        }
    }

    private val _selectedReport = MutableStateFlow<EcgReportEntity?>(null)
    val selectedReport: StateFlow<EcgReportEntity?> = _selectedReport.asStateFlow()

    private val _selectedWaveform = MutableStateFlow<List<EcgWavePoint>>(emptyList())
    val selectedWaveform: StateFlow<List<EcgWavePoint>> = _selectedWaveform.asStateFlow()

    private val _activeInterpretation = MutableStateFlow<InterpretResponse?>(null)
    val activeInterpretation: StateFlow<InterpretResponse?> = _activeInterpretation.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("ecg_settings", Context.MODE_PRIVATE)

    private val _geminiApiKey = MutableStateFlow(sharedPrefs.getString("gemini_api_key", "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun setGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        sharedPrefs.edit().putString("gemini_api_key", key).apply()
    }

    fun getApiKey(): String {
        val userKey = _geminiApiKey.value
        if (userKey.isNotEmpty()) return userKey
        return BuildConfig.GEMINI_API_KEY
    }

    fun isApiKeyConfigured(): Boolean {
        val userKey = _geminiApiKey.value
        if (userKey.isNotEmpty()) return true
        val buildKey = BuildConfig.GEMINI_API_KEY
        return buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY" && !buildKey.startsWith("placeholder")
    }

    private val _selectedDeviceType = MutableStateFlow("Standard 12-Lead ECG")
    val selectedDeviceType: StateFlow<String> = _selectedDeviceType.asStateFlow()

    private val _isPatientMoving = MutableStateFlow(false)
    val isPatientMoving: StateFlow<Boolean> = _isPatientMoving.asStateFlow()

    private val _patientSymptoms = MutableStateFlow("")
    val patientSymptoms: StateFlow<String> = _patientSymptoms.asStateFlow()

    fun setDeviceType(device: String) {
        _selectedDeviceType.value = device
    }

    fun setIsPatientMoving(moving: Boolean) {
        _isPatientMoving.value = moving
    }

    fun setPatientSymptoms(symptoms: String) {
        _patientSymptoms.value = symptoms
    }

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _activeRhythmType = MutableStateFlow("Normal Sinus Rhythm")
    val activeRhythmType: StateFlow<String> = _activeRhythmType.asStateFlow()

    private val _showXaiHeatmap = MutableStateFlow(false)
    val showXaiHeatmap: StateFlow<Boolean> = _showXaiHeatmap.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ClinicalLiterature>>(LiteratureRepository.literatureList.sortedByDescending { it.year })
    val searchResults: StateFlow<List<ClinicalLiterature>> = _searchResults.asStateFlow()

    private val _apiErrorMessage = MutableStateFlow<String?>(null)
    val apiErrorMessage: StateFlow<String?> = _apiErrorMessage.asStateFlow()

    private val _useDynamicColor = MutableStateFlow(true)
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    private val _customPrimaryColorHex = MutableStateFlow("0xFF0F766E")
    val customPrimaryColorHex: StateFlow<String> = _customPrimaryColorHex.asStateFlow()

    fun setUseDynamicColor(use: Boolean) {
        _useDynamicColor.value = use
    }

    fun setCustomPrimaryColorHex(hex: String) {
        _customPrimaryColorHex.value = hex
    }

    init {
        // Load default Normal Sinus Rhythm on startup
        loadSyntheticRhythm("Normal Sinus Rhythm")
    }

    fun loadSyntheticRhythm(type: String) {
        _activeRhythmType.value = type
        val response = EcgSynthesizer.generateRhythm(type)
        val wavePoints = EcgSynthesizer.synthesizeWaveform(type)

        _activeInterpretation.value = response
        _selectedWaveform.value = wavePoints
        _selectedReport.value = null // Not looking at a historic saved report yet
        _apiErrorMessage.value = null
    }

    fun toggleXaiHeatmap() {
        _showXaiHeatmap.value = !_showXaiHeatmap.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            if (query.isEmpty()) {
                _searchResults.value = LiteratureRepository.literatureList.sortedByDescending { it.year }
            } else {
                val results = withContext(Dispatchers.IO) {
                    PubMedSearch.searchPubMed(query)
                }
                if (results.isNotEmpty()) {
                    _searchResults.value = results.sortedByDescending { it.year }
                } else {
                    _searchResults.value = LiteratureRepository.literatureList.filter {
                        it.title.contains(query, ignoreCase = true) ||
                                it.summary.contains(query, ignoreCase = true) ||
                                it.category.contains(query, ignoreCase = true)
                    }.sortedByDescending { it.year }
                }
            }
        }
    }

    fun selectHistoricReport(report: EcgReportEntity) {
        _selectedReport.value = report

        // Deserialize stored JSON fields to restore active visualizer states
        try {
            val tracePoints = deserializeTracePoints(report.traceDataJson)
            val activeInterpret = InterpretResponse(
                rhythmType = report.rhythmType,
                heartRate = report.heartRate,
                strokeVolume = report.strokeVolume,
                ejectionFraction = report.ejectionFraction,
                lvCavitySize = report.lvCavitySize,
                findings = report.findings,
                conditions = deserializeStringList(report.traceDataJson), // Fallback or empty if not parsed
                segments = deserializeSegments(report.annotationsJson),
                artifacts = deserializeArtifacts(report.artifactsJson),
                literatureRefs = deserializeLiterature(report.literatureRefsJson)
            )

            _selectedWaveform.value = tracePoints
            _activeInterpretation.value = activeInterpret
            _activeRhythmType.value = report.rhythmType
        } catch (e: Exception) {
            Log.e("EcgViewModel", "Error restoring historic report", e)
        }
    }

    fun deleteReport(reportId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteReportById(reportId)
            if (_selectedReport.value?.id == reportId) {
                _selectedReport.value = null
            }
        }
    }

    fun clearAllReports() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
            _selectedReport.value = null
        }
    }

    fun analyzeEcgImage(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _apiErrorMessage.value = null
 
            try {
                if (!isApiKeyConfigured()) {
                    // Fallback to beautiful simulation if no API key is specified
                    simulateAnalysisWithDelay("Atrial Fibrillation")
                    _apiErrorMessage.value = "Gemini API key is not configured. Displaying simulated cardiac analysis based on AHA guidelines."
                    return@launch
                }
 
                val base64Image = withContext(Dispatchers.IO) {
                    val bitmap = loadBitmapFromUri(context, uri, 1024) ?: throw Exception("Failed to read image or PDF.")
                    bitmap.toBase64()
                }
 
                val prompt = """
                    You are an expert cardiologist AI. Analyze this heart monitor tracing or ECG image.
                    
                    CLINICAL METADATA PROVIDED BY THE USER:
                    - ECG Device Source: ${_selectedDeviceType.value}
                    - Is Patient Moving (Motion Artifact Check): ${if (_isPatientMoving.value) "Yes (expect motion artifact distortion)" else "No (clean trace expected)"}
                    - Patient Activity & Clinical Symptoms: ${_patientSymptoms.value.ifEmpty { "None specified" }}
                    
                    INSTRUCTIONS FOR INTERPRETATION & EXPLAINABILITY (FOR TEXT-ONLY READERS):
                    1. For every clinical finding, clearly describe the corresponding visual pattern in the trace (e.g., "PR interval prolongation," "ST segment elevation in anterior leads") in simple terms so that a layperson can map the text to the visual chart.
                    2. If this is a standard 12-lead ECG scan or contains multiple tracings, explicitly name the specific leads (e.g., V1-V3, II, III, aVF) showing the abnormalities so the user can cross-reference with clinical records.
                    3. Explain clearly why muscle tremor or electrode motion (based on the Patient Moving flag) does or does not account for the abnormal readings.
                    4. Ground your analysis with references to specific American Heart Association (AHA) / American College of Cardiology (ACC) guidelines or National Institutes of Health (NIH) consensus guidelines.
                    
                    Ensure that you look for wave uniformity and identify potential arrhythmias or abnormal heart rate patterns.
                    Specifically look for:
                    - P-waves
                    - QRS complex segments
                    - T-waves
                    - Provide a detailed report on clinical findings based on the newest AHA & NIH scientific literature.
                    
                    Estimate these metrics based on visual pattern markers (e.g. sign of remodeling, ischemia, or hypertrophy):
                    - Stroke Volume (in mL, e.g., 65.0)
                    - Left Ventricular Cavity Size (in cm, e.g., 4.7)
                    - Ejection Fraction (in %, e.g., 55.0)
                    - Heart Rate (in BPM, e.g., 85)
                    
                    Identify cardiac conditions affecting the heart such as arrhythmias, congenital defects, valvular stenosis, or ischemic patterns (ST-segment alterations).
                    Identify 3 to 5 artifact points in the tracing and classify them in 'artifacts' as absolute artifacts, potential artifacts, or none (natural rhythm polarization markers) with detailed descriptions of why they are or are not artifacts.
                    
                    You MUST return your entire response as a valid, strictly formatted JSON matching this schema:
                    {
                      "rhythmType": "String (the primary classified rhythm, e.g. Atrial Fibrillation (AFib))",
                      "heartRate": Integer,
                      "strokeVolume": Float,
                      "ejectionFraction": Float,
                      "lvCavitySize": Float,
                      "findings": "Detailed medical findings report",
                      "conditions": ["Condition 1", "Condition 2"],
                      "segments": [
                        {"type": "P|QRS|T", "startIndex": Integer (0-500, relative positions), "endIndex": Integer, "peakIndex": Integer, "durationMs": Integer, "description": "Clinical details"}
                      ],
                      "artifacts": [
                        {"index": Integer (0-500), "classification": "absolute|potential|none", "label": "Label", "explanation": "Scientific reasoning"}
                      ],
                      "literatureRefs": [
                        {"title": "Title of literature", "authors": "Authors", "source": "AHA/NIH journal", "year": Integer, "summary": "Key takeaways", "category": "Arrhythmia|Artifact Differentiation|Valvular|Heart Failure", "levelOfEvidence": "e.g. AHA Class I"}
                      ]
                    }
                    Only output the raw JSON. Do not include markdown code block syntax.
                """.trimIndent()
 
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = prompt),
                                GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = base64Image))
                            )
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.2f
                    )
                )
 
                val response = withContext(Dispatchers.IO) {
                    GeminiNetwork.service.generateContent(getApiKey(), request)
                }

                val jsonResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("No analytical report returned from Gemini.")

                val interpretResult = withContext(Dispatchers.Default) {
                    GeminiNetwork.interpretResponseAdapter.fromJson(jsonResponse)
                        ?: throw Exception("Failed to parse cardiology interpretation model.")
                }

                // Successfully analyzed: generate synthetic points matching their rhythm, and attach the AI findings
                val wavePoints = withContext(Dispatchers.Default) {
                    val baseType = if (interpretResult.rhythmType.contains("AFib", true) || interpretResult.rhythmType.contains("Fibrillation", true)) {
                        "Atrial Fibrillation"
                    } else if (interpretResult.rhythmType.contains("Ischemia", true) || interpretResult.rhythmType.contains("STEMI", true) || interpretResult.rhythmType.contains("Infarction", true)) {
                        "ST-Elevation Ischemia"
                    } else if (interpretResult.rhythmType.contains("Stenosis", true) || interpretResult.rhythmType.contains("Hypertrophy", true)) {
                        "Valvular & Congenital"
                    } else {
                        "Normal Sinus Rhythm"
                    }
                    EcgSynthesizer.synthesizeWaveform(baseType)
                }

                _activeInterpretation.value = interpretResult
                _selectedWaveform.value = wavePoints
                _activeRhythmType.value = interpretResult.rhythmType

                // Save report to Room database history
                withContext(Dispatchers.IO) {
                    val reportEntity = EcgReportEntity(
                        title = "ECG Analysis - ${interpretResult.rhythmType}",
                        rhythmType = interpretResult.rhythmType,
                        heartRate = interpretResult.heartRate,
                        strokeVolume = interpretResult.strokeVolume,
                        ejectionFraction = interpretResult.ejectionFraction,
                        lvCavitySize = interpretResult.lvCavitySize,
                        findings = interpretResult.findings,
                        traceDataJson = serializeTracePoints(wavePoints),
                        annotationsJson = serializeSegments(interpretResult.segments),
                        artifactsJson = serializeArtifacts(interpretResult.artifacts),
                        literatureRefsJson = serializeLiterature(interpretResult.literatureRefs)
                    )
                    val insertedId = repository.insertReport(reportEntity)
                    _selectedReport.value = reportEntity.copy(id = insertedId)
                }

            } catch (e: Exception) {
                Log.e("EcgViewModel", "Error analyzing ECG", e)
                _apiErrorMessage.value = "Cardiology interpretation error: ${e.message}. Displaying local safety simulation."
                simulateAnalysisWithDelay("Normal Sinus Rhythm")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private suspend fun simulateAnalysisWithDelay(rhythmType: String) {
        _isAnalyzing.value = true
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.delay(2000) // Realistic processing delay
            val response = EcgSynthesizer.generateRhythm(rhythmType)
            val wavePoints = EcgSynthesizer.synthesizeWaveform(rhythmType)

            _activeInterpretation.value = response
            _selectedWaveform.value = wavePoints
            _activeRhythmType.value = response.rhythmType

            val reportEntity = EcgReportEntity(
                title = "ECG Simulation - ${response.rhythmType}",
                rhythmType = response.rhythmType,
                heartRate = response.heartRate,
                strokeVolume = response.strokeVolume,
                ejectionFraction = response.ejectionFraction,
                lvCavitySize = response.lvCavitySize,
                findings = response.findings,
                traceDataJson = serializeTracePoints(wavePoints),
                annotationsJson = serializeSegments(response.segments),
                artifactsJson = serializeArtifacts(response.artifacts),
                literatureRefsJson = serializeLiterature(response.literatureRefs)
            )
            val insertedId = repository.insertReport(reportEntity)
            _selectedReport.value = reportEntity.copy(id = insertedId)
        }
        _isAnalyzing.value = false
    }

    // --- JSON Serialization/Deserialization Helpers using basic parsing ---

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

    private fun serializeSegments(segments: List<WaveSegment>): String {
        return segments.joinToString("|") { "${it.type},${it.startIndex},${it.endIndex},${it.peakIndex},${it.durationMs},${it.description}" }
    }

    private fun deserializeSegments(serialized: String): List<WaveSegment> {
        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split("|").map {
                val tokens = it.split(",")
                WaveSegment(tokens[0], tokens[1].toInt(), tokens[2].toInt(), tokens[3].toInt(), tokens[4].toInt(), tokens[5])
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeArtifacts(artifacts: List<ArtifactPoint>): String {
        return artifacts.joinToString("|") { "${it.index},${it.classification},${it.label},${it.explanation}" }
    }

    private fun deserializeArtifacts(serialized: String): List<ArtifactPoint> {
        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split("|").map {
                val tokens = it.split(",")
                ArtifactPoint(tokens[0].toInt(), tokens[1], tokens[2], tokens[3])
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeLiterature(lit: List<ClinicalLiterature>): String {
        return lit.joinToString("|") { "${it.title};${it.authors};${it.source};${it.year};${it.summary};${it.category};${it.levelOfEvidence}" }
    }

    private fun deserializeLiterature(serialized: String): List<ClinicalLiterature> {
        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split("|").map {
                val tokens = it.split(";")
                ClinicalLiterature(tokens[0], tokens[1], tokens[2], tokens[3].toInt(), tokens[4], tokens[5], tokens[6])
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun deserializeStringList(serialized: String): List<String> {
        return emptyList()
    }

    // --- Image Resizing Utilities to limit payload and token count ---

    private fun getResizedBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            var scale = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                scale = Math.pow(2.0, Math.ceil(Math.log(maxDimension.toDouble() / Math.max(options.outHeight, options.outWidth).toDouble()) / Math.log(0.5)).toInt().toDouble()).toInt()
            }

            val outOptions = BitmapFactory.Options().apply { inSampleSize = scale }
            inputStream = context.contentResolver.openInputStream(uri)
            val resizedBitmap = BitmapFactory.decodeStream(inputStream, null, outOptions)
            resizedBitmap
        } catch (e: Exception) {
            Log.e("EcgViewModel", "Error decoding bitmap", e)
            null
        } finally {
            inputStream?.close()
        }
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val type = context.contentResolver.getType(uri) ?: ""
        val isPdf = type.contains("pdf", ignoreCase = true) || uri.toString().endsWith(".pdf", ignoreCase = true)
        return if (isPdf) {
            renderPdfPageToBitmap(context, uri)
        } else {
            getResizedBitmap(context, uri, maxDimension)
        }
    }

    private fun renderPdfPageToBitmap(context: Context, uri: Uri): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    page = renderer.openPage(0)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("EcgViewModel", "Error rendering PDF page to bitmap", e)
            null
        } finally {
            try {
                page?.close()
                renderer?.close()
                pfd?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
