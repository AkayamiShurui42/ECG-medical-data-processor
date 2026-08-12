package com.example.data

/**
 * Multi-scale signal inspection keeps the same calibrated waveform samples at every
 * zoom level. Zoom is analytical context, not image interpolation or a substitute trace.
 */
enum class InspectionScale {
    WHOLE_RECORDING,
    WHOLE_EPISODE,
    SIXTEEN_BEATS,
    EIGHT_BEATS,
    FOUR_BEATS,
    TWO_BEATS,
    SINGLE_BEAT,
    WAVE_COMPONENT
}

enum class CrossScaleAgreement {
    AGREES,
    PARTIALLY_AGREES,
    DISAGREES,
    INSUFFICIENT_DATA
}

data class InspectionWindow(
    val id: String,
    val scale: InspectionScale,
    val startTimeMs: Double,
    val endTimeMs: Double,
    val leadName: String? = null,
    val beatIndices: List<Int> = emptyList(),
    val samples: List<WaveformSample>,
    val events: List<DetectedWaveformEvent> = emptyList(),
    val calibration: CalibrationInfo,
    val sourceUri: String,
    val sourcePage: Int? = null,
    val sourceVideoStartMs: Long? = null,
    val sourceVideoEndMs: Long? = null
)

data class SignalQualityFeature(
    val name: String,
    val value: Double?,
    val unit: String? = null,
    val explanation: String,
    val supportsCardiacOrigin: Boolean? = null
)

data class MorphologyFingerprint(
    val qrsDurationMs: Double? = null,
    val peakToPeakAmplitudeMv: Double? = null,
    val areaMvMs: Double? = null,
    val maxUpstrokeMvPerSec: Double? = null,
    val maxDownstrokeMvPerSec: Double? = null,
    val correlationToDominantBeat: Double? = null,
    val correlationToAdjacentBeat: Double? = null,
    val polarity: String? = null,
    val morphologyClusterId: Int? = null
)

data class CardiacOriginAssessment(
    val eventIndex: Long,
    val probabilityCardiac: Float,
    val probabilityNonCardiac: Float,
    val likelyClass: WaveformEventClass,
    val morphology: MorphologyFingerprint,
    val qualityFeatures: List<SignalQualityFeature>,
    val reasonsForCardiac: List<String>,
    val reasonsForNonCardiac: List<String>,
    val unresolvedQuestions: List<String>
)

data class ScaleSpecificInterpretation(
    val windowId: String,
    val scale: InspectionScale,
    val rhythmCandidates: List<RhythmDifferentialCandidate>,
    val eventAssessments: List<CardiacOriginAssessment>,
    val artifactAssessments: List<ArtifactAssessment>,
    val notes: List<String> = emptyList()
)

data class CrossScaleComparison(
    val broadWindowId: String,
    val narrowWindowId: String,
    val agreement: CrossScaleAgreement,
    val stableFindings: List<String>,
    val findingsOnlyVisibleWhenZoomed: List<String>,
    val findingsLostWhenZoomedOut: List<String>,
    val contradictions: List<String>
)

data class ZoomAuditTrail(
    val sourceUri: String,
    val windows: List<InspectionWindow>,
    val interpretations: List<ScaleSpecificInterpretation>,
    val comparisons: List<CrossScaleComparison>,
    val selectedWindowId: String? = null
)

object InspectionWindowFactory {
    /**
     * Builds a sample window directly from calibrated samples. No resynthesis occurs.
     */
    fun timeWindow(
        id: String,
        scale: InspectionScale,
        source: WaveformWindow,
        startTimeMs: Double,
        endTimeMs: Double,
        events: List<DetectedWaveformEvent> = emptyList(),
        beatIndices: List<Int> = emptyList()
    ): InspectionWindow {
        val selectedSamples = source.samples.filter { it.timeMs in startTimeMs..endTimeMs }
        val selectedEvents = events.filter { it.timeMs in startTimeMs..endTimeMs }
        return InspectionWindow(
            id = id,
            scale = scale,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            leadName = source.leadName,
            beatIndices = beatIndices,
            samples = selectedSamples,
            events = selectedEvents,
            calibration = source.calibration,
            sourceUri = source.sourceUri,
            sourcePage = source.sourcePage,
            sourceVideoStartMs = source.sourceVideoStartMs,
            sourceVideoEndMs = source.sourceVideoEndMs
        )
    }

    /**
     * Creates a tight one- or two-beat window from accepted ventricular events.
     * The margin is contextual only; it does not blank or discard intervening deflections.
     */
    fun aroundBeats(
        id: String,
        source: WaveformWindow,
        acceptedBeats: List<DetectedWaveformEvent>,
        firstBeatIndex: Int,
        beatCount: Int,
        allEvents: List<DetectedWaveformEvent>,
        marginMs: Double = 250.0
    ): InspectionWindow? {
        if (beatCount <= 0 || firstBeatIndex !in acceptedBeats.indices) return null
        val lastIndex = (firstBeatIndex + beatCount - 1).coerceAtMost(acceptedBeats.lastIndex)
        val first = acceptedBeats[firstBeatIndex]
        val last = acceptedBeats[lastIndex]
        val start = (first.onsetTimeMs ?: first.timeMs) - marginMs
        val end = (last.offsetTimeMs ?: last.timeMs) + marginMs
        val scale = when (beatCount) {
            1 -> InspectionScale.SINGLE_BEAT
            2 -> InspectionScale.TWO_BEATS
            in 3..4 -> InspectionScale.FOUR_BEATS
            in 5..8 -> InspectionScale.EIGHT_BEATS
            else -> InspectionScale.SIXTEEN_BEATS
        }
        return timeWindow(
            id = id,
            scale = scale,
            source = source,
            startTimeMs = start.coerceAtLeast(source.startTimeMs),
            endTimeMs = end.coerceAtMost(source.endTimeMs),
            events = allEvents,
            beatIndices = (firstBeatIndex..lastIndex).toList()
        )
    }
}
