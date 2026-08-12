package com.example.data

import kotlin.math.sqrt

/**
 * Robust rate summary for accepted ventricular events.
 *
 * Do not average instantaneous bpm values: a handful of very short intervals can
 * inflate that number dramatically. Instead compute the overall ventricular rate
 * from elapsed R-to-R time and report interval distribution separately.
 */
data class VentricularRateSummary(
    val overallRateBpm: Double?,
    val medianRateBpm: Double?,
    val medianRrMs: Double?,
    val shortestRrMs: Double?,
    val longestRrMs: Double?,
    val rrCv: Double?,
    val acceptedBeatCount: Int,
    val suspiciousVeryShortIntervals: Int,
    val notes: List<String>
)

object RobustVentricularRateEstimator {
    fun summarize(trace: LocalWaveformDigitizer.LocalTraceResult): VentricularRateSummary {
        val intervals = trace.intervals.filter { it.rrMs > 0.0 }.sortedBy { it.fromRPeakTimeMs }
        if (intervals.isEmpty()) {
            return VentricularRateSummary(null, null, null, null, null, null, 0, 0,
                listOf("No accepted ventricular RR intervals available."))
        }

        val rr = intervals.map { it.rrMs }
        val sortedRr = rr.sorted()
        val medianRr = median(sortedRr)
        val medianRate = medianRr.takeIf { it > 0.0 }?.let { 60_000.0 / it }

        // This is equivalent to beats over elapsed time and is much more stable
        // than averaging instantaneous 60,000/RR values.
        val elapsed = intervals.last().toRPeakTimeMs - intervals.first().fromRPeakTimeMs
        val overallRate = if (elapsed > 0.0) 60_000.0 * intervals.size / elapsed else null

        val meanRr = rr.average()
        val sd = if (rr.size >= 2) sqrt(rr.map { (it - meanRr) * (it - meanRr) }.average()) else 0.0
        val cv = if (meanRr > 0.0) sd / meanRr else null
        val veryShort = rr.count { it < 100.0 }

        val notes = buildList {
            add("Overall ventricular rate is calculated from accepted beats over elapsed R-to-R time, not from the arithmetic mean of instantaneous bpm values.")
            add("Median RR and shortest RR are shown separately so extremely short intervals cannot silently dominate the displayed rate.")
            if (veryShort > 0) add("$veryShort accepted interval(s) were shorter than 100 ms; inspect source pixels for double counting, oversensing, polymorphic activity, or non-discrete ventricular activity.")
        }

        return VentricularRateSummary(
            overallRateBpm = overallRate,
            medianRateBpm = medianRate,
            medianRrMs = medianRr,
            shortestRrMs = sortedRr.firstOrNull(),
            longestRrMs = sortedRr.lastOrNull(),
            rrCv = cv,
            acceptedBeatCount = intervals.size + 1,
            suspiciousVeryShortIntervals = veryShort,
            notes = notes
        )
    }

    private fun median(sorted: List<Double>): Double {
        if (sorted.isEmpty()) return Double.NaN
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
