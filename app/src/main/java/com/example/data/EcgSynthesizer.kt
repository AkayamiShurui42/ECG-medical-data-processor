package com.example.data

import kotlin.math.exp
import kotlin.math.sin

object EcgSynthesizer {

    fun generateRhythm(type: String): InterpretResponse {
        return when (type) {
            "Normal Sinus Rhythm" -> InterpretResponse(
                rhythmType = "Normal Sinus Rhythm (NSR)",
                heartRate = 72,
                strokeVolume = 75f,
                ejectionFraction = 65f,
                lvCavitySize = 4.6f,
                findings = "Normal sinus rhythm at 72 BPM. Well-defined P-waves, narrow QRS complexes, and upright T-waves present throughout the tracing. No signs of ischemia or conduction blocks. Waveforms show high uniformity. All polarization tip points are physiologically valid, confirming a stable and healthy natural rhythm with zero clinical artifacts.",
                conditions = listOf("Physiologically Normal Rhythm"),
                segments = listOf(
                    WaveSegment("P", 35, 65, 50, 120, "Atrial depolarization. Upright and uniform."),
                    WaveSegment("QRS", 72, 92, 82, 80, "Ventricular depolarization. Standard amplitude and duration."),
                    WaveSegment("T", 120, 180, 150, 240, "Ventricular repolarization. Smooth, symmetric, and upright."),
                    WaveSegment("P", 235, 265, 250, 120, "Subsequent atrial depolarization."),
                    WaveSegment("QRS", 272, 292, 282, 80, "Subsequent ventricular depolarization."),
                    WaveSegment("T", 320, 380, 350, 240, "Subsequent ventricular repolarization.")
                ),
                artifacts = listOf(
                    ArtifactPoint(50, "none", "P-Wave Polarization", "Healthy atrial depolarization peak."),
                    ArtifactPoint(82, "none", "R-Wave Peak", "Healthy ventricular contraction peak."),
                    ArtifactPoint(150, "none", "T-Wave Peak", "Healthy ventricular relaxation peak."),
                    ArtifactPoint(250, "none", "P-Wave Polarization", "Healthy subsequent atrial peak."),
                    ArtifactPoint(282, "none", "R-Wave Peak", "Healthy subsequent ventricular peak.")
                ),
                literatureRefs = listOf(
                    LiteratureRepository.literatureList[0],
                    LiteratureRepository.literatureList[1]
                )
            )
            "Atrial Fibrillation" -> InterpretResponse(
                rhythmType = "Atrial Fibrillation (AFib)",
                heartRate = 125,
                strokeVolume = 54f,
                ejectionFraction = 48f,
                lvCavitySize = 5.3f,
                findings = "Atrial Fibrillation with a rapid ventricular response averaging 125 BPM. Baseline displays disorganized fibrillatory waves (f-waves) with a complete absence of coordinated P-waves. RR intervals are highly irregular. Tracing is impacted by high-frequency muscle tremor artifacts and patient motion (baseline wander). Distinguishing these noise factors is key to preventing false rhythm alerts, supported by AHA 2026 guidelines.",
                conditions = listOf("Atrial Fibrillation (AFib)", "Diastolic Dysfunction (HFpEF)"),
                segments = listOf(
                    WaveSegment("QRS", 40, 58, 49, 72, "Narrow QRS complex, irregular interval."),
                    WaveSegment("T", 80, 130, 105, 200, "Inverted T-wave suggesting rapid rate remodeling."),
                    WaveSegment("QRS", 185, 203, 194, 72, "Succeeding ventricular contraction."),
                    WaveSegment("T", 225, 275, 250, 200, "Ventricular repolarization phase."),
                    WaveSegment("QRS", 310, 328, 319, 72, "Subsequent irregular QRS contraction.")
                ),
                artifacts = listOf(
                    ArtifactPoint(49, "none", "R-Wave Peak", "True cardiac polarization peak."),
                    ArtifactPoint(105, "none", "T-Wave Peak", "True repolarization."),
                    ArtifactPoint(135, "absolute", "Muscle Tremor", "High-frequency somatic artifact from shivering or patient movement. Absolutely an artifact."),
                    ArtifactPoint(194, "none", "R-Wave Peak", "True cardiac polarization."),
                    ArtifactPoint(270, "potential", "Electrode Contact Noise", "Suspected baseline wander due to respiration or loose lead wire. Might be an artifact."),
                    ArtifactPoint(319, "none", "R-Wave Peak", "True cardiac polarization.")
                ),
                literatureRefs = listOf(
                    LiteratureRepository.literatureList[0],
                    LiteratureRepository.literatureList[2]
                )
            )
            "ST-Elevation Ischemia" -> InterpretResponse(
                rhythmType = "Acute ST-Elevation Ischemia (STEMI)",
                heartRate = 98,
                strokeVolume = 42f,
                ejectionFraction = 34f,
                lvCavitySize = 5.8f,
                findings = "Acute myocardial ischemia characterized by pronounced ST-segment elevation (>3mm) in the anterior lead layout. T-waves are hyperacute, and the QRS complex is widened. This pattern indicates complete coronary occlusion requiring immediate cardiac catheterization. Stroke volume and ejection fraction are severely reduced (HFrEF phenotype) reflecting acute ventricular distress, as per NIH guidelines.",
                conditions = listOf("Acute Anterior STEMI", "Ischemic Cardiomyopathy", "Systolic Heart Failure (HFrEF)"),
                segments = listOf(
                    WaveSegment("P", 25, 55, 40, 120, "Low-amplitude P-wave."),
                    WaveSegment("QRS", 65, 88, 76, 92, "Widened QRS complex representing injury current."),
                    WaveSegment("T", 88, 160, 120, 288, "Severely elevated ST segment and hyperacute T-wave."),
                    WaveSegment("P", 215, 245, 230, 120, "Subsequent low-amplitude P-wave."),
                    WaveSegment("QRS", 255, 278, 266, 92, "Subsequent widened QRS complex."),
                    WaveSegment("T", 278, 350, 310, 288, "Subsequent ST elevation.")
                ),
                artifacts = listOf(
                    ArtifactPoint(40, "none", "P-Wave", "Physiological atrial polarization."),
                    ArtifactPoint(76, "none", "R-Wave Peak", "Ventricular polarization."),
                    ArtifactPoint(100, "potential", "Lead Shift", "Minor high-frequency baseline shifts. Might be an artifact."),
                    ArtifactPoint(120, "none", "Hyperacute T-Wave", "Critical ST segment elevation marker. Confirming acute myocardial infarction."),
                    ArtifactPoint(266, "none", "R-Wave Peak", "Subsequent ventricular polarization.")
                ),
                literatureRefs = listOf(
                    LiteratureRepository.literatureList[3],
                    LiteratureRepository.literatureList[4]
                )
            )
            else -> InterpretResponse( // "Valvular & Congenital"
                rhythmType = "Congenital Right Hypertrophy & Valvular Stenosis",
                heartRate = 82,
                strokeVolume = 48f,
                ejectionFraction = 52f,
                lvCavitySize = 4.1f,
                findings = "Right ventricular hypertrophy pattern marked by tall R-waves in right-sided leads, severe right axis deviation, and secondary T-wave inversions. This reflects chronic pressure overload indicative of severe Aortic Valvular Stenosis. Additionally, a prolonged PR interval is suggestive of a first-degree AV block, commonly associated with congenital septal defects like ASD, based on 2021 NIH pediatric cardiology consensus.",
                conditions = listOf("Valvular Aortic Stenosis", "Right Ventricular Hypertrophy", "Atrial Septal Defect (ASD)"),
                segments = listOf(
                    WaveSegment("P", 20, 48, 34, 112, "Enlarged P-wave indicating right atrial overload."),
                    WaveSegment("QRS", 68, 92, 80, 96, "High-amplitude tall R-wave of ventricular hypertrophy."),
                    WaveSegment("T", 110, 170, 140, 240, "Deeply inverted asymmetric T-wave representing ventricular strain."),
                    WaveSegment("P", 220, 248, 234, 112, "Subsequent enlarged P-wave."),
                    WaveSegment("QRS", 268, 292, 280, 96, "Subsequent ventricular hypertrophy QRS.")
                ),
                artifacts = listOf(
                    ArtifactPoint(34, "none", "Enlarged P-Wave", "Atrial overload marker."),
                    ArtifactPoint(80, "none", "R-Wave Peak", "Ventricular hypertrophy spike."),
                    ArtifactPoint(140, "none", "Inverted T-Wave", "Ventricular strain pattern."),
                    ArtifactPoint(190, "absolute", "AC Interference", "60Hz powerline interference ripple. Absolutely an artifact."),
                    ArtifactPoint(280, "none", "R-Wave Peak", "Subsequent ventricular hypertrophy spike.")
                ),
                literatureRefs = listOf(
                    LiteratureRepository.literatureList[5],
                    LiteratureRepository.literatureList[3]
                )
            )
        }
    }

    /**
     * Synthesizes 500 continuous trace points representing a premium, realistic wave
     * depending on the rhythm selected.
     */
    fun synthesizeWaveform(type: String): List<EcgWavePoint> {
        val points = mutableListOf<EcgWavePoint>()
        val size = 500

        for (i in 0 until size) {
            var valBase = 0f
            var xaiWeight = 0.05f

            when (type) {
                "Normal Sinus Rhythm" -> {
                    // Two complete beats in 500 samples
                    // Beat 1 starts at sample 30, Beat 2 starts at sample 230
                    valBase += generateBeatValue(i, 30, isSTEMI = false, isAFib = false, isStenosis = false)
                    valBase += generateBeatValue(i, 230, isSTEMI = false, isAFib = false, isStenosis = false)
                    valBase += generateBeatValue(i, 430, isSTEMI = false, isAFib = false, isStenosis = false)

                    // Add very tiny background noise for realism
                    valBase += (sin(i * 1.5f) * 0.01f)

                    // Highlight P, QRS, T for XAI
                    if (i in 35..65 || i in 235..265) {
                        xaiWeight = 0.4f // P wave
                    } else if (i in 72..92 || i in 272..292) {
                        xaiWeight = 0.9f // QRS
                    } else if (i in 120..180 || i in 320..380) {
                        xaiWeight = 0.6f // T wave
                    }
                }
                "Atrial Fibrillation" -> {
                    // AFib has no distinct P waves, chaotic fibrillatory baseline, and irregular R peaks
                    // Sample beats at 35, 180, 305, 440 (irregular intervals)
                    valBase += generateBeatValue(i, 35, isSTEMI = false, isAFib = true, isStenosis = false)
                    valBase += generateBeatValue(i, 180, isSTEMI = false, isAFib = true, isStenosis = false)
                    valBase += generateBeatValue(i, 305, isSTEMI = false, isAFib = true, isStenosis = false)
                    valBase += generateBeatValue(i, 440, isSTEMI = false, isAFib = true, isStenosis = false)

                    // Fibrillatory waves (f-waves)
                    valBase += (sin(i * 0.8f) * 0.08f) + (sin(i * 1.9f) * 0.05f)

                    // Baseline wander artifact around index 240-290
                    if (i in 220..290) {
                        valBase += sin((i - 220) * 0.05f) * 0.35f
                    }

                    // Somatic tremor artifact (muscle noise) around index 120-150
                    if (i in 120..150) {
                        valBase += (sin(i * 4.0f) * 0.18f)
                    }

                    // XAI weights
                    if (i in 40..58 || i in 185..203 || i in 310..328) {
                        xaiWeight = 0.85f // Irregular QRS
                    } else if (i in 120..150 || i in 220..290) {
                        xaiWeight = 0.1f // Noise segments
                    } else if (i in 80..130 || i in 225..275) {
                        xaiWeight = 0.5f // T-wave
                    }
                }
                "ST-Elevation Ischemia" -> {
                    // STEMI: ST elevation and hyperacute T waves
                    valBase += generateBeatValue(i, 20, isSTEMI = true, isAFib = false, isStenosis = false)
                    valBase += generateBeatValue(i, 210, isSTEMI = true, isAFib = false, isStenosis = false)
                    valBase += generateBeatValue(i, 400, isSTEMI = true, isAFib = false, isStenosis = false)

                    // Minor baseline high-frequency noise (lead shift) around 90-110
                    if (i in 90..115) {
                        valBase += (sin(i * 3.5f) * 0.12f)
                    }

                    // XAI: ST segment elevation is critical
                    if (i in 78..140 || i in 268..330) {
                        xaiWeight = 0.98f // Hyperacute ST and T (clinical focus)
                    } else if (i in 65..78 || i in 255..268) {
                        xaiWeight = 0.7f // QRS
                    }
                }
                else -> { // "Valvular & Congenital"
                    // Right ventricular hypertrophy (tall R-waves) and inverted T-waves
                    valBase += generateBeatValue(i, 15, isSTEMI = false, isAFib = false, isStenosis = true)
                    valBase += generateBeatValue(i, 215, isSTEMI = false, isAFib = false, isStenosis = true)
                    valBase += generateBeatValue(i, 415, isSTEMI = false, isAFib = false, isStenosis = true)

                    // AC Powerline interference artifact (60Hz ripple) around 170-205
                    if (i in 170..205) {
                        valBase += sin(i * 1.2f) * 0.15f
                    }

                    // XAI: tall R-wave and inverted T-wave are indicators
                    if (i in 75..95 || i in 275..295) {
                        xaiWeight = 0.9f // Tall QRS
                    } else if (i in 110..160 || i in 310..360) {
                        xaiWeight = 0.8f // Strain T-wave
                    }
                }
            }

            points.add(EcgWavePoint(i, valBase, xaiWeight))
        }

        return points
    }

    private fun generateBeatValue(
        index: Int,
        startOffset: Int,
        isSTEMI: Boolean,
        isAFib: Boolean,
        isStenosis: Boolean
    ): Float {
        val x = index - startOffset
        var beatValue = 0f

        // P wave (absent in AFib)
        if (!isAFib) {
            val pStart = 5
            val pPeak = 20
            val pEnd = 35
            if (x in pStart..pEnd) {
                val pWidth = pEnd - pStart
                val pCenter = pStart + pWidth / 2f
                val pAmp = if (isStenosis) 0.22f else 0.12f // Overload enlarges P
                beatValue += pAmp * exp(-0.02f * (x - pCenter) * (x - pCenter))
            }
        }

        // PR Segment (flat line at 0)

        // QRS complex
        val qrsStart = 42
        val qrsPeak = 52
        val qrsEnd = 62
        if (x in qrsStart..qrsEnd) {
            // Q wave (downward spike)
            if (x < qrsPeak - 3) {
                beatValue -= 0.15f * exp(-0.1f * (x - (qrsStart + 3)) * (x - (qrsStart + 3)))
            }
            // R wave (sharp upward spike)
            val rAmp = if (isStenosis) 1.8f else if (isSTEMI) 0.9f else 1.2f // Stenosis/Hypertrophy has massive tall R-waves
            beatValue += rAmp * exp(-0.15f * (x - qrsPeak) * (x - qrsPeak))

            // S wave (deep downward spike)
            if (x > qrsPeak + 2) {
                val sAmp = if (isStenosis) -0.5f else -0.25f
                beatValue += sAmp * exp(-0.08f * (x - (qrsPeak + 5)) * (x - (qrsPeak + 5)))
            }
        }

        // ST Segment & T-Wave
        val tStart = 62
        val tPeak = 120
        val tEnd = 160
        if (x in tStart..tEnd) {
            if (isSTEMI) {
                // ST Segment Elevation starts immediately after S wave and merges with T wave
                val stAmp = 0.55f
                val stElevation = stAmp * exp(-0.003f * (x - 75) * (x - 75))
                beatValue += stElevation

                // Hyperacute elevated T wave
                val tAmp = 0.7f
                beatValue += tAmp * exp(-0.005f * (x - tPeak) * (x - tPeak))
            } else if (isStenosis) {
                // Secondary T-wave inversion (ventricular strain)
                val tAmp = -0.45f
                beatValue += tAmp * exp(-0.006f * (x - 110) * (x - 110))
            } else if (isAFib) {
                // Inverted T wave
                val tAmp = -0.2f
                beatValue += tAmp * exp(-0.004f * (x - 105) * (x - 105))
            } else {
                // Normal T wave (upright, smooth)
                val tAmp = 0.28f
                beatValue += tAmp * exp(-0.003f * (x - tPeak) * (x - tPeak))
            }
        }

        return beatValue
    }
}
