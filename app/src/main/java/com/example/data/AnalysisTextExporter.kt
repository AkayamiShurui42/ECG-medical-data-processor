package com.example.data

import java.util.Locale

/** Plain-text export of the measured analysis for clipboard/share workflows. */
object AnalysisTextExporter {

    fun build(
        result: LocalWaveformDigitizer.LocalTraceResult,
        modality: RecordingModality,
        lead: LeadConfiguration,
        calibration: AutoCalibrationDetector.DetectionResult?,
        context: RecordingEventContext,
        visualCandidates: List<RhythmVisualCandidate>,
        gatePolicy: PhysiologicBeatAcceptanceGate.Policy,
        sourceLabel: String? = null
    ): String {
        val rr = result.intervals.map { it.rrMs }
        val rates = result.intervals.mapNotNull { it.instantaneousRateBpm }
        val samples = result.waveformWindow.samples
        val accepted = result.events.count { it.acceptedAsVentricularBeat }
        val artifacts = result.events.count {
            it.primaryClass in setOf(
                WaveformEventClass.MOTION_ARTIFACT,
                WaveformEventClass.ELECTRODE_CONTACT_ARTIFACT,
                WaveformEventClass.EXTRACTION_ARTIFACT,
                WaveformEventClass.MYOPOTENTIAL,
                WaveformEventClass.POWERLINE_NOISE,
                WaveformEventClass.SATURATION_CLIPPING
            )
        }

        return buildString {
            appendLine("ECG / IEGM SIGNAL WORKBENCH INTERPRETATION")
            sourceLabel?.let { appendLine("Source: $it") }
            appendLine("Modality: ${pretty(modality.name)}")
            appendLine("Lead configuration: ${pretty(lead.name)}")
            appendLine()

            appendLine("CALIBRATION")
            appendLine("Time: ${calibration?.timeReferenceSource ?: "not available"}")
            appendLine("Amplitude: ${calibration?.amplitudeReferenceSource ?: "not available"}")
            appendLine("Trace region: ${calibration?.traceRegionSourcePx?.let { "x ${it.left}-${it.right}, y ${it.top}-${it.bottom} px" } ?: "not available"}")
            appendLine()

            appendLine("VENTRICULAR ACCEPTANCE POLICY")
            appendLine("Minimum counted RR: ${fmt(gatePolicy.minimumAcceptedRrMs)} ms")
            appendLine("Nominal counted-rate ceiling: ${gatePolicy.nominalMaximumCountedRateBpm?.let { fmt(it) + " bpm" } ?: "disabled"}")
            appendLine("Candidate deflections remain preserved even when excluded from RR counting.")
            appendLine()

            appendLine("MEASURED SUMMARY")
            appendLine("Waveform samples: ${samples.size}")
            appendLine("Candidate deflections: ${result.events.size}")
            appendLine("Accepted ventricular beats: $accepted")
            appendLine("Artifact-class candidates: $artifacts")
            appendLine("RR intervals: ${result.intervals.size}")
            if (rr.isNotEmpty()) {
                appendLine("RR range: ${fmt(rr.min())}-${fmt(rr.max())} ms")
                appendLine("RR mean: ${fmt(rr.average())} ms")
            }
            if (rates.isNotEmpty()) {
                appendLine("Rate range: ${fmt(rates.min())}-${fmt(rates.max())} bpm")
                appendLine("Mean rate: ${fmt(rates.average())} bpm")
            }
            if (samples.isNotEmpty()) {
                appendLine("Measured amplitude range: ${fmt(samples.minOf { it.amplitudeMv })} to ${fmt(samples.maxOf { it.amplitudeMv })} mV")
            }
            appendLine()

            appendLine("BEAT-TO-BEAT RR")
            if (result.intervals.isEmpty()) appendLine("No accepted RR intervals.")
            result.intervals.forEachIndexed { index, interval ->
                appendLine("${index + 1}. ${fmt(interval.fromRPeakTimeMs)} -> ${fmt(interval.toRPeakTimeMs)} ms | RR ${fmt(interval.rrMs)} ms | ${interval.instantaneousRateBpm?.let { fmt(it) + " bpm" } ?: "rate n/a"}")
            }
            appendLine()

            appendLine("RECORDING CONTEXT")
            appendLine("Position: ${pretty(context.bodyPosition.name)}")
            appendLine("Activity: ${pretty(context.activityState.name)}")
            appendLine("Motion: ${pretty(context.motionLevel.name)}")
            appendLine("Symptoms: ${if (context.symptoms.isEmpty()) "not supplied" else context.symptoms.joinToString { pretty(it.name) }}")
            if (context.symptomNarrative.isNotBlank()) appendLine("Symptom notes: ${context.symptomNarrative}")
            if (context.userNarrative.isNotBlank()) appendLine("Event notes: ${context.userNarrative}")
            val missing = context.missingContextLabels()
            if (missing.isNotEmpty()) appendLine("Missing context: ${missing.joinToString()}")
            appendLine()

            appendLine("RHYTHM DIFFERENTIAL - METRIC COMPATIBILITY")
            visualCandidates.take(20).forEachIndexed { index, candidate ->
                appendLine("${index + 1}. ${candidate.displayName}: ${"%.0f".format(Locale.US, candidate.metricCompatibility * 100)}% metric compatibility")
                appendLine("   ${candidate.metricSummary}")
            }
            appendLine()
            appendLine("Interpretation aid only: visual resemblance and metric compatibility are displayed separately and neither is diagnostic proof by itself.")
        }
    }

    private fun fmt(v: Double): String = "%.3f".format(Locale.US, v)
    private fun pretty(s: String): String = s.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.US) }
}
