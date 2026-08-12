package com.example.data

import kotlin.math.round

/**
 * Continuous ECG/IEGM detection model.
 *
 * A fixed post-R or post-T blanking/dead window is intentionally NOT part of this
 * analysis model. Every candidate deflection remains available for inspection.
 * Temporal relationships may influence classification probabilities, but never
 * delete or hide the underlying waveform event.
 */
enum class WaveformEventClass {
    P_WAVE,
    QRS_COMPLEX,
    T_WAVE,
    U_WAVE,
    PAC,
    PVC,
    FUSION_BEAT,
    ESCAPE_BEAT,
    PACED_BEAT,
    CARDIAC_UNCLASSIFIED,
    MYOPOTENTIAL,
    MOTION_ARTIFACT,
    ELECTRODE_CONTACT_ARTIFACT,
    BASELINE_SHIFT,
    POWERLINE_NOISE,
    SATURATION_CLIPPING,
    EXTRACTION_ARTIFACT,
    NON_CARDIAC_OTHER,
    UNKNOWN
}

data class EventProbability(
    val eventClass: WaveformEventClass,
    val probability: Float
)

data class DetectedWaveformEvent(
    val eventIndex: Long,
    val sampleIndex: Long,
    val timeMs: Double,
    val amplitudeMv: Double,
    val leadName: String? = null,
    val onsetTimeMs: Double? = null,
    val offsetTimeMs: Double? = null,
    val primaryClass: WaveformEventClass,
    val classProbabilities: List<EventProbability> = emptyList(),
    val morphologyConfidence: Float? = null,
    val signalQuality: Float? = null,
    val acceptedAsVentricularBeat: Boolean = false,
    val sourceWindowId: String? = null,
    val notes: List<String> = emptyList()
)

/** Exact ventricular beat-to-beat timing. No blanking interval is subtracted. */
data class BeatToBeatInterval(
    val fromBeatIndex: Int,
    val toBeatIndex: Int,
    val fromRPeakTimeMs: Double,
    val toRPeakTimeMs: Double,
    val rrMs: Double,
    val instantaneousRateBpm: Double?,
    val fromClassification: WaveformEventClass,
    val toClassification: WaveformEventClass,
    val intervalConfidence: Float? = null,
    /** Every classified/nonclassified deflection between the accepted R peaks. */
    val interveningEvents: List<DetectedWaveformEvent> = emptyList(),
    /** Always false in this analysis pipeline; retained for explicit auditability. */
    val fixedBlankingApplied: Boolean = false
)

data class BeatWaveTiming(
    val beatIndex: Int,
    val pOnsetMs: Double? = null,
    val pPeakMs: Double? = null,
    val pOffsetMs: Double? = null,
    val qrsOnsetMs: Double? = null,
    val qPeakMs: Double? = null,
    val rPeakMs: Double? = null,
    val sNadirMs: Double? = null,
    val qrsOffsetMs: Double? = null,
    val tOnsetMs: Double? = null,
    val tPeakMs: Double? = null,
    val tOffsetMs: Double? = null,
    val nextPOnsetMs: Double? = null,
    val ppMs: Double? = null,
    val rrMs: Double? = null,
    val prMs: Double? = null,
    val pDurationMs: Double? = null,
    val qrsDurationMs: Double? = null,
    val qtMs: Double? = null,
    val qtcBazettMs: Double? = null,
    val qtcFridericiaMs: Double? = null,
    val stDurationMs: Double? = null,
    val tpIntervalMs: Double? = null,
    val artifactLikelihood: Float? = null,
    val confidence: Float? = null
)

data class ContinuousDetectionPolicy(
    /** Never create a fixed post-R or post-T dead zone in analysis data. */
    val useFixedBlankingWindow: Boolean = false,
    /** Preserve all candidate cardiac and non-cardiac deflections for review. */
    val retainAllCandidates: Boolean = true,
    /** Keep ambiguous candidates instead of forcing them into a cardiac class. */
    val retainAmbiguousCandidates: Boolean = true,
    /** Temporal relationships affect classification probability only. */
    val allowTemporalFeaturesForClassification: Boolean = true,
    /** Threshold controls beat acceptance, not whether a deflection remains visible. */
    val ventricularBeatProbabilityThreshold: Float = 0.50f
)

enum class BodyPosition {
    SUPINE,
    LEFT_LATERAL,
    RIGHT_LATERAL,
    PRONE,
    RECLINED,
    SITTING,
    STANDING,
    UNKNOWN
}

enum class ActivityState {
    ASLEEP,
    LYING_STILL,
    SITTING_STILL,
    STANDING_STILL,
    POSITION_CHANGE,
    WALKING,
    STAIRS,
    EXERCISE,
    DRIVING,
    TALKING,
    COUGHING,
    BENDING,
    OTHER,
    UNKNOWN
}

enum class MotionLevel {
    NONE,
    MINIMAL,
    MODERATE,
    HEAVY,
    UNKNOWN
}

enum class SymptomType {
    NONE,
    PALPITATIONS,
    PRESYNCOPE,
    SYNCOPE,
    DIZZINESS,
    WEAKNESS,
    DYSPNEA,
    CHEST_PAIN,
    VISION_CHANGE,
    NEUROLOGIC_SYMPTOM,
    OTHER,
    UNKNOWN
}

enum class EventTriggerSource {
    DEVICE_AUTOMATIC,
    PATIENT_TRIGGERED,
    CLINICIAN_MARKED,
    IMPORTED_REPORT,
    UNKNOWN
}

/**
 * Context is attached to the exact episode. Missing fields remain UNKNOWN and
 * are surfaced to the user rather than silently inferred.
 */
data class RecordingEventContext(
    val bodyPosition: BodyPosition = BodyPosition.UNKNOWN,
    val activityState: ActivityState = ActivityState.UNKNOWN,
    val motionLevel: MotionLevel = MotionLevel.UNKNOWN,
    val symptoms: List<SymptomType> = emptyList(),
    val symptomNarrative: String = "",
    val userNarrative: String = "",
    val eventTriggerSource: EventTriggerSource = EventTriggerSource.UNKNOWN,
    val wasAwake: Boolean? = null,
    val recordingLocalDateTime: String? = null,
    val contextSource: ValueProvenance = ValueProvenance.REPORTED
) {
    fun missingContextLabels(): List<String> = buildList {
        if (bodyPosition == BodyPosition.UNKNOWN) add("body position")
        if (activityState == ActivityState.UNKNOWN) add("activity state")
        if (motionLevel == MotionLevel.UNKNOWN) add("motion level")
        if (symptoms.isEmpty() || symptoms.all { it == SymptomType.UNKNOWN }) add("symptoms")
        if (eventTriggerSource == EventTriggerSource.UNKNOWN) add("event trigger source")
    }
}

object ContinuousBeatTiming {
    private val ventricularClasses = setOf(
        WaveformEventClass.QRS_COMPLEX,
        WaveformEventClass.PVC,
        WaveformEventClass.FUSION_BEAT,
        WaveformEventClass.ESCAPE_BEAT,
        WaveformEventClass.PACED_BEAT
    )

    /**
     * Creates exact R-to-R intervals between accepted ventricular events while
     * preserving every intervening deflection. No refractory/blanking time is used.
     */
    fun calculateIntervals(events: List<DetectedWaveformEvent>): List<BeatToBeatInterval> {
        if (events.size < 2) return emptyList()

        val ordered = events.sortedBy { it.timeMs }
        val beats = ordered.filter {
            it.acceptedAsVentricularBeat && it.primaryClass in ventricularClasses
        }
        if (beats.size < 2) return emptyList()

        return beats.zipWithNext().mapIndexed { index, pair ->
            val from = pair.first
            val to = pair.second
            val rr = to.timeMs - from.timeMs
            val rate = if (rr > 0.0) 60_000.0 / rr else null
            val between = ordered.filter { it.timeMs > from.timeMs && it.timeMs < to.timeMs }
            BeatToBeatInterval(
                fromBeatIndex = index,
                toBeatIndex = index + 1,
                fromRPeakTimeMs = from.timeMs,
                toRPeakTimeMs = to.timeMs,
                rrMs = round(rr * 1000.0) / 1000.0,
                instantaneousRateBpm = rate?.let { round(it * 100.0) / 100.0 },
                fromClassification = from.primaryClass,
                toClassification = to.primaryClass,
                intervalConfidence = listOfNotNull(from.morphologyConfidence, to.morphologyConfidence)
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toFloat(),
                interveningEvents = between,
                fixedBlankingApplied = false
            )
        }
    }
}
