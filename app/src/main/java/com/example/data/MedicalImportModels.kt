package com.example.data

/**
 * Describes what kind of medical evidence a user imported. The analyzer must
 * preserve provenance so reported metrics are never presented as waveform-
 * measured values and digitized values are never confused with source text.
 */
enum class MedicalEvidenceKind {
    WAVEFORM_IMAGE,
    WAVEFORM_PDF,
    WAVEFORM_VIDEO,
    RAW_WAVEFORM_DATA,
    INTERROGATION_REPORT,
    HOLTER_REPORT,
    ECG_REPORT,
    METRICS_ONLY_REPORT,
    MIXED_REPORT,
    UNKNOWN
}

enum class RecordingModality {
    DIAGNOSTIC_ECG,
    HOLTER,
    IMPLANTABLE_LOOP_RECORDER,
    EXTERNAL_LOOP_RECORDER,
    PATCH_MONITOR,
    EVENT_RECORDER,
    TELEMETRY,
    WEARABLE_ECG,
    BEDSIDE_MONITOR,
    CUSTOM,
    UNKNOWN
}

enum class LeadConfiguration {
    TWELVE_LEAD,
    SIX_LEAD,
    THREE_LEAD,
    TWO_LEAD,
    SINGLE_LEAD,
    DEVICE_SPECIFIC_VECTOR,
    UNKNOWN
}

enum class ValueProvenance {
    /** Printed or typed directly in the source report. */
    REPORTED,

    /** Calculated directly from a digitized waveform. */
    MEASURED,

    /** Derived mathematically from one or more measured/reported values. */
    DERIVED,

    /** Estimated by a model and therefore not a direct measurement. */
    ESTIMATED
}

data class ImportedMedicalDocument(
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val evidenceKind: MedicalEvidenceKind,
    val modality: RecordingModality = RecordingModality.UNKNOWN,
    val leadConfiguration: LeadConfiguration = LeadConfiguration.UNKNOWN,
    val pageCount: Int? = null,
    val durationMs: Long? = null,
    val notes: String = ""
)

data class CalibrationInfo(
    /** Millimetres per second for paper/scan ECGs, usually detected or entered. */
    val paperSpeedMmPerSec: Float? = null,
    /** Millimetres per millivolt for paper/scan ECGs, usually detected or entered. */
    val gainMmPerMv: Float? = null,
    /** Samples per second when native digital sampling information exists. */
    val sampleRateHz: Float? = null,
    /** Optional explicit pixel calibration after image digitization. */
    val pixelsPerSecond: Float? = null,
    val pixelsPerMv: Float? = null,
    val calibrationSource: ValueProvenance = ValueProvenance.REPORTED
)

data class MetricValue(
    val name: String,
    val value: Double,
    val unit: String,
    val provenance: ValueProvenance,
    val sourceLocation: String? = null,
    val confidence: Float? = null
)

data class WaveformSample(
    val sampleIndex: Long,
    val timeMs: Double,
    val amplitudeMv: Double,
    val leadName: String? = null,
    val signalQuality: Float? = null,
    val artifactFlag: Boolean = false
)

data class WaveformWindow(
    val leadName: String? = null,
    val startTimeMs: Double,
    val endTimeMs: Double,
    val samples: List<WaveformSample>,
    val calibration: CalibrationInfo,
    val sourceUri: String,
    val sourcePage: Int? = null,
    val sourceVideoStartMs: Long? = null,
    val sourceVideoEndMs: Long? = null
)

data class BeatMeasurement(
    val beatIndex: Int,
    val rPeakTimeMs: Double?,
    val rrMs: Double?,
    val pOnsetMs: Double?,
    val pPeakMs: Double?,
    val pEndMs: Double?,
    val qrsOnsetMs: Double?,
    val rPeakAmplitudeMv: Double?,
    val sNadirAmplitudeMv: Double?,
    val qrsEndMs: Double?,
    val tOnsetMs: Double?,
    val tPeakMs: Double?,
    val tEndMs: Double?,
    val prMs: Double?,
    val qrsDurationMs: Double?,
    val qtMs: Double?,
    val qtcMs: Double?,
    val artifactLikelihood: Float? = null,
    val notes: List<String> = emptyList()
)

data class ImportedAnalysisBundle(
    val document: ImportedMedicalDocument,
    val reportedMetrics: List<MetricValue> = emptyList(),
    val measuredMetrics: List<MetricValue> = emptyList(),
    val waveformWindows: List<WaveformWindow> = emptyList(),
    val beatMeasurements: List<BeatMeasurement> = emptyList(),
    val extractedText: String = ""
)
