package com.example.data

import kotlin.math.abs

/**
 * Ventricular-event acceptance gate.
 *
 * This is NOT a waveform blanking window. Every candidate deflection remains in
 * the event list for waveform, T-wave, artifact, and oversensing review. The gate
 * only decides which candidates may simultaneously contribute to RR/rate metrics.
 */
object PhysiologicBeatAcceptanceGate {

    data class Policy(
        /**
         * Very permissive default: 100 ms ~= 600 bpm.
         * This is an analysis safety/duplicate-sensing floor, not a claim that
         * 600 bpm is a universal biological ventricular-rate limit.
         * Set to 0 to disable.
         */
        val minimumAcceptedRrMs: Double = 100.0,
        val preserveRejectedCandidates: Boolean = true
    ) {
        val nominalMaximumCountedRateBpm: Double?
            get() = minimumAcceptedRrMs.takeIf { it > 0.0 }?.let { 60_000.0 / it }
    }

    data class GateResult(
        val trace: LocalWaveformDigitizer.LocalTraceResult,
        val gatedCandidateCount: Int,
        val policy: Policy
    )

    fun apply(
        input: LocalWaveformDigitizer.LocalTraceResult,
        policy: Policy = Policy()
    ): GateResult {
        if (policy.minimumAcceptedRrMs <= 0.0) return GateResult(input, 0, policy)

        val events = input.events.sortedBy { it.timeMs }
        val candidateIndices = events.indices.filter { events[it].acceptedAsVentricularBeat }
        if (candidateIndices.size < 2) return GateResult(input, 0, policy)

        /*
         * Select strongest mutually compatible candidates. Sorting by strength
         * first and then admitting a candidate only when it is sufficiently far
         * from every already-selected candidate guarantees that no surviving pair
         * violates the requested minimum separation. The prior cluster cursor
         * implementation could allow a close pair to escape after the cluster
         * winner changed.
         */
        val selected = mutableListOf<Int>()
        candidateIndices
            .sortedWith(compareByDescending<Int> { beatStrength(events[it]) }.thenBy { events[it].timeMs })
            .forEach { index ->
                val time = events[index].timeMs
                if (selected.none { chosen -> abs(events[chosen].timeMs - time) < policy.minimumAcceptedRrMs }) {
                    selected += index
                }
            }

        val selectedSet = selected.toSet()
        val demote = candidateIndices.filter { it !in selectedSet }.toSet()
        if (demote.isEmpty()) return GateResult(input, 0, policy)

        val updated = events.mapIndexed { index, event ->
            if (index !in demote) event
            else event.copy(
                acceptedAsVentricularBeat = false,
                notes = event.notes + listOf(
                    "Candidate retained for review but excluded from ventricular RR counting by ${policy.minimumAcceptedRrMs.toInt()} ms duplicate/physiologic acceptance floor"
                )
            )
        }
        val intervals = ContinuousBeatTiming.calculateIntervals(updated)
        val note = "Ventricular acceptance floor: ${policy.minimumAcceptedRrMs.toInt()} ms minimum counted RR (${policy.nominalMaximumCountedRateBpm?.let { "%.1f".format(it) } ?: "disabled"} bpm mathematical ceiling). This is not waveform blanking and is not presented as a universal biological limit; rejected candidates remain visible."

        return GateResult(
            input.copy(events = updated, intervals = intervals, notes = input.notes + note),
            demote.size,
            policy
        )
    }

    private fun beatStrength(event: DetectedWaveformEvent): Double {
        val qrs = event.classProbabilities.firstOrNull { it.eventClass == WaveformEventClass.QRS_COMPLEX }?.probability?.toDouble()
            ?: event.morphologyConfidence?.toDouble()
            ?: 0.0
        val quality = event.signalQuality?.toDouble() ?: 0.5
        val amplitude = abs(event.amplitudeMv).coerceAtMost(5.0) / 5.0
        return qrs * 0.60 + quality * 0.25 + amplitude * 0.15
    }
}
