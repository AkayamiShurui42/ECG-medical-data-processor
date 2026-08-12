package com.example.data

import kotlin.math.abs

/**
 * Physiologic ventricular-acceptance gate.
 *
 * This is intentionally NOT a waveform blanking window. All candidate deflections
 * remain in the event list and stay visible for T-wave/artifact review. The gate
 * only prevents two implausibly close candidates from both being counted as
 * accepted ventricular depolarizations for RR/rate calculations.
 */
object PhysiologicBeatAcceptanceGate {

    data class Policy(
        /** 180 ms ~= 333 bpm. Set to 0 to disable the acceptance gate. */
        val minimumAcceptedRrMs: Double = 180.0,
        /** Keep every rejected candidate in the event stream. */
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
        if (policy.minimumAcceptedRrMs <= 0.0) {
            return GateResult(input, 0, policy)
        }

        val events = input.events.sortedBy { it.timeMs }.toMutableList()
        val acceptedIndices = events.indices.filter { events[it].acceptedAsVentricularBeat }.toMutableList()
        if (acceptedIndices.size < 2) return GateResult(input, 0, policy)

        val demote = mutableSetOf<Int>()
        var cursor = 0
        while (cursor < acceptedIndices.size - 1) {
            val firstIndex = acceptedIndices[cursor]
            var nextCursor = cursor + 1
            var winnerIndex = firstIndex
            var clusterEndTime = events[firstIndex].timeMs + policy.minimumAcceptedRrMs

            while (nextCursor < acceptedIndices.size) {
                val candidateIndex = acceptedIndices[nextCursor]
                val candidate = events[candidateIndex]
                if (candidate.timeMs >= clusterEndTime) break

                val winner = events[winnerIndex]
                if (beatStrength(candidate) > beatStrength(winner)) {
                    demote += winnerIndex
                    winnerIndex = candidateIndex
                    clusterEndTime = candidate.timeMs + policy.minimumAcceptedRrMs
                } else {
                    demote += candidateIndex
                }
                nextCursor++
            }
            cursor = if (nextCursor == cursor + 1) cursor + 1 else nextCursor
        }

        if (demote.isEmpty()) return GateResult(input, 0, policy)

        val updated = events.mapIndexed { index, event ->
            if (index !in demote) event
            else event.copy(
                acceptedAsVentricularBeat = false,
                notes = event.notes + listOf(
                    "Retained candidate but excluded from ventricular RR counting by ${policy.minimumAcceptedRrMs.toInt()} ms physiologic acceptance gate"
                )
            )
        }
        val intervals = ContinuousBeatTiming.calculateIntervals(updated)
        val note = "Physiologic ventricular acceptance gate: ${policy.minimumAcceptedRrMs.toInt()} ms minimum counted RR (${policy.nominalMaximumCountedRateBpm?.let { "%.1f".format(it) } ?: "disabled"} bpm nominal ceiling). Candidate deflections were preserved."

        return GateResult(
            input.copy(
                events = updated,
                intervals = intervals,
                notes = input.notes + note
            ),
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
