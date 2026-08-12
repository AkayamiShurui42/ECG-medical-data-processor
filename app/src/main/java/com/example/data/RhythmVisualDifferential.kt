package com.example.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Complete visual rhythm differential.
 *
 * Every catalogued RhythmMechanism receives a synthetic educational comparison
 * strip. Examples are generated at the observed ventricular rate whenever that
 * is physiologically sensible. These strips are comparison aids only and are
 * never substituted for the patient's extracted waveform.
 */
data class RhythmVisualCandidate(
    val mechanism: RhythmMechanism,
    val displayName: String,
    val metricCompatibility: Float,
    val metricSummary: String,
    val visualCaveat: String,
    val waveform: List<EcgWavePoint>
)

object RhythmVisualDifferential {
    private const val SAMPLE_COUNT = 1200
    private const val DURATION_SEC = 8.0

    fun build(
        trace: LocalWaveformDigitizer.LocalTraceResult,
        modality: RecordingModality
    ): List<RhythmVisualCandidate> {
        val rates = trace.intervals.mapNotNull { it.instantaneousRateBpm }
        val observedRate = rates.takeIf { it.isNotEmpty() }?.average() ?: 75.0
        val rr = trace.intervals.map { it.rrMs }
        val rrCv = if (rr.size >= 2) {
            val mean = rr.average()
            val sd = sqrt(rr.map { (it - mean) * (it - mean) }.average())
            if (mean > 0.0) sd / mean else 0.0
        } else 0.0

        return RhythmMechanism.entries.map { mechanism ->
            RhythmVisualCandidate(
                mechanism = mechanism,
                displayName = RhythmMechanismCatalog.displayNames[mechanism]
                    ?: mechanism.name.replace('_', ' '),
                metricCompatibility = metricScore(mechanism, observedRate, rrCv, modality),
                metricSummary = metricExplanation(mechanism, observedRate, rrCv),
                visualCaveat = modalityCaveat(modality),
                waveform = generate(mechanism, observedRate)
            )
        }.sortedWith(
            compareByDescending<RhythmVisualCandidate> { it.metricCompatibility }
                .thenBy { it.displayName }
        )
    }

    private fun metricScore(
        mechanism: RhythmMechanism,
        rate: Double,
        rrCv: Double,
        modality: RecordingModality
    ): Float {
        val tachy = rate >= 100.0
        val brady = rate < 60.0
        val irregular = rrCv >= 0.06

        val score = when (mechanism) {
            RhythmMechanism.NORMAL_SINUS_RHYTHM -> if (rate in 60.0..100.0 && !irregular) .90 else .30
            RhythmMechanism.SINUS_BRADYCARDIA -> if (brady && !irregular) .92 else .24
            RhythmMechanism.SINUS_TACHYCARDIA -> if (tachy && !irregular) .92 else .28
            RhythmMechanism.SINUS_ARRHYTHMIA -> if (!tachy && irregular) .78 else .36
            RhythmMechanism.SINUS_PAUSE_OR_ARREST,
            RhythmMechanism.SINOATRIAL_EXIT_BLOCK -> if (brady || rrCv > .14) .72 else .30

            RhythmMechanism.PREMATURE_ATRIAL_COMPLEX,
            RhythmMechanism.ATRIAL_BIGEMINY,
            RhythmMechanism.ATRIAL_TRIGEMINY,
            RhythmMechanism.PREMATURE_VENTRICULAR_COMPLEX,
            RhythmMechanism.VENTRICULAR_BIGEMINY,
            RhythmMechanism.VENTRICULAR_TRIGEMINY,
            RhythmMechanism.COUPLET,
            RhythmMechanism.TRIPLET -> if (rrCv > .035) .70 else .43

            RhythmMechanism.FOCAL_ATRIAL_TACHYCARDIA,
            RhythmMechanism.AVNRT,
            RhythmMechanism.ORTHODROMIC_AVRT,
            RhythmMechanism.PERMANENT_JUNCTIONAL_RECIPROCATING_TACHYCARDIA,
            RhythmMechanism.UNCLASSIFIED_REGULAR_NARROW_COMPLEX_SVT,
            RhythmMechanism.JUNCTIONAL_TACHYCARDIA -> if (tachy && !irregular) .84 else .31

            RhythmMechanism.MULTIFOCAL_ATRIAL_TACHYCARDIA -> if (tachy && irregular) .84 else .27
            RhythmMechanism.ATRIAL_FIBRILLATION,
            RhythmMechanism.UNCLASSIFIED_IRREGULAR_NARROW_COMPLEX_TACHYCARDIA -> if (irregular) .91 else .21
            RhythmMechanism.TYPICAL_ATRIAL_FLUTTER,
            RhythmMechanism.ATYPICAL_ATRIAL_FLUTTER -> if (tachy) .70 else .45

            RhythmMechanism.ANTIDROMIC_AVRT,
            RhythmMechanism.MONOMORPHIC_VENTRICULAR_TACHYCARDIA,
            RhythmMechanism.SUPRAVENTRICULAR_TACHYCARDIA_WITH_ABERRANCY -> if (tachy && !irregular) .73 else .29
            RhythmMechanism.POLYMORPHIC_VENTRICULAR_TACHYCARDIA,
            RhythmMechanism.TORSADES_DE_POINTES -> if (tachy) .64 else .17
            RhythmMechanism.VENTRICULAR_FLUTTER,
            RhythmMechanism.VENTRICULAR_FIBRILLATION -> if (rate >= 150.0) .58 else .11

            RhythmMechanism.JUNCTIONAL_ESCAPE_RHYTHM,
            RhythmMechanism.IDIOVENTRICULAR_RHYTHM -> if (brady) .76 else .22
            RhythmMechanism.ACCELERATED_JUNCTIONAL_RHYTHM,
            RhythmMechanism.ACCELERATED_IDIOVENTRICULAR_RHYTHM -> if (rate in 50.0..120.0) .66 else .25

            RhythmMechanism.FIRST_DEGREE_AV_BLOCK -> if (!tachy) .52 else .32
            RhythmMechanism.SECOND_DEGREE_AV_BLOCK_MOBITZ_I,
            RhythmMechanism.SECOND_DEGREE_AV_BLOCK_MOBITZ_II,
            RhythmMechanism.TWO_TO_ONE_AV_BLOCK,
            RhythmMechanism.HIGH_GRADE_AV_BLOCK,
            RhythmMechanism.COMPLETE_AV_BLOCK -> if (brady || rrCv > .08) .71 else .27

            RhythmMechanism.PREEXCITATION_PATTERN,
            RhythmMechanism.BUNDLE_BRANCH_BLOCK_PATTERN,
            RhythmMechanism.RATE_RELATED_BUNDLE_BRANCH_ABERRANCY -> .49
            RhythmMechanism.PREEXCITED_ATRIAL_FIBRILLATION -> if (tachy && irregular) .82 else .23

            RhythmMechanism.PACED_ATRIAL_RHYTHM,
            RhythmMechanism.PACED_VENTRICULAR_RHYTHM,
            RhythmMechanism.DUAL_CHAMBER_PACING,
            RhythmMechanism.PACEMAKER_UNDERSENSING,
            RhythmMechanism.PACEMAKER_OVERSENSING,
            RhythmMechanism.FAILURE_TO_CAPTURE -> if (modality == RecordingModality.IMPLANTABLE_LOOP_RECORDER) .31 else .42

            RhythmMechanism.ARTIFACT_MIMICKING_TACHYCARDIA,
            RhythmMechanism.OVERSENSING_MIMICKING_TACHYCARDIA -> if (tachy || rrCv > .12) .68 else .38
            RhythmMechanism.ARTIFACT_MIMICKING_BRADYCARDIA_OR_PAUSE,
            RhythmMechanism.UNDERSENSING_MIMICKING_PAUSE -> if (brady || rrCv > .10) .75 else .34
            RhythmMechanism.UNCLASSIFIED_CARDIAC_RHYTHM -> .50
        }
        return score.toFloat().coerceIn(0f, 1f)
    }

    private fun metricExplanation(mechanism: RhythmMechanism, rate: Double, rrCv: Double): String {
        val regularity = when {
            rrCv < .03 -> "very regular"
            rrCv < .06 -> "fairly regular"
            rrCv < .12 -> "moderately irregular"
            else -> "markedly irregular"
        }
        val extra = when (mechanism) {
            RhythmMechanism.ATRIAL_FIBRILLATION -> "AF still requires exclusion of ectopy, sensing dropout, and artifact as explanations for irregularity."
            RhythmMechanism.AVNRT, RhythmMechanism.ORTHODROMIC_AVRT -> "Atrial timing/RP behavior is needed to separate these re-entrant mechanisms."
            RhythmMechanism.SINUS_TACHYCARDIA -> "Organized sinus atrial activity and gradual onset/offset remain important discriminators."
            RhythmMechanism.MONOMORPHIC_VENTRICULAR_TACHYCARDIA -> "QRS width/morphology and AV relationship are required before calling VT."
            RhythmMechanism.UNDERSENSING_MIMICKING_PAUSE -> "Look for QRS-like morphology continuing through an apparent device pause."
            else -> "P/QRS/T morphology and source-pixel review determine whether this remains compatible."
        }
        return "Observed rate ${"%.1f".format(rate)} bpm; RR pattern $regularity (CV ${"%.3f".format(rrCv)}). $extra"
    }

    private fun modalityCaveat(modality: RecordingModality): String = when (modality) {
        RecordingModality.IMPLANTABLE_LOOP_RECORDER -> "Generated as a single-vector comparison. P/T visibility and amplitude can differ substantially with implant vector and sensing geometry."
        RecordingModality.HOLTER -> "Generated as a representative rhythm strip; longer Holter context should be used to verify onset, termination, burden, and morphology stability."
        RecordingModality.DIAGNOSTIC_ECG -> "Generated as a generic single-strip morphology aid; a true 12-lead diagnosis also depends on lead-to-lead spatial information."
        else -> "Synthetic comparison only; match morphology and measured intervals separately."
    }

    fun generate(mechanism: RhythmMechanism, observedRate: Double): List<EcgWavePoint> {
        val rate = observedRate.coerceIn(25.0, 260.0)
        return when (mechanism) {
            RhythmMechanism.NORMAL_SINUS_RHYTHM -> regular(rate.coerceIn(60.0, 100.0), pWaves = true)
            RhythmMechanism.SINUS_BRADYCARDIA -> regular(rate.coerceIn(30.0, 59.0), pWaves = true)
            RhythmMechanism.SINUS_TACHYCARDIA -> regular(rate.coerceAtLeast(100.0), pWaves = true)
            RhythmMechanism.SINUS_ARRHYTHMIA -> irregularPattern(rate.coerceIn(45.0, 100.0), pWaves = true, variation = .16)
            RhythmMechanism.SINUS_PAUSE_OR_ARREST -> paused(rate.coerceIn(50.0, 90.0), droppedEvery = 5, pWaves = true)
            RhythmMechanism.SINOATRIAL_EXIT_BLOCK -> paused(rate.coerceIn(50.0, 90.0), droppedEvery = 4, pWaves = true)

            RhythmMechanism.PREMATURE_ATRIAL_COMPLEX -> ectopyPattern(rate, ventricular = false, pattern = 0)
            RhythmMechanism.ATRIAL_BIGEMINY -> ectopyPattern(rate, ventricular = false, pattern = 2)
            RhythmMechanism.ATRIAL_TRIGEMINY -> ectopyPattern(rate, ventricular = false, pattern = 3)
            RhythmMechanism.FOCAL_ATRIAL_TACHYCARDIA -> regular(rate.coerceAtLeast(110.0), pWaves = true, pOffset = .09)
            RhythmMechanism.MULTIFOCAL_ATRIAL_TACHYCARDIA -> irregularPattern(rate.coerceAtLeast(100.0), pWaves = true, variation = .20, variableP = true)
            RhythmMechanism.ATRIAL_FIBRILLATION -> atrialFibrillation(rate.coerceAtLeast(70.0))
            RhythmMechanism.TYPICAL_ATRIAL_FLUTTER -> atrialFlutter(rate, atypical = false)
            RhythmMechanism.ATYPICAL_ATRIAL_FLUTTER -> atrialFlutter(rate, atypical = true)

            RhythmMechanism.AVNRT -> regular(rate.coerceAtLeast(140.0), pWaves = false, retroP = true)
            RhythmMechanism.ORTHODROMIC_AVRT -> regular(rate.coerceAtLeast(140.0), pWaves = false, retroP = true, retroPDelay = .07)
            RhythmMechanism.ANTIDROMIC_AVRT -> regular(rate.coerceAtLeast(140.0), pWaves = false, wide = true, retroP = true)
            RhythmMechanism.PERMANENT_JUNCTIONAL_RECIPROCATING_TACHYCARDIA -> regular(rate.coerceAtLeast(110.0), pWaves = false, retroP = true, retroPDelay = .19)
            RhythmMechanism.UNCLASSIFIED_REGULAR_NARROW_COMPLEX_SVT -> regular(rate.coerceAtLeast(130.0), pWaves = false)
            RhythmMechanism.UNCLASSIFIED_IRREGULAR_NARROW_COMPLEX_TACHYCARDIA -> irregularPattern(rate.coerceAtLeast(110.0), pWaves = false, variation = .22)

            RhythmMechanism.JUNCTIONAL_ESCAPE_RHYTHM -> regular(rate.coerceIn(35.0, 60.0), pWaves = false, retroP = true)
            RhythmMechanism.ACCELERATED_JUNCTIONAL_RHYTHM -> regular(rate.coerceIn(60.0, 100.0), pWaves = false, retroP = true)
            RhythmMechanism.JUNCTIONAL_TACHYCARDIA -> regular(rate.coerceAtLeast(100.0), pWaves = false, retroP = true)

            RhythmMechanism.PREMATURE_VENTRICULAR_COMPLEX -> ectopyPattern(rate, ventricular = true, pattern = 0)
            RhythmMechanism.VENTRICULAR_BIGEMINY -> ectopyPattern(rate, ventricular = true, pattern = 2)
            RhythmMechanism.VENTRICULAR_TRIGEMINY -> ectopyPattern(rate, ventricular = true, pattern = 3)
            RhythmMechanism.COUPLET -> ventricularRuns(rate, runLength = 2)
            RhythmMechanism.TRIPLET -> ventricularRuns(rate, runLength = 3)
            RhythmMechanism.IDIOVENTRICULAR_RHYTHM -> regular(rate.coerceIn(20.0, 50.0), pWaves = false, wide = true)
            RhythmMechanism.ACCELERATED_IDIOVENTRICULAR_RHYTHM -> regular(rate.coerceIn(50.0, 110.0), pWaves = false, wide = true)
            RhythmMechanism.MONOMORPHIC_VENTRICULAR_TACHYCARDIA -> regular(rate.coerceAtLeast(120.0), pWaves = false, wide = true)
            RhythmMechanism.POLYMORPHIC_VENTRICULAR_TACHYCARDIA -> polymorphic(rate.coerceAtLeast(140.0), torsades = false)
            RhythmMechanism.TORSADES_DE_POINTES -> polymorphic(rate.coerceAtLeast(160.0), torsades = true)
            RhythmMechanism.VENTRICULAR_FLUTTER -> sineTachy(rate.coerceAtLeast(220.0), amplitude = 1.0)
            RhythmMechanism.VENTRICULAR_FIBRILLATION -> ventricularFibrillation()

            RhythmMechanism.FIRST_DEGREE_AV_BLOCK -> regular(rate.coerceIn(45.0, 95.0), pWaves = true, pOffset = .24)
            RhythmMechanism.SECOND_DEGREE_AV_BLOCK_MOBITZ_I -> wenckebach(rate.coerceIn(45.0, 90.0), mobitz2 = false)
            RhythmMechanism.SECOND_DEGREE_AV_BLOCK_MOBITZ_II -> wenckebach(rate.coerceIn(40.0, 80.0), mobitz2 = true)
            RhythmMechanism.TWO_TO_ONE_AV_BLOCK -> twoToOne(rate.coerceIn(30.0, 80.0))
            RhythmMechanism.HIGH_GRADE_AV_BLOCK -> highGradeBlock(rate.coerceIn(25.0, 60.0))
            RhythmMechanism.COMPLETE_AV_BLOCK -> completeBlock(rate.coerceIn(25.0, 50.0))

            RhythmMechanism.PREEXCITATION_PATTERN -> regular(rate.coerceIn(55.0, 100.0), pWaves = true, delta = true)
            RhythmMechanism.PREEXCITED_ATRIAL_FIBRILLATION -> preexcitedAf(rate.coerceAtLeast(120.0))

            RhythmMechanism.PACED_ATRIAL_RHYTHM -> paced(rate, atrial = true, ventricular = false)
            RhythmMechanism.PACED_VENTRICULAR_RHYTHM -> paced(rate, atrial = false, ventricular = true)
            RhythmMechanism.DUAL_CHAMBER_PACING -> paced(rate, atrial = true, ventricular = true)
            RhythmMechanism.PACEMAKER_UNDERSENSING -> pacingFailure(rate, oversensing = false, captureFailure = false)
            RhythmMechanism.PACEMAKER_OVERSENSING -> pacingFailure(rate, oversensing = true, captureFailure = false)
            RhythmMechanism.FAILURE_TO_CAPTURE -> pacingFailure(rate, oversensing = false, captureFailure = true)

            RhythmMechanism.SUPRAVENTRICULAR_TACHYCARDIA_WITH_ABERRANCY -> regular(rate.coerceAtLeast(120.0), pWaves = false, wide = true)
            RhythmMechanism.RATE_RELATED_BUNDLE_BRANCH_ABERRANCY -> regular(rate.coerceAtLeast(100.0), pWaves = true, wide = true)
            RhythmMechanism.BUNDLE_BRANCH_BLOCK_PATTERN -> regular(rate.coerceIn(50.0, 110.0), pWaves = true, wide = true)

            RhythmMechanism.ARTIFACT_MIMICKING_TACHYCARDIA -> artifactTachy(rate)
            RhythmMechanism.ARTIFACT_MIMICKING_BRADYCARDIA_OR_PAUSE -> artifactPause(rate)
            RhythmMechanism.UNDERSENSING_MIMICKING_PAUSE -> undersensingMimic(rate)
            RhythmMechanism.OVERSENSING_MIMICKING_TACHYCARDIA -> oversensingMimic(rate)

            RhythmMechanism.UNCLASSIFIED_CARDIAC_RHYTHM -> regular(rate, pWaves = false)
        }
    }

    private fun gaussian(t: Double, center: Double, width: Double, amp: Double): Double {
        val z = (t - center) / width
        return amp * exp(-.5 * z * z)
    }

    private fun beat(
        t: Double,
        center: Double,
        pWaves: Boolean,
        wide: Boolean = false,
        pOffset: Double = .12,
        retroP: Boolean = false,
        retroPDelay: Double = .04,
        delta: Boolean = false,
        scale: Double = 1.0,
        polarity: Double = 1.0
    ): Double {
        val qrsW = if (wide) .022 else .008
        var y = 0.0
        if (pWaves) y += gaussian(t, center - pOffset, .018, .12 * polarity)
        if (delta) y += gaussian(t, center - .035, .025, .20 * polarity)
        y += gaussian(t, center - .018, qrsW, -.18 * scale * polarity)
        y += gaussian(t, center, qrsW, 1.00 * scale * polarity)
        y += gaussian(t, center + .023, qrsW * 1.3, -.32 * scale * polarity)
        y += gaussian(t, center + .20, .047, .27 * polarity)
        if (retroP) y += gaussian(t, center + retroPDelay, .014, -.08 * polarity)
        return y
    }

    private fun regular(
        rate: Double,
        pWaves: Boolean,
        wide: Boolean = false,
        pOffset: Double = .12,
        retroP: Boolean = false,
        retroPDelay: Double = .04,
        delta: Boolean = false
    ): List<EcgWavePoint> {
        val rr = 60.0 / rate.coerceAtLeast(1.0)
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            var b = .45
            while (b < DURATION_SEC + .4) {
                y += beat(t, b, pWaves, wide, pOffset, retroP, retroPDelay, delta)
                b += rr
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun irregularPattern(
        rate: Double,
        pWaves: Boolean,
        variation: Double,
        variableP: Boolean = false
    ): List<EcgWavePoint> {
        val nominal = 60.0 / rate.coerceAtLeast(1.0)
        val pattern = doubleArrayOf(-.18, .09, -.05, .14, -.11, .04, .18, -.08)
        val beats = mutableListOf<Double>()
        var b = .4
        var k = 0
        while (b < DURATION_SEC) {
            beats += b
            b += nominal * (1.0 + pattern[k % pattern.size] * (variation / .18))
            k++
        }
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            beats.forEachIndexed { index, center ->
                val pOff = if (variableP) .09 + (index % 3) * .025 else .12
                y += beat(t, center, pWaves, pOffset = pOff, polarity = if (variableP && index % 3 == 2) -.8 else 1.0)
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun paused(rate: Double, droppedEvery: Int, pWaves: Boolean): List<EcgWavePoint> {
        val rr = 60.0 / rate
        val beats = mutableListOf<Double>()
        var b = .45
        var n = 1
        while (b < DURATION_SEC) {
            if (n % droppedEvery != 0) beats += b
            b += rr
            n++
        }
        return waveformFromBeats(beats) { t, c, _ -> beat(t, c, pWaves) }
    }

    private fun atrialFibrillation(rate: Double): List<EcgWavePoint> {
        val nominal = 60.0 / rate
        val pattern = doubleArrayOf(.67, 1.13, .82, 1.28, .72, 1.05, .59, 1.22)
        val beats = mutableListOf<Double>()
        var b = .35
        var k = 0
        while (b < DURATION_SEC) {
            beats += b
            b += nominal * pattern[k % pattern.size]
            k++
        }
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = .025 * sin(2.0 * PI * 6.2 * t) + .018 * sin(2.0 * PI * 8.6 * t)
            beats.forEach { y += beat(t, it, pWaves = false) }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun atrialFlutter(rate: Double, atypical: Boolean): List<EcgWavePoint> {
        val atrialRate = if (atypical) 260.0 else 300.0
        val flutterPeriod = 60.0 / atrialRate
        val ventricularRate = rate.coerceIn(60.0, 180.0)
        val rr = 60.0 / ventricularRate
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            val phase = (t % flutterPeriod) / flutterPeriod
            var y = if (atypical) .10 * sin(2.0 * PI * atrialRate / 60.0 * t) else (phase - .5) * .18
            var b = .4
            while (b < DURATION_SEC) {
                y += beat(t, b, pWaves = false)
                b += rr
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun ectopyPattern(rate: Double, ventricular: Boolean, pattern: Int): List<EcgWavePoint> {
        val rr = 60.0 / rate.coerceIn(45.0, 120.0)
        val beats = mutableListOf<Pair<Double, Boolean>>()
        var b = .45
        var n = 1
        while (b < DURATION_SEC) {
            beats += b to false
            val shouldPremature = when (pattern) {
                2 -> n % 1 == 0
                3 -> n % 2 == 0
                else -> n == 3 || n == 7
            }
            if (shouldPremature && b + rr * .55 < DURATION_SEC) beats += (b + rr * .55) to true
            b += rr
            n++
        }
        return waveformFromBeats(beats.map { it.first }) { t, c, index ->
            val isPremature = beats[index].second
            if (isPremature) beat(t, c, pWaves = !ventricular, wide = ventricular, scale = if (ventricular) 1.3 else 1.0)
            else beat(t, c, pWaves = true)
        }
    }

    private fun ventricularRuns(rate: Double, runLength: Int): List<EcgWavePoint> {
        val baseRate = rate.coerceIn(55.0, 90.0)
        val baseRr = 60.0 / baseRate
        val beats = mutableListOf<Triple<Double, Boolean, Int>>()
        var b = .4
        var n = 0
        while (b < DURATION_SEC) {
            beats += Triple(b, false, n++)
            if (n == 3 || n == 7) {
                var extra = b + baseRr * .50
                repeat(runLength) { j ->
                    if (extra < DURATION_SEC) beats += Triple(extra, true, j)
                    extra += .28
                }
            }
            b += baseRr
        }
        val ordered = beats.sortedBy { it.first }
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            ordered.forEach { (center, ventricular, _) -> y += beat(t, center, pWaves = !ventricular, wide = ventricular, scale = if (ventricular) 1.25 else 1.0) }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun polymorphic(rate: Double, torsades: Boolean): List<EcgWavePoint> {
        val rr = 60.0 / rate
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            var b = .25
            var n = 0
            while (b < DURATION_SEC + .3) {
                val amp = if (torsades) .55 + .75 * abs(sin(n * .45)) else .75 + .35 * sin(n * 1.7)
                val polarity = if (torsades) sin(n * .38).coerceIn(-1.0, 1.0) else if (n % 2 == 0) 1.0 else -.8
                y += beat(t, b, pWaves = false, wide = true, scale = amp, polarity = polarity)
                b += rr
                n++
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun sineTachy(rate: Double, amplitude: Double): List<EcgWavePoint> = List(SAMPLE_COUNT) { i ->
        val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
        EcgWavePoint(i, (amplitude * sin(2.0 * PI * rate / 60.0 * t)).toFloat())
    }

    private fun ventricularFibrillation(): List<EcgWavePoint> = List(SAMPLE_COUNT) { i ->
        val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
        val y = .52 * sin(2.0 * PI * 5.1 * t) + .34 * sin(2.0 * PI * 7.7 * t + .6) + .21 * sin(2.0 * PI * 11.3 * t + 1.8)
        EcgWavePoint(i, y.toFloat())
    }

    private fun wenckebach(rate: Double, mobitz2: Boolean): List<EcgWavePoint> {
        val atrialRr = 60.0 / (rate * 1.3).coerceAtMost(110.0)
        val pTimes = mutableListOf<Double>()
        var p = .35
        while (p < DURATION_SEC) { pTimes += p; p += atrialRr }
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            pTimes.forEachIndexed { index, pTime ->
                y += gaussian(t, pTime, .018, .12)
                val dropped = index % 4 == 3
                val pr = if (mobitz2) .18 else .13 + (index % 4) * .035
                if (!dropped) y += beat(t, pTime + pr, pWaves = false)
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun twoToOne(rate: Double): List<EcgWavePoint> {
        val ventricularRr = 60.0 / rate
        val atrialRr = ventricularRr / 2.0
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            var p = .30
            var n = 0
            while (p < DURATION_SEC) {
                y += gaussian(t, p, .018, .12)
                if (n % 2 == 0) y += beat(t, p + .17, pWaves = false)
                p += atrialRr
                n++
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun highGradeBlock(rate: Double): List<EcgWavePoint> {
        val atrialRr = .72
        val ventricularRr = 60.0 / rate
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            var p = .25
            while (p < DURATION_SEC) { y += gaussian(t, p, .018, .12); p += atrialRr }
            var v = .55
            while (v < DURATION_SEC) { y += beat(t, v, pWaves = false); v += ventricularRr }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun completeBlock(rate: Double): List<EcgWavePoint> {
        val atrialRr = .70
        val ventricularRr = 60.0 / rate
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            var p = .2
            while (p < DURATION_SEC) { y += gaussian(t, p, .018, .12); p += atrialRr }
            var v = .52
            while (v < DURATION_SEC) { y += beat(t, v, pWaves = false, wide = rate < 40.0); v += ventricularRr }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun preexcitedAf(rate: Double): List<EcgWavePoint> {
        val nominal = 60.0 / rate
        val pattern = doubleArrayOf(.55, .88, .63, 1.10, .72, .49, .95)
        val beats = mutableListOf<Double>()
        var b = .25
        var k = 0
        while (b < DURATION_SEC) { beats += b; b += nominal * pattern[k++ % pattern.size] }
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = .018 * sin(2.0 * PI * 7.0 * t)
            beats.forEachIndexed { idx, center -> y += beat(t, center, pWaves = false, wide = idx % 2 == 0, delta = true, scale = .9 + (idx % 3) * .18) }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun paced(rate: Double, atrial: Boolean, ventricular: Boolean): List<EcgWavePoint> {
        val rr = 60.0 / rate.coerceIn(45.0, 130.0)
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            var b = .45
            while (b < DURATION_SEC) {
                if (atrial) y += gaussian(t, b - .16, .002, .55)
                if (ventricular) y += gaussian(t, b - .012, .002, .8)
                y += beat(t, b, pWaves = atrial && !ventricular, wide = ventricular)
                b += rr
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun pacingFailure(rate: Double, oversensing: Boolean, captureFailure: Boolean): List<EcgWavePoint> {
        val rr = 60.0 / rate.coerceIn(45.0, 100.0)
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            var b = .45
            var n = 0
            while (b < DURATION_SEC) {
                val suppressSpike = oversensing && n % 4 == 2
                if (!suppressSpike) y += gaussian(t, b - .014, .002, .75)
                val capture = !(captureFailure && n % 3 == 1)
                if (capture) y += beat(t, b, pWaves = false, wide = true)
                if (!oversensing && n % 5 == 3) y += beat(t, b + rr * .45, pWaves = false, wide = true)
                b += rr
                n++
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun artifactTachy(rate: Double): List<EcgWavePoint> {
        val base = regular(rate.coerceIn(60.0, 90.0), pWaves = true)
        return base.mapIndexed { i, pt ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            val artifact = if (t in 2.0..4.5) .35 * sin(2.0 * PI * 13.0 * t) + .18 * sin(2.0 * PI * 21.0 * t) else 0.0
            EcgWavePoint(i, pt.value + artifact.toFloat())
        }
    }

    private fun artifactPause(rate: Double): List<EcgWavePoint> {
        val base = regular(rate.coerceIn(55.0, 85.0), pWaves = true)
        return base.mapIndexed { i, pt ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            val atten = if (t in 3.0..4.3) .08f else 1f
            EcgWavePoint(i, pt.value * atten)
        }
    }

    private fun undersensingMimic(rate: Double): List<EcgWavePoint> {
        val rr = 60.0 / rate.coerceIn(55.0, 90.0)
        return List(SAMPLE_COUNT) { i ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            var b = .45
            var n = 0
            while (b < DURATION_SEC) {
                val scale = if (n % 4 == 2) .18 else 1.0
                y += beat(t, b, pWaves = false, scale = scale)
                b += rr
                n++
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    private fun oversensingMimic(rate: Double): List<EcgWavePoint> {
        val base = regular(rate.coerceIn(60.0, 100.0), pWaves = false)
        return base.mapIndexed { i, pt ->
            val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
            val extra = .09 * sin(2.0 * PI * 4.2 * t) + if ((t * 2).toInt() % 3 == 1) gaussian(t, ((t * 2).toInt() / 2.0) + .23, .006, .28) else 0.0
            EcgWavePoint(i, pt.value + extra.toFloat())
        }
    }

    private fun waveformFromBeats(
        beats: List<Double>,
        renderer: (Double, Double, Int) -> Double
    ): List<EcgWavePoint> = List(SAMPLE_COUNT) { i ->
        val t = DURATION_SEC * i / (SAMPLE_COUNT - 1).toDouble()
        var y = 0.0
        beats.forEachIndexed { index, center -> y += renderer(t, center, index) }
        EcgWavePoint(i, y.toFloat())
    }
}