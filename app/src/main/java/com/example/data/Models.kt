package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EcgWavePoint(
    val index: Int,
    val value: Float,
    val xaiHeatmapWeight: Float = 0f // Weight (0.0 to 1.0) for Explainable AI (XAI) overlays
)

@JsonClass(generateAdapter = true)
data class WaveSegment(
    val type: String, // "P", "QRS", "T"
    val startIndex: Int,
    val endIndex: Int,
    val peakIndex: Int,
    val durationMs: Int,
    val description: String
)

@JsonClass(generateAdapter = true)
data class ArtifactPoint(
    val index: Int,
    val classification: String, // "absolute", "potential", "none"
    val label: String, // e.g., "Muscle Tremor", "Powerline Interference", "Natural Heartbeat"
    val explanation: String
)

@JsonClass(generateAdapter = true)
data class ClinicalLiterature(
    val title: String,
    val authors: String,
    val source: String,
    val year: Int,
    val summary: String,
    val category: String, // "Arrhythmia", "Congenital", "Valvular", "Ischemic", "Artifact Differentiation"
    val levelOfEvidence: String // e.g., "AHA Class I", "NIH Guidelines 2026", "AHA/ACC 2024"
)

@JsonClass(generateAdapter = true)
data class InterpretResponse(
    val rhythmType: String,
    val heartRate: Int,
    val strokeVolume: Float,
    val ejectionFraction: Float,
    val lvCavitySize: Float,
    val findings: String,
    val conditions: List<String>,
    val segments: List<WaveSegment>,
    val artifacts: List<ArtifactPoint>,
    val literatureRefs: List<ClinicalLiterature>
)
