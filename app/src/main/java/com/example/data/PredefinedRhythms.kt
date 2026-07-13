package com.example.data

import kotlin.math.exp
import kotlin.math.sin

data class ReferenceRhythm(
    val name: String,
    val shortName: String,
    val category: String, // "Normal", "Atrial", "Ventricular", "Conduction Block", "Myocardial Injury"
    val typicalHr: Int,
    val pWaveMs: Int,
    val prIntervalMs: Int,
    val qrsDurationMs: Int,
    val qtMs: Int,
    val stElevationMv: Float,
    val tWaveMorphology: String,
    val description: String,
    val guidelines2026: String
)

object PredefinedRhythmLibrary {

    val rhythms = listOf(
        ReferenceRhythm(
            name = "Normal Sinus Rhythm (NSR)",
            shortName = "NSR",
            category = "Normal",
            typicalHr = 72,
            pWaveMs = 90,
            prIntervalMs = 160,
            qrsDurationMs = 80,
            qtMs = 400,
            stElevationMv = 0.0f,
            tWaveMorphology = "Upright",
            description = "Normal cardiac rhythm initiated by the SA node. Stable, uniform waveforms, consistent intervals, rate between 60 and 100 BPM.",
            guidelines2026 = "2026 AHA consensus reaffirms NSR as the physiological baseline. Regular R-R interval, constant PR (120-200ms), and narrow QRS (<120ms) are fundamental hallmarks."
        ),
        ReferenceRhythm(
            name = "Sinus Bradycardia",
            shortName = "Bradycardia",
            category = "Normal",
            typicalHr = 48,
            pWaveMs = 95,
            prIntervalMs = 170,
            qrsDurationMs = 80,
            qtMs = 430,
            stElevationMv = 0.0f,
            tWaveMorphology = "Upright",
            description = "Sinus rhythm with a rate below 60 BPM. Otherwise normal waveform characteristics.",
            guidelines2026 = "2026 AHA bradycardia guidelines prioritize investigation of medication effects (beta-blockers) and thyroid function before pacemaker interventions."
        ),
        ReferenceRhythm(
            name = "Sinus Tachycardia",
            shortName = "Tachycardia",
            category = "Normal",
            typicalHr = 120,
            pWaveMs = 80,
            prIntervalMs = 130,
            qrsDurationMs = 80,
            qtMs = 320,
            stElevationMv = 0.0f,
            tWaveMorphology = "Upright",
            description = "Sinus rhythm with a rate above 100 BPM. Often a physiological response to stress, exercise, or infection.",
            guidelines2026 = "Treatment focuses on resolving underlying triggers (dehydration, fever, hyperthyroidism) rather than direct heart rate suppression."
        ),
        ReferenceRhythm(
            name = "Atrial Fibrillation (AFib)",
            shortName = "AFib",
            category = "Atrial",
            typicalHr = 125,
            pWaveMs = 0,
            prIntervalMs = 0,
            qrsDurationMs = 85,
            qtMs = 360,
            stElevationMv = 0.0f,
            tWaveMorphology = "Inverted",
            description = "Complete absence of P-waves. Replaced by high-frequency fibrillatory baseline f-waves. Irregularly irregular R-R intervals.",
            guidelines2026 = "2026 AHA/NIH stroke mitigation guidelines mandate immediate calculation of CHA2DS2-VASc score and anticoagulation selection for LA size > 40 mm."
        ),
        ReferenceRhythm(
            name = "Atrial Flutter (AFL)",
            shortName = "Flutter",
            category = "Atrial",
            typicalHr = 100,
            pWaveMs = 0,
            prIntervalMs = 0,
            qrsDurationMs = 80,
            qtMs = 380,
            stElevationMv = 0.0f,
            tWaveMorphology = "Flat",
            description = "Characterized by rapid, regular sawtooth-like waves ('F-waves') at an atrial rate of 250-350 BPM, often with a fixed conduction ratio (e.g., 3:1).",
            guidelines2026 = "Reentrant atrial macroreentry guidelines strongly recommend catheter ablation of the cavotricuspid isthmus (CTI) as a first-line therapy."
        ),
        ReferenceRhythm(
            name = "Supraventricular Tachycardia (SVT)",
            shortName = "SVT",
            category = "Atrial",
            typicalHr = 180,
            pWaveMs = 0,
            prIntervalMs = 0,
            qrsDurationMs = 75,
            qtMs = 280,
            stElevationMv = 0.0f,
            tWaveMorphology = "Flat",
            description = "Rapid regular rhythm arising above the bundle branches. P-waves are often buried inside or immediately follow the narrow QRS complexes.",
            guidelines2026 = "Vagal maneuvers (modified Valsalva) followed by rapid IV adenosine are established as the class I interventions under 2026 ACLS guidelines."
        ),
        ReferenceRhythm(
            name = "Premature Ventricular Contractions (PVC)",
            shortName = "PVC",
            category = "Ventricular",
            typicalHr = 80,
            pWaveMs = 90,
            prIntervalMs = 150,
            qrsDurationMs = 140,
            qtMs = 420,
            stElevationMv = -0.1f,
            tWaveMorphology = "Inverted",
            description = "Occasional early ventricular depolarization causing a wide, bizarre QRS complex (>120ms) without a preceding P-wave, followed by a compensatory pause.",
            guidelines2026 = "PVC burden exceeding 10-15% of daily beats warrants echocardiogram to rule out PVC-induced cardiomyopathy as per newest NIH registries."
        ),
        ReferenceRhythm(
            name = "Ventricular Tachycardia (VT)",
            shortName = "VT",
            category = "Ventricular",
            typicalHr = 160,
            pWaveMs = 0,
            prIntervalMs = 0,
            qrsDurationMs = 150,
            qtMs = 320,
            stElevationMv = -0.3f,
            tWaveMorphology = "Inverted",
            description = "Run of three or more consecutive wide QRS complexes (>120ms) at a rate above 100 BPM. Severe hemodynamic instability risk.",
            guidelines2026 = "ACLS 2026 updates state: pulseless VT requires immediate defibrillation. Stable VT is treated with amiodarone, synchronized cardioversion, or catheter ablation."
        ),
        ReferenceRhythm(
            name = "Ventricular Fibrillation (VFib)",
            shortName = "VFib",
            category = "Ventricular",
            typicalHr = 220,
            pWaveMs = 0,
            prIntervalMs = 0,
            qrsDurationMs = 0,
            qtMs = 0,
            stElevationMv = 0.0f,
            tWaveMorphology = "Flat",
            description = "Completely chaotic baseline with no discernible P, QRS, or T waves. Represents severe cardiac arrest.",
            guidelines2026 = "Immediate high-quality CPR and defibrillation. Survival rates drop 10% for every minute of delayed shocks, making high-speed ACLS deployment crucial."
        ),
        ReferenceRhythm(
            name = "First-Degree AV Block",
            shortName = "1st Deg Block",
            category = "Conduction Block",
            typicalHr = 65,
            pWaveMs = 90,
            prIntervalMs = 240,
            qrsDurationMs = 80,
            qtMs = 410,
            stElevationMv = 0.0f,
            tWaveMorphology = "Upright",
            description = "Characterized by a prolonged PR interval (>200 ms) that remains constant from beat to beat, with all P-waves successfully conducted.",
            guidelines2026 = "A benign finding in young athletes, but progression to Mobitz II or complete block must be monitored in older patients with underlying structural heart disease."
        ),
        ReferenceRhythm(
            name = "Second-Degree AV Block Mobitz I",
            shortName = "Mobitz I",
            category = "Conduction Block",
            typicalHr = 55,
            pWaveMs = 90,
            prIntervalMs = 180,
            qrsDurationMs = 85,
            qtMs = 415,
            stElevationMv = 0.0f,
            tWaveMorphology = "Upright",
            description = "Progressive prolongation of the PR interval until a QRS complex is completely dropped, after which the cycle resets (Wenckebach phenomenon).",
            guidelines2026 = "Usually localized to the AV node itself. Symptomatic cases may respond to atropine, but permanent pacing is rarely required unless high-grade."
        ),
        ReferenceRhythm(
            name = "Second-Degree AV Block Mobitz II",
            shortName = "Mobitz II",
            category = "Conduction Block",
            typicalHr = 50,
            pWaveMs = 90,
            prIntervalMs = 160,
            qrsDurationMs = 95,
            qtMs = 420,
            stElevationMv = 0.0f,
            tWaveMorphology = "Upright",
            description = "Intermittent dropped QRS complexes without progressive PR prolongation. PR interval is constant before the dropped beat.",
            guidelines2026 = "High risk of sudden progression to complete third-degree AV block. 2026 AHA guidelines mandate dual-chamber pacemaker implantation for Mobitz II."
        ),
        ReferenceRhythm(
            name = "Third-Degree AV Block (Complete)",
            shortName = "3rd Deg Block",
            category = "Conduction Block",
            typicalHr = 38,
            pWaveMs = 95,
            prIntervalMs = 0,
            qrsDurationMs = 120,
            qtMs = 460,
            stElevationMv = 0.0f,
            tWaveMorphology = "Inverted",
            description = "Complete dissociation between atria and ventricles. P-waves and QRS complexes occur independently at separate rates.",
            guidelines2026 = "Lethal bradycardia risk. Emergency transcutaneous pacing followed by class I permanent pacemaker insertion is required."
        ),
        ReferenceRhythm(
            name = "Acute ST-Elevation (STEMI)",
            shortName = "STEMI",
            category = "Myocardial Injury",
            typicalHr = 95,
            pWaveMs = 90,
            prIntervalMs = 155,
            qrsDurationMs = 95,
            qtMs = 410,
            stElevationMv = 3.5f,
            tWaveMorphology = "Hyperacute",
            description = "Myocardial injury representing acute coronary artery occlusion. Marked ST-segment elevation (>1-2 mm in multiple contiguous leads).",
            guidelines2026 = "Time is muscle. Emergency percutaneous coronary intervention (PCI) within 90 minutes of first medical contact is the primary 2026 guideline target."
        )
    )

    fun synthesizeWaveformForRhythm(rhythmName: String): List<EcgWavePoint> {
        val points = mutableListOf<EcgWavePoint>()
        val size = 500

        for (i in 0 until size) {
            var valBase = 0f
            when (rhythmName) {
                "Normal Sinus Rhythm (NSR)" -> {
                    valBase += generateBeat(i, 30, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                    valBase += generateBeat(i, 230, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                    valBase += generateBeat(i, 430, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                }
                "Sinus Bradycardia" -> {
                    valBase += generateBeat(i, 50, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                    valBase += generateBeat(i, 350, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                }
                "Sinus Tachycardia" -> {
                    valBase += generateBeat(i, 10, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                    valBase += generateBeat(i, 130, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                    valBase += generateBeat(i, 250, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                    valBase += generateBeat(i, 370, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                }
                "Atrial Fibrillation (AFib)" -> {
                    valBase += generateBeat(i, 35, st = 0.0f, isWide = false, hasP = false, isTInverted = true)
                    valBase += generateBeat(i, 180, st = 0.0f, isWide = false, hasP = false, isTInverted = true)
                    valBase += generateBeat(i, 305, st = 0.0f, isWide = false, hasP = false, isTInverted = true)
                    valBase += generateBeat(i, 440, st = 0.0f, isWide = false, hasP = false, isTInverted = true)
                    valBase += (sin(i * 0.8f) * 0.08f) + (sin(i * 1.9f) * 0.05f)
                }
                "Atrial Flutter (AFL)" -> {
                    valBase += generateBeat(i, 60, st = 0.0f, isWide = false, hasP = false, isTInverted = false)
                    valBase += generateBeat(i, 210, st = 0.0f, isWide = false, hasP = false, isTInverted = false)
                    valBase += generateBeat(i, 360, st = 0.0f, isWide = false, hasP = false, isTInverted = false)
                    // regular sawtooth flutter waves
                    valBase += (sin(i * 0.35f) * 0.22f)
                }
                "Supraventricular Tachycardia (SVT)" -> {
                    valBase += generateBeat(i, 10, st = 0.0f, isWide = false, hasP = false, isTInverted = false)
                    valBase += generateBeat(i, 100, st = 0.0f, isWide = false, hasP = false, isTInverted = false)
                    valBase += generateBeat(i, 190, st = 0.0f, isWide = false, hasP = false, isTInverted = false)
                    valBase += generateBeat(i, 280, st = 0.0f, isWide = false, hasP = false, isTInverted = false)
                    valBase += generateBeat(i, 370, st = 0.0f, isWide = false, hasP = false, isTInverted = false)
                }
                "Premature Ventricular Contractions (PVC)" -> {
                    valBase += generateBeat(i, 30, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                    // Wide early PVC beat at 180
                    valBase += generateBeat(i, 160, st = -0.1f, isWide = true, hasP = false, isTInverted = true, customAmp = 1.6f)
                    valBase += generateBeat(i, 360, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                }
                "Ventricular Tachycardia (VT)" -> {
                    valBase += generateBeat(i, 20, st = -0.2f, isWide = true, hasP = false, isTInverted = true, customAmp = 1.5f)
                    valBase += generateBeat(i, 130, st = -0.2f, isWide = true, hasP = false, isTInverted = true, customAmp = 1.5f)
                    valBase += generateBeat(i, 240, st = -0.2f, isWide = true, hasP = false, isTInverted = true, customAmp = 1.5f)
                    valBase += generateBeat(i, 350, st = -0.2f, isWide = true, hasP = false, isTInverted = true, customAmp = 1.5f)
                }
                "Ventricular Fibrillation (VFib)" -> {
                    // completely chaotic waves
                    valBase += (sin(i * 0.2f) * 0.4f) + (sin(i * 0.5f) * 0.3f) + (sin(i * 0.9f) * 0.15f)
                }
                "First-Degree AV Block" -> {
                    // prolonged PR interval (represented by shifting QRS/T offset further)
                    valBase += generateBeat(i, 20, st = 0.0f, isWide = false, hasP = true, isTInverted = false, prShift = 15)
                    valBase += generateBeat(i, 250, st = 0.0f, isWide = false, hasP = true, isTInverted = false, prShift = 15)
                }
                "Second-Degree AV Block Mobitz I" -> {
                    // Progressive PR delay: beat 1 has normal PR, beat 2 has long PR, beat 3 drops
                    valBase += generateBeat(i, 20, st = 0.0f, isWide = false, hasP = true, isTInverted = false, prShift = 0)
                    valBase += generateBeat(i, 200, st = 0.0f, isWide = false, hasP = true, isTInverted = false, prShift = 20)
                    // Drop beat around 360 (P wave only, no QRS/T)
                    valBase += generatePOnly(i, 360)
                }
                "Second-Degree AV Block Mobitz II" -> {
                    // fixed PR, but intermittent block
                    valBase += generateBeat(i, 20, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                    valBase += generatePOnly(i, 200) // dropped beat
                    valBase += generateBeat(i, 320, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                }
                "Third-Degree AV Block (Complete)" -> {
                    // Complete dissociation: regular slow ventricular escape beats at 35, 280, and fast regular P waves
                    valBase += generateEscapeBeat(i, 40)
                    valBase += generateEscapeBeat(i, 290)
                    // P waves marching at independent fast rate
                    valBase += generatePOnly(i, 15)
                    valBase += generatePOnly(i, 115)
                    valBase += generatePOnly(i, 215)
                    valBase += generatePOnly(i, 315)
                    valBase += generatePOnly(i, 415)
                }
                "Acute ST-Elevation (STEMI)" -> {
                    valBase += generateBeat(i, 20, st = 0.65f, isWide = false, hasP = true, isTInverted = false)
                    valBase += generateBeat(i, 210, st = 0.65f, isWide = false, hasP = true, isTInverted = false)
                    valBase += generateBeat(i, 400, st = 0.65f, isWide = false, hasP = true, isTInverted = false)
                }
                else -> { // Default normal sinus
                    valBase += generateBeat(i, 30, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                    valBase += generateBeat(i, 230, st = 0.0f, isWide = false, hasP = true, isTInverted = false)
                }
            }
            points.add(EcgWavePoint(i, valBase, 0f))
        }
        return points
    }

    private fun generateBeat(
        index: Int,
        start: Int,
        st: Float,
        isWide: Boolean,
        hasP: Boolean,
        isTInverted: Boolean,
        prShift: Int = 0,
        customAmp: Float = 1.2f
    ): Float {
        val x = index - start
        var value = 0f

        // P wave
        if (hasP && x in 5..35) {
            val pCenter = 20f
            value += 0.12f * exp(-0.02f * (x - pCenter) * (x - pCenter))
        }

        // QRS complex (shifted by prShift)
        val qrsOffset = 42 + prShift
        val qrsStart = qrsOffset
        val qrsPeak = qrsOffset + 10
        val qrsEnd = qrsOffset + 20

        if (x in qrsStart..qrsEnd) {
            // R wave
            val rAmp = if (isWide) customAmp else 1.2f
            val rWidthCoeff = if (isWide) -0.04f else -0.15f
            value += rAmp * exp(rWidthCoeff * (x - qrsPeak) * (x - qrsPeak))

            // S wave
            if (x > qrsPeak + 2) {
                val sAmp = if (isWide) -0.4f else -0.25f
                value += sAmp * exp(-0.08f * (x - (qrsPeak + 5)) * (x - (qrsPeak + 5)))
            }
        }

        // T wave
        val tOffset = qrsOffset + 18
        val tStart = tOffset
        val tPeak = tOffset + 40
        val tEnd = tOffset + 80

        if (x in tStart..tEnd) {
            if (st > 0f) {
                // ST elevation
                value += st * exp(-0.003f * (x - (tStart + 10)) * (x - (tStart + 10)))
                // Hyperacute T
                value += 0.7f * exp(-0.005f * (x - tPeak) * (x - tPeak))
            } else {
                val tAmp = if (isTInverted) -0.3f else 0.28f
                value += tAmp * exp(-0.003f * (x - tPeak) * (x - tPeak))
            }
        }

        return value
    }

    private fun generatePOnly(index: Int, start: Int): Float {
        val x = index - start
        if (x in 5..35) {
            val pCenter = 20f
            return 0.12f * exp(-0.02f * (x - pCenter) * (x - pCenter))
        }
        return 0f
    }

    private fun generateEscapeBeat(index: Int, start: Int): Float {
        val x = index - start
        var value = 0f
        // Wide, slow ventricular escape beat (no P wave)
        val qrsPeak = 30
        if (x in 15..45) {
            value += 1.0f * exp(-0.05f * (x - qrsPeak) * (x - qrsPeak))
            if (x > qrsPeak + 3) {
                value -= 0.3f * exp(-0.04f * (x - (qrsPeak + 6)) * (x - (qrsPeak + 6)))
            }
        }
        if (x in 45..110) {
            value -= 0.35f * exp(-0.004f * (x - 75) * (x - 75)) // inverted T wave
        }
        return value
    }
}
