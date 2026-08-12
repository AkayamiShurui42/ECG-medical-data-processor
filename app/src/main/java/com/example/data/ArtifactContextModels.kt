package com.example.data

/**
 * Context-aware artifact differentiation.
 *
 * IMPORTANT: These structures separate patient/device context from the actual
 * waveform evidence. Context may alter the differential or confidence, but it
 * must never fabricate a rhythm diagnosis or overwrite the measured trace.
 */

enum class ArtifactMechanism {
    R_WAVE_UNDERSENSING,
    P_WAVE_OVERSENSING,
    T_WAVE_OVERSENSING,
    MULTIPLE_SENSING,
    MYOPOTENTIAL_OVERSENSING,
    MOTION_ARTIFACT,
    SIGNAL_DROPOUT,
    AMPLIFIER_SATURATION,
    BASELINE_WANDER,
    POWERLINE_INTERFERENCE,
    ELECTRODE_CONTACT_NOISE,
    ECTOPY_MISCLASSIFICATION,
    EXTRACTION_ARTIFACT,
    UNKNOWN
}

enum class EvidenceStrength {
    GUIDELINE,
    MULTICENTER_STUDY,
    COHORT_STUDY,
    CASE_SERIES,
    CASE_REPORT,
    USER_REPORTED_CONTEXT,
    HYPOTHESIS_ONLY
}

enum class ContextDirection {
    INCREASES_ARTIFACT_LIKELIHOOD,
    DECREASES_ARTIFACT_LIKELIHOOD,
    INCREASES_UNDERSENSING_RISK,
    INCREASES_OVERSENSING_RISK,
    CHANGES_EXPECTED_MORPHOLOGY,
    CONTEXT_ONLY,
    UNKNOWN
}

enum class ClinicalConditionFlag {
    SMALL_LV_CAVITY,
    LOW_LV_MASS,
    LV_HYPERTROPHY,
    LV_DILATION,
    ATRIAL_ENLARGEMENT,
    LOW_EJECTION_FRACTION,
    PRESERVED_EJECTION_FRACTION,
    ATRIAL_FIBRILLATION_HISTORY,
    ATRIAL_FLUTTER_HISTORY,
    SUPRAVENTRICULAR_TACHYCARDIA_HISTORY,
    VENTRICULAR_TACHYCARDIA_HISTORY,
    PREMATURE_ATRIAL_COMPLEXES,
    PREMATURE_VENTRICULAR_COMPLEXES,
    CONDUCTION_DISEASE,
    PACEMAKER_PRESENT,
    ICD_PRESENT,
    CARDIOMYOPATHY,
    ISCHEMIC_HEART_DISEASE,
    VALVULAR_DISEASE,
    CONGENITAL_HEART_DISEASE,
    AUTONOMIC_DYSFUNCTION,
    OTHER
}

data class StructuralHeartContext(
    val ejectionFractionPercent: Double? = null,
    val lvInternalDiameterDiastoleMm: Double? = null,
    val lvMassG: Double? = null,
    val lvMassIndexGPerM2: Double? = null,
    val leftAtrialVolumeIndexMlPerM2: Double? = null,
    val rightAtrialVolumeIndexMlPerM2: Double? = null,
    val strokeVolumeMl: Double? = null,
    val strokeVolumeIndexMlPerM2: Double? = null,
    val conditions: Set<ClinicalConditionFlag> = emptySet(),
    val notes: String = ""
)

data class PatientSensingContext(
    val bodyMassIndex: Double? = null,
    val bodyPosition: String? = null,
    val activityAtRecording: String? = null,
    val symptomsAtRecording: List<String> = emptyList(),
    val knownEctopyBurdenPercent: Double? = null,
    val knownAfBurdenPercent: Double? = null,
    val structuralHeart: StructuralHeartContext = StructuralHeartContext()
)

data class DeviceSensingContext(
    val modality: RecordingModality,
    val manufacturer: String? = null,
    val model: String? = null,
    val implantSite: String? = null,
    val sensingVectorLengthMm: Double? = null,
    val implantDepthMm: Double? = null,
    val deviceToHeartDistanceMm: Double? = null,
    val programmedSensitivityMv: Double? = null,
    val programmedFilterDescription: String? = null,
    val nominalRWaveAmplitudeMv: Double? = null,
    val notes: String = ""
)

data class SignalSensingContext(
    val measuredRWaveAmplitudeMv: Double? = null,
    val measuredPWaveAmplitudeMv: Double? = null,
    val measuredTWaveAmplitudeMv: Double? = null,
    val beatToBeatRWaveAmplitudeVariationPercent: Double? = null,
    val signalDropoutFraction: Double? = null,
    val baselineNoiseMvRms: Double? = null,
    val clippingOrSaturationDetected: Boolean = false,
    val repeatedDeflectionsPerCardiacCycle: Int? = null,
    val sourceWindowStartMs: Double? = null,
    val sourceWindowEndMs: Double? = null
)

data class ArtifactEvidenceReference(
    val title: String,
    val organizationOrJournal: String,
    val year: Int,
    val doiOrIdentifier: String? = null,
    val evidenceStrength: EvidenceStrength,
    val relevance: String
)

data class ContextualArtifactRule(
    val id: String,
    val mechanism: ArtifactMechanism,
    val appliesTo: Set<RecordingModality>,
    val direction: ContextDirection,
    val summary: String,
    val criteriaDescription: String,
    val references: List<ArtifactEvidenceReference>,
    val isHardDiagnosticRule: Boolean = false
)

data class ArtifactAssessment(
    val mechanism: ArtifactMechanism,
    val likelihood: Float,
    val direction: ContextDirection,
    val reasonsFor: List<String>,
    val reasonsAgainst: List<String>,
    val missingInformation: List<String>,
    val references: List<ArtifactEvidenceReference>
)

/**
 * Evidence-backed starter rules. These are intentionally conservative.
 * Numeric cutoffs are treated as heuristics for review, not automatic diagnoses.
 */
object ArtifactDifferentiationKnowledgeBase {

    val ilrLowRWaveReference = ArtifactEvidenceReference(
        title = "Insertable cardiac monitor with a long sensing vector: impact of obesity on sensing quality and safety",
        organizationOrJournal = "Europace",
        year = 2023,
        doiOrIdentifier = "PMCID: PMC10071510",
        evidenceStrength = EvidenceStrength.COHORT_STUDY,
        relevance = "Reports lower R-wave amplitude as a sensing limitation and describes ~0.3 mV as a generally accepted minimum level for adequate R-wave detection."
    )

    val ilrAccuracyReference = ArtifactEvidenceReference(
        title = "Diagnostic accuracy of R-wave detection by insertable cardiac monitors",
        organizationOrJournal = "Pacing and Clinical Electrophysiology",
        year = 2020,
        doiOrIdentifier = "10.1111/pace.13912",
        evidenceStrength = EvidenceStrength.COHORT_STUDY,
        relevance = "False arrhythmia alerts were frequently related to inadequate R-wave sensing; undersensing predominated and commonly produced false bradycardia/pause alerts."
    )

    val ilrPerformanceReference = ArtifactEvidenceReference(
        title = "Performance of implantable loop recorders: role of R vector and detection algorithms",
        organizationOrJournal = "Journal of Electrocardiology",
        year = 2021,
        doiOrIdentifier = "10.1016/j.jelectrocard.2021.08.009",
        evidenceStrength = EvidenceStrength.COHORT_STUDY,
        relevance = "Lower R-wave amplitudes and R-vector characteristics were associated with incorrect ILR detections/artifacts."
    )

    val ilrFalseAlarmSystematicReview = ArtifactEvidenceReference(
        title = "False-positive alarms in patients with implantable loop recorder followed by remote monitoring: a systematic review",
        organizationOrJournal = "Pacing and Clinical Electrophysiology",
        year = 2024,
        doiOrIdentifier = "10.1111/pace.14941",
        evidenceStrength = EvidenceStrength.MULTICENTER_STUDY,
        relevance = "False-positive ILR alerts are common; AF false positives are frequently caused by premature atrial or ventricular complexes."
    )

    val ambulatoryConsensus = ArtifactEvidenceReference(
        title = "2017 ISHNE-HRS Expert Consensus Statement on Ambulatory ECG and External Cardiac Monitoring/Telemetry",
        organizationOrJournal = "ISHNE / Heart Rhythm Society",
        year = 2017,
        evidenceStrength = EvidenceStrength.GUIDELINE,
        relevance = "Defines interpretation of ambulatory ECG in the context of acquisition technology, technical limitations and recording modality."
    )

    val remoteDeviceConsensus = ArtifactEvidenceReference(
        title = "2023 HRS/EHRA/APHRS/LAHRS Expert Consensus Statement on Practical Management of the Remote Device Clinic",
        organizationOrJournal = "HRS / EHRA / APHRS / LAHRS",
        year = 2023,
        evidenceStrength = EvidenceStrength.GUIDELINE,
        relevance = "Supports device-specific alert management and review rather than treating automated detections as final diagnoses."
    )

    val rules = listOf(
        ContextualArtifactRule(
            id = "ilr-low-r-wave-undersensing",
            mechanism = ArtifactMechanism.R_WAVE_UNDERSENSING,
            appliesTo = setOf(RecordingModality.IMPLANTABLE_LOOP_RECORDER),
            direction = ContextDirection.INCREASES_UNDERSENSING_RISK,
            summary = "Low-amplitude R waves increase concern for missed QRS detections in an ILR/ICM.",
            criteriaDescription = "Review especially when measured or device-reported R-wave amplitude is near/below the device's expected sensing range. Around 0.3 mV is an evidence-supported review heuristic, not a universal diagnostic cutoff.",
            references = listOf(ilrLowRWaveReference, ilrAccuracyReference, ilrPerformanceReference)
        ),
        ContextualArtifactRule(
            id = "ilr-pause-brady-undersensing",
            mechanism = ArtifactMechanism.R_WAVE_UNDERSENSING,
            appliesTo = setOf(RecordingModality.IMPLANTABLE_LOOP_RECORDER),
            direction = ContextDirection.INCREASES_UNDERSENSING_RISK,
            summary = "An ILR pause/bradycardia alert should be checked for signal dropout or missed low-amplitude R waves before being accepted as a true pause.",
            criteriaDescription = "Compare the raw subcutaneous electrogram with device markers; look for continuing ventricular morphology that the device failed to mark.",
            references = listOf(ilrAccuracyReference, remoteDeviceConsensus)
        ),
        ContextualArtifactRule(
            id = "ilr-af-ectopy-misclassification",
            mechanism = ArtifactMechanism.ECTOPY_MISCLASSIFICATION,
            appliesTo = setOf(RecordingModality.IMPLANTABLE_LOOP_RECORDER),
            direction = ContextDirection.INCREASES_ARTIFACT_LIKELIHOOD,
            summary = "Frequent PACs/PVCs can produce false-positive device-detected AF episodes.",
            criteriaDescription = "Review irregularity for premature beats, compensatory pauses and preserved organized atrial/ventricular morphology rather than accepting the device AF label alone.",
            references = listOf(ilrFalseAlarmSystematicReview, remoteDeviceConsensus)
        ),
        ContextualArtifactRule(
            id = "modality-specific-interpretation",
            mechanism = ArtifactMechanism.UNKNOWN,
            appliesTo = RecordingModality.entries.toSet(),
            direction = ContextDirection.CHANGES_EXPECTED_MORPHOLOGY,
            summary = "Artifact criteria must be interpreted in the context of the acquisition technology and available lead/vector information.",
            criteriaDescription = "A single subcutaneous vector, surface single lead, telemetry strip and 12-lead ECG have different expected morphology and different failure modes.",
            references = listOf(ambulatoryConsensus)
        ),
        ContextualArtifactRule(
            id = "small-heart-context-only",
            mechanism = ArtifactMechanism.UNKNOWN,
            appliesTo = RecordingModality.entries.toSet(),
            direction = ContextDirection.CONTEXT_ONLY,
            summary = "Small LV cavity size or low LV mass may be clinically relevant but is not currently treated as an independent artifact rule.",
            criteriaDescription = "Store and display structural-heart measurements beside the tracing. Do not automatically increase or decrease artifact probability unless an evidence-backed sensing relationship is also present (for example low measured R-wave amplitude).",
            references = emptyList()
        )
    )
}
