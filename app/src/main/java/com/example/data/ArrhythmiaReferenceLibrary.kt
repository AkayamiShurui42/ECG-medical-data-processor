package com.example.data

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * User-facing rhythm reference cards.
 *
 * These are visual/differential teaching aids for comparison with the uploaded
 * tracing. They are not a replacement for the measured waveform, and they do
 * not silently convert a resemblance into a diagnosis.
 */
enum class RhythmFamily {
    SINUS,
    ATRIAL,
    JUNCTIONAL,
    SUPRAVENTRICULAR_TACHYCARDIA,
    VENTRICULAR,
    AV_CONDUCTION,
    PREEXCITATION,
    PAUSE_BRADY,
    PACED,
    OTHER
}

data class ExpectedNumericFeature(
    val label: String,
    val expected: String,
    val unit: String? = null,
    val explanation: String
)

data class MorphologyCue(
    val label: String,
    val whatToLookFor: String,
    val supportsWhenPresent: Boolean = true
)

data class DifferentialCue(
    val alternative: String,
    val distinction: String
)

data class ModalityCaveat(
    val modality: RecordingModality,
    val note: String
)

data class RhythmReferenceCard(
    val id: String,
    val name: String,
    val family: RhythmFamily,
    val shortDescription: String,
    val expectedNumbers: List<ExpectedNumericFeature>,
    val morphology: List<MorphologyCue>,
    val featuresAgainst: List<String>,
    val commonLookalikes: List<DifferentialCue>,
    val modalityCaveats: List<ModalityCaveat>,
    val guidelineTopics: Set<GuidelineTopic>,
    val generatedExample: List<EcgWavePoint>
)

/**
 * Generates normalized educational examples only. Values are deliberately
 * synthetic and are never substituted for an uploaded patient's waveform.
 */
object ReferenceWaveformGenerator {
    private const val SAMPLE_COUNT = 1000

    private fun gaussian(x: Double, center: Double, width: Double, amplitude: Double): Double {
        val z = (x - center) / width
        return amplitude * exp(-0.5 * z * z)
    }

    private fun normalBeat(x: Double, center: Double, p: Boolean = true, qrsScale: Double = 1.0, wide: Boolean = false): Double {
        val qrsWidth = if (wide) 0.018 else 0.008
        var y = 0.0
        if (p) y += gaussian(x, center - 0.12, 0.018, 0.12)
        y += gaussian(x, center - 0.018, qrsWidth, -0.18 * qrsScale)
        y += gaussian(x, center, qrsWidth, 1.0 * qrsScale)
        y += gaussian(x, center + 0.022, qrsWidth * 1.25, -0.32 * qrsScale)
        y += gaussian(x, center + 0.20, 0.045, 0.28)
        return y
    }

    fun sinus(rateBpm: Int = 75): List<EcgWavePoint> {
        val seconds = 8.0
        val rr = 60.0 / rateBpm
        return List(SAMPLE_COUNT) { i ->
            val t = seconds * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            var beat = 0.55
            while (beat < seconds + 0.5) {
                y += normalBeat(t, beat)
                beat += rr
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    fun atrialFibrillation(rateBpm: Int = 110): List<EcgWavePoint> {
        val seconds = 8.0
        val rrPattern = doubleArrayOf(0.47, 0.62, 0.50, 0.77, 0.55, 0.68, 0.44, 0.73)
        val beats = mutableListOf<Double>()
        var tBeat = 0.45
        var k = 0
        while (tBeat < seconds) {
            beats += tBeat
            val nominal = 60.0 / rateBpm
            tBeat += rrPattern[k % rrPattern.size] * nominal / 0.55
            k++
        }
        return List(SAMPLE_COUNT) { i ->
            val t = seconds * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.025 * sin(2.0 * PI * 6.4 * t) + 0.018 * sin(2.0 * PI * 8.7 * t)
            beats.forEach { y += normalBeat(t, it, p = false) }
            EcgWavePoint(i, y.toFloat())
        }
    }

    fun atrialFlutter(conductionRatio: Int = 2): List<EcgWavePoint> {
        val seconds = 8.0
        val flutterRate = 300.0
        val flutterPeriod = 60.0 / flutterRate
        val ventricularPeriod = flutterPeriod * conductionRatio
        return List(SAMPLE_COUNT) { i ->
            val t = seconds * i / (SAMPLE_COUNT - 1).toDouble()
            val phase = (t % flutterPeriod) / flutterPeriod
            val saw = (phase - 0.5) * 0.18
            var y = saw
            var beat = 0.45
            while (beat < seconds + 0.5) {
                y += normalBeat(t, beat, p = false)
                beat += ventricularPeriod
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    fun narrowComplexTachycardia(rateBpm: Int = 180): List<EcgWavePoint> {
        val seconds = 8.0
        val rr = 60.0 / rateBpm
        return List(SAMPLE_COUNT) { i ->
            val t = seconds * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            var beat = 0.30
            while (beat < seconds + 0.3) {
                y += normalBeat(t, beat, p = false)
                beat += rr
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    fun ventricularTachycardia(rateBpm: Int = 160): List<EcgWavePoint> {
        val seconds = 8.0
        val rr = 60.0 / rateBpm
        return List(SAMPLE_COUNT) { i ->
            val t = seconds * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            var beat = 0.35
            while (beat < seconds + 0.4) {
                y += normalBeat(t, beat, p = false, qrsScale = 1.25, wide = true)
                beat += rr
            }
            EcgWavePoint(i, y.toFloat())
        }
    }

    fun pvcPattern(): List<EcgWavePoint> {
        val seconds = 8.0
        val beats = listOf(
            Triple(0.60, false, true),
            Triple(1.40, false, true),
            Triple(2.00, true, false),
            Triple(3.00, false, true),
            Triple(3.80, false, true),
            Triple(4.60, false, true),
            Triple(5.18, true, false),
            Triple(6.20, false, true),
            Triple(7.00, false, true)
        )
        return List(SAMPLE_COUNT) { i ->
            val t = seconds * i / (SAMPLE_COUNT - 1).toDouble()
            var y = 0.0
            beats.forEach { (center, pvc, pWave) ->
                y += normalBeat(t, center, p = pWave, qrsScale = if (pvc) 1.35 else 1.0, wide = pvc)
            }
            EcgWavePoint(i, y.toFloat())
        }
    }
}

object ArrhythmiaReferenceLibrary {
    val cards: List<RhythmReferenceCard> = listOf(
        RhythmReferenceCard(
            id = "sinus_tachycardia",
            name = "Sinus Tachycardia",
            family = RhythmFamily.SINUS,
            shortDescription = "Fast rhythm that retains an organized sinus P-to-QRS relationship.",
            expectedNumbers = listOf(
                ExpectedNumericFeature("Rate", ">100 in adults", "bpm", "Rate alone does not distinguish sinus tachycardia from another supraventricular rhythm."),
                ExpectedNumericFeature("QRS", "usually narrow unless baseline conduction abnormality/aberrancy is present", "ms", "Compare each beat with the patient's baseline morphology."),
                ExpectedNumericFeature("RR", "generally regular with physiologic variability", "ms", "Gradual acceleration/deceleration favors sinus mechanism over abrupt re-entry.")
            ),
            morphology = listOf(
                MorphologyCue("P before QRS", "Look for a consistent atrial deflection preceding each accepted ventricular complex."),
                MorphologyCue("AV relationship", "P-QRS relationship should remain organized beat to beat."),
                MorphologyCue("Onset/offset", "Gradual rate change supports sinus tachycardia; sudden onset/termination raises re-entrant SVT in the differential.")
            ),
            featuresAgainst = listOf("No reproducible P-QRS relationship", "Abrupt fixed-rate onset/termination", "Irregularly irregular RR pattern without organized P waves"),
            commonLookalikes = listOf(
                DifferentialCue("AVNRT/AVRT", "Often more abrupt and highly regular; atrial activity may be buried in or immediately adjacent to QRS."),
                DifferentialCue("Atrial tachycardia", "P-wave morphology/axis can differ from sinus and may show warm-up/cool-down."),
                DifferentialCue("Artifact", "Noise may create apparent extra peaks but should not reproduce a coherent QRS morphology across cycles.")
            ),
            modalityCaveats = listOf(
                ModalityCaveat(RecordingModality.IMPLANTABLE_LOOP_RECORDER, "A subcutaneous vector may make P waves small or invisible; do not require clearly visible P waves when the device/vector cannot reliably show them."),
                ModalityCaveat(RecordingModality.HOLTER, "Use onset/offset and longer context when available rather than judging a short strip alone.")
            ),
            guidelineTopics = setOf(GuidelineTopic.SUPRAVENTRICULAR_TACHYCARDIA),
            generatedExample = ReferenceWaveformGenerator.sinus(125)
        ),
        RhythmReferenceCard(
            id = "atrial_fibrillation",
            name = "Atrial Fibrillation",
            family = RhythmFamily.ATRIAL,
            shortDescription = "Disorganized atrial activity with no consistent P-wave sequence and an irregular ventricular response unless conduction is constrained.",
            expectedNumbers = listOf(
                ExpectedNumericFeature("RR pattern", "irregularly irregular in typical conducted AF", "ms", "The exact RR sequence is more informative than a single average heart rate."),
                ExpectedNumericFeature("P waves", "no consistent organized P wave preceding each QRS", null, "Fine/coarse atrial activity may be present, but single-lead visibility depends strongly on vector and filtering."),
                ExpectedNumericFeature("QRS", "usually narrow unless pre-existing block, aberrancy, pacing, or ventricular ectopy is present", "ms", "Wide QRS does not by itself exclude AF.")
            ),
            morphology = listOf(
                MorphologyCue("RR irregularity", "Inspect the beat-to-beat millisecond list for non-repeating irregular intervals."),
                MorphologyCue("Atrial organization", "Look for absence of a stable repeating P morphology/PR relationship."),
                MorphologyCue("Baseline", "Fibrillatory activity may be subtle; do not label baseline noise as fibrillation without ventricular timing support.")
            ),
            featuresAgainst = listOf("Highly regular RR sequence with stable atrial relationship", "Consistent sinus P wave before every QRS", "Apparent irregularity explained entirely by PAC/PVCs or sensing dropout"),
            commonLookalikes = listOf(
                DifferentialCue("Frequent PAC/PVCs", "Ectopy can produce irregular RR intervals while organized atrial activity remains between ectopic beats."),
                DifferentialCue("ILR undersensing", "Missed R waves can create false irregularity, pause, or low-rate patterns."),
                DifferentialCue("Motion/myopotential noise", "Noise should be tested against QRS continuity and repeated morphology rather than equated with fibrillatory baseline.")
            ),
            modalityCaveats = listOf(
                ModalityCaveat(RecordingModality.IMPLANTABLE_LOOP_RECORDER, "Automated AF detections require review for ectopy, undersensing, oversensing, and noise."),
                ModalityCaveat(RecordingModality.WEARABLE_ECG, "Single-lead recordings may support rhythm classification but do not supply the spatial information of a diagnostic 12-lead ECG.")
            ),
            guidelineTopics = setOf(GuidelineTopic.ATRIAL_FIBRILLATION, GuidelineTopic.IMPLANTABLE_LOOP_RECORDER),
            generatedExample = ReferenceWaveformGenerator.atrialFibrillation()
        ),
        RhythmReferenceCard(
            id = "atrial_flutter",
            name = "Atrial Flutter",
            family = RhythmFamily.ATRIAL,
            shortDescription = "Organized macro-reentrant atrial rhythm; ventricular response depends on AV conduction ratio.",
            expectedNumbers = listOf(
                ExpectedNumericFeature("Atrial activity", "classically around 250–350", "bpm", "Actual rate varies and prior treatment/atrial disease can alter it."),
                ExpectedNumericFeature("Conduction", "may be 2:1, 3:1, 4:1, variable, or other", null, "A ventricular rate near 150 bpm can occur with 2:1 conduction but is not diagnostic by itself."),
                ExpectedNumericFeature("RR", "regular with fixed conduction; variable when AV conduction varies", "ms", "Inspect both atrial deflections and ventricular timing.")
            ),
            morphology = listOf(
                MorphologyCue("Repeated atrial deflections", "Look for organized repeating atrial activity between QRS complexes."),
                MorphologyCue("Conduction ratio", "Count atrial cycles per ventricular response rather than relying only on ventricular rate."),
                MorphologyCue("Saw-tooth appearance", "Classic flutter morphology may be obvious in some leads and subtle or absent in a single subcutaneous vector.")
            ),
            featuresAgainst = listOf("No organized atrial periodicity", "Completely chaotic baseline with irregularly irregular ventricular response"),
            commonLookalikes = listOf(
                DifferentialCue("Sinus tachycardia", "Sinus rhythm has one organized sinus P wave for each conducted QRS rather than continuous flutter activity."),
                DifferentialCue("Atrial tachycardia", "Atrial tachycardia may have discrete P waves separated by isoelectric baseline rather than continuous macro-reentrant activity."),
                DifferentialCue("Artifact", "Regular electrical interference can imitate repetitive atrial activity; verify phase relationship to QRS and persistence across the strip.")
            ),
            modalityCaveats = listOf(ModalityCaveat(RecordingModality.IMPLANTABLE_LOOP_RECORDER, "Low-amplitude atrial activity may be poorly represented; ventricular timing plus longer episode context becomes more important.")),
            guidelineTopics = setOf(GuidelineTopic.ATRIAL_FLUTTER, GuidelineTopic.ATRIAL_FIBRILLATION),
            generatedExample = ReferenceWaveformGenerator.atrialFlutter()
        ),
        RhythmReferenceCard(
            id = "regular_narrow_svt",
            name = "Regular Narrow-Complex SVT (Differential)",
            family = RhythmFamily.SUPRAVENTRICULAR_TACHYCARDIA,
            shortDescription = "A pattern category rather than a single mechanism; includes AVNRT, orthodromic AVRT, atrial tachycardia, flutter with fixed conduction, and sinus tachycardia.",
            expectedNumbers = listOf(
                ExpectedNumericFeature("Rate", "often >100 and commonly 150–250 depending on mechanism", "bpm", "Rate overlaps substantially among mechanisms."),
                ExpectedNumericFeature("QRS", "typically <120 if no baseline block/aberrancy", "ms", "A wide-complex tachycardia requires a different differential."),
                ExpectedNumericFeature("RR", "usually highly regular for re-entrant SVT", "ms", "Beat-to-beat timing should be inspected rather than inferred from displayed average rate.")
            ),
            morphology = listOf(
                MorphologyCue("Atrial timing", "Determine whether atrial activity is before, within, or after QRS."),
                MorphologyCue("Onset/termination", "Abrupt onset and offset favor re-entry; warm-up/cool-down suggests automatic atrial/sinus mechanisms."),
                MorphologyCue("RP/PR relationship", "When atrial activity is visible, compare RP and PR across beats.")
            ),
            featuresAgainst = listOf("Markedly irregular RR sequence", "Clearly ventricular-origin morphology or AV dissociation"),
            commonLookalikes = listOf(
                DifferentialCue("Sinus tachycardia", "Gradual onset/offset and sinus P-wave relationship favor sinus mechanism."),
                DifferentialCue("Atrial flutter with 2:1 conduction", "Search for an additional atrial deflection hidden in/near each QRS/T cycle."),
                DifferentialCue("Ventricular tachycardia", "A wide complex or AV dissociation changes the differential substantially.")
            ),
            modalityCaveats = listOf(ModalityCaveat(RecordingModality.IMPLANTABLE_LOOP_RECORDER, "P/RP relationships may not be resolvable; classify what the single vector can actually demonstrate and retain uncertainty about mechanism.")),
            guidelineTopics = setOf(GuidelineTopic.SUPRAVENTRICULAR_TACHYCARDIA, GuidelineTopic.PREEXCITATION),
            generatedExample = ReferenceWaveformGenerator.narrowComplexTachycardia()
        ),
        RhythmReferenceCard(
            id = "pvc",
            name = "Premature Ventricular Complex",
            family = RhythmFamily.VENTRICULAR,
            shortDescription = "An early ventricular depolarization with morphology different from the surrounding conducted beats.",
            expectedNumbers = listOf(
                ExpectedNumericFeature("Prematurity", "earlier than expected next sinus/underlying beat", "ms", "Measure coupling interval directly from accepted R peaks."),
                ExpectedNumericFeature("QRS", "often wide and morphologically different", "ms", "Single-lead width/morphology must be measured from the actual digitized trace."),
                ExpectedNumericFeature("Pause", "may be compensatory, interpolated, or non-compensatory", "ms", "Do not require a compensatory pause to call a ventricular ectopic beat.")
            ),
            morphology = listOf(
                MorphologyCue("Wide/different ventricular morphology", "Compare the candidate beat against multiple preceding and following beats."),
                MorphologyCue("No fixed preceding sinus P relationship", "Atrial activity may be absent, dissociated, or retrograde."),
                MorphologyCue("Coupling interval", "List exact milliseconds from the preceding accepted ventricular beat to the ectopic beat.")
            ),
            featuresAgainst = listOf("Candidate deflection lacks a coherent ventricular complex", "Deflection exists only as high-frequency noise without a corresponding cardiac morphology"),
            commonLookalikes = listOf(
                DifferentialCue("PAC with aberrancy", "Look for premature atrial activity and compare aberrant morphology with known bundle-branch patterns."),
                DifferentialCue("Motion/myopotential artifact", "Artifact often lacks a full physiologic depolarization/repolarization sequence and may not preserve plausible timing/morphology."),
                DifferentialCue("ILR oversensing", "A single ventricular complex may be counted twice if a T wave or noise deflection crosses sensing thresholds.")
            ),
            modalityCaveats = listOf(ModalityCaveat(RecordingModality.IMPLANTABLE_LOOP_RECORDER, "Assess whether the device counted one ventricular cycle multiple times or missed adjacent low-amplitude R waves before accepting device-generated ectopy counts.")),
            guidelineTopics = setOf(GuidelineTopic.VENTRICULAR_ARRHYTHMIA, GuidelineTopic.IMPLANTABLE_LOOP_RECORDER),
            generatedExample = ReferenceWaveformGenerator.pvcPattern()
        ),
        RhythmReferenceCard(
            id = "ventricular_tachycardia",
            name = "Ventricular Tachycardia",
            family = RhythmFamily.VENTRICULAR,
            shortDescription = "A ventricular tachyarrhythmia; morphology and clinical context determine subtype and urgency.",
            expectedNumbers = listOf(
                ExpectedNumericFeature("Sequence", "≥3 consecutive ventricular-origin beats is a commonly used run definition", null, "Episode duration and rate should be measured exactly."),
                ExpectedNumericFeature("Rate", "commonly >100", "bpm", "Slow VT exists; rate alone is not sufficient."),
                ExpectedNumericFeature("QRS", "often wide", "ms", "A single-lead strip may not provide enough spatial information for a definitive mechanism in every case.")
            ),
            morphology = listOf(
                MorphologyCue("Repeated ventricular morphology", "Look for consecutive similar abnormal ventricular complexes."),
                MorphologyCue("AV relationship", "AV dissociation, capture beats, or fusion beats support VT when visible."),
                MorphologyCue("Episode continuity", "Verify that successive complexes form a coherent cardiac rhythm rather than fragmented artifact.")
            ),
            featuresAgainst = listOf("Normal narrow conducted morphology with clear supraventricular atrial relationship", "Apparent rapid activity consists only of non-cardiac oscillation without repeated QRS complexes"),
            commonLookalikes = listOf(
                DifferentialCue("SVT with aberrancy", "Compare baseline conduction morphology and atrial/ventricular relationships."),
                DifferentialCue("Pre-excited tachycardia", "Requires consideration when pre-excitation is known/suspected."),
                DifferentialCue("Artifact", "Confirm ventricular depolarization morphology across the episode and inspect the raw source frame/trace at high zoom.")
            ),
            modalityCaveats = listOf(ModalityCaveat(RecordingModality.IMPLANTABLE_LOOP_RECORDER, "Single-vector recordings can make mechanism adjudication difficult; retain the full differential and inspect sensing errors before accepting automated tachy labels.")),
            guidelineTopics = setOf(GuidelineTopic.VENTRICULAR_ARRHYTHMIA, GuidelineTopic.CARDIAC_DEVICE_MONITORING),
            generatedExample = ReferenceWaveformGenerator.ventricularTachycardia()
        )
    )

    fun byId(id: String): RhythmReferenceCard? = cards.firstOrNull { it.id == id }

    fun byFamily(family: RhythmFamily): List<RhythmReferenceCard> = cards.filter { it.family == family }
}
