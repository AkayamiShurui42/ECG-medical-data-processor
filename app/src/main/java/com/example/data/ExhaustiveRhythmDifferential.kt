package com.example.data

/**
 * A broad rhythm differential. The intent is not to force one label but to preserve
 * plausible alternatives that remain compatible with measured waveform features.
 */
enum class DifferentialTier {
    BEST_FIT,
    STRONGLY_COMPATIBLE,
    COMPATIBLE,
    WEAKLY_COMPATIBLE,
    NOT_CURRENTLY_SUPPORTED,
    CONTRADICTED_BY_MEASURED_FEATURES
}

enum class RhythmMechanism {
    NORMAL_SINUS_RHYTHM,
    SINUS_BRADYCARDIA,
    SINUS_TACHYCARDIA,
    SINUS_ARRHYTHMIA,
    SINUS_PAUSE_OR_ARREST,
    SINOATRIAL_EXIT_BLOCK,

    PREMATURE_ATRIAL_COMPLEX,
    ATRIAL_BIGEMINY,
    ATRIAL_TRIGEMINY,
    FOCAL_ATRIAL_TACHYCARDIA,
    MULTIFOCAL_ATRIAL_TACHYCARDIA,
    ATRIAL_FIBRILLATION,
    TYPICAL_ATRIAL_FLUTTER,
    ATYPICAL_ATRIAL_FLUTTER,

    AVNRT,
    ORTHODROMIC_AVRT,
    ANTIDROMIC_AVRT,
    PERMANENT_JUNCTIONAL_RECIPROCATING_TACHYCARDIA,
    UNCLASSIFIED_REGULAR_NARROW_COMPLEX_SVT,
    UNCLASSIFIED_IRREGULAR_NARROW_COMPLEX_TACHYCARDIA,

    JUNCTIONAL_ESCAPE_RHYTHM,
    ACCELERATED_JUNCTIONAL_RHYTHM,
    JUNCTIONAL_TACHYCARDIA,

    PREMATURE_VENTRICULAR_COMPLEX,
    VENTRICULAR_BIGEMINY,
    VENTRICULAR_TRIGEMINY,
    COUPLET,
    TRIPLET,
    IDIOVENTRICULAR_RHYTHM,
    ACCELERATED_IDIOVENTRICULAR_RHYTHM,
    MONOMORPHIC_VENTRICULAR_TACHYCARDIA,
    POLYMORPHIC_VENTRICULAR_TACHYCARDIA,
    TORSADES_DE_POINTES,
    VENTRICULAR_FLUTTER,
    VENTRICULAR_FIBRILLATION,

    FIRST_DEGREE_AV_BLOCK,
    SECOND_DEGREE_AV_BLOCK_MOBITZ_I,
    SECOND_DEGREE_AV_BLOCK_MOBITZ_II,
    TWO_TO_ONE_AV_BLOCK,
    HIGH_GRADE_AV_BLOCK,
    COMPLETE_AV_BLOCK,

    PREEXCITATION_PATTERN,
    PREEXCITED_ATRIAL_FIBRILLATION,

    PACED_ATRIAL_RHYTHM,
    PACED_VENTRICULAR_RHYTHM,
    DUAL_CHAMBER_PACING,
    PACEMAKER_UNDERSENSING,
    PACEMAKER_OVERSENSING,
    FAILURE_TO_CAPTURE,

    SUPRAVENTRICULAR_TACHYCARDIA_WITH_ABERRANCY,
    RATE_RELATED_BUNDLE_BRANCH_ABERRANCY,
    BUNDLE_BRANCH_BLOCK_PATTERN,

    ARTIFACT_MIMICKING_TACHYCARDIA,
    ARTIFACT_MIMICKING_BRADYCARDIA_OR_PAUSE,
    UNDERSENSING_MIMICKING_PAUSE,
    OVERSENSING_MIMICKING_TACHYCARDIA,

    UNCLASSIFIED_CARDIAC_RHYTHM
}

enum class FeatureRelation {
    SUPPORTS,
    WEAKLY_SUPPORTS,
    NEUTRAL,
    WEAKLY_CONTRADICTS,
    CONTRADICTS,
    UNKNOWN
}

data class MeasuredRhythmFeatures(
    val modality: RecordingModality,
    val leadConfiguration: LeadConfiguration,
    val meanRateBpm: Double? = null,
    val minRateBpm: Double? = null,
    val maxRateBpm: Double? = null,
    val rrMeanMs: Double? = null,
    val rrSdMs: Double? = null,
    val rrCv: Double? = null,
    val regularityDescription: String? = null,
    val organizedPWaveFraction: Double? = null,
    val pBeforeQrsFraction: Double? = null,
    val pToQrsOneToOneFraction: Double? = null,
    val medianPrMs: Double? = null,
    val minPrMs: Double? = null,
    val maxPrMs: Double? = null,
    val prVariabilityMs: Double? = null,
    val medianQrsMs: Double? = null,
    val maxQrsMs: Double? = null,
    val medianQtMs: Double? = null,
    val medianQtcFridericiaMs: Double? = null,
    val abruptOnsetDetected: Boolean? = null,
    val abruptTerminationDetected: Boolean? = null,
    val gradualWarmupDetected: Boolean? = null,
    val gradualCooldownDetected: Boolean? = null,
    val avDissociationEvidence: Boolean? = null,
    val captureBeatEvidence: Boolean? = null,
    val fusionBeatEvidence: Boolean? = null,
    val compensatoryPauseFraction: Double? = null,
    val repeatedAtrialActivityRateBpm: Double? = null,
    val atrialToVentricularRatio: Double? = null,
    val beatMorphologyClusterCount: Int? = null,
    val dominantRWaveAmplitudeMv: Double? = null,
    val minimumRWaveAmplitudeMv: Double? = null,
    val rWaveAmplitudeCv: Double? = null,
    val dropoutFraction: Double? = null,
    val noiseFraction: Double? = null,
    val saturationFraction: Double? = null,
    val acceptedVentricularBeats: Int = 0,
    val ambiguousCandidateCount: Int = 0,
    val sourceDurationMs: Double? = null
)

data class FeatureAssessment(
    val feature: String,
    val observed: String,
    val expected: String,
    val relation: FeatureRelation,
    val explanation: String
)

data class RhythmDifferentialCandidate(
    val mechanism: RhythmMechanism,
    val displayName: String,
    val tier: DifferentialTier,
    val compatibilityScore: Float,
    val supportingFeatures: List<FeatureAssessment>,
    val conflictingFeatures: List<FeatureAssessment>,
    val unresolvedFeatures: List<String>,
    val testsOrObservationsThatWouldDifferentiate: List<String>,
    val modalityLimitations: List<String>,
    val artifactAlternatives: List<ArtifactMechanism> = emptyList(),
    val guidelineTopics: Set<GuidelineTopic> = emptySet()
)

data class ExhaustiveDifferentialResult(
    val primaryCandidate: RhythmDifferentialCandidate?,
    /** Every catalogued candidate evaluated for this signal, including contradicted ones. */
    val allCandidates: List<RhythmDifferentialCandidate>,
    val compatibleCandidates: List<RhythmDifferentialCandidate>,
    val artifactMimics: List<RhythmDifferentialCandidate>,
    val missingHighValueObservations: List<String>,
    val recordingContextMissing: List<String>,
    val notes: List<String>
)

/**
 * Core catalog. Feature-scoring algorithms can evolve independently of this list.
 * This keeps rare/alternative mechanisms from disappearing merely because a
 * classifier was not originally trained to emit them.
 */
object RhythmMechanismCatalog {
    val displayNames: Map<RhythmMechanism, String> = mapOf(
        RhythmMechanism.NORMAL_SINUS_RHYTHM to "Normal sinus rhythm",
        RhythmMechanism.SINUS_BRADYCARDIA to "Sinus bradycardia",
        RhythmMechanism.SINUS_TACHYCARDIA to "Sinus tachycardia",
        RhythmMechanism.SINUS_ARRHYTHMIA to "Sinus arrhythmia",
        RhythmMechanism.SINUS_PAUSE_OR_ARREST to "Sinus pause / sinus arrest",
        RhythmMechanism.SINOATRIAL_EXIT_BLOCK to "Sinoatrial exit block",
        RhythmMechanism.PREMATURE_ATRIAL_COMPLEX to "Premature atrial complex",
        RhythmMechanism.ATRIAL_BIGEMINY to "Atrial bigeminy",
        RhythmMechanism.ATRIAL_TRIGEMINY to "Atrial trigeminy",
        RhythmMechanism.FOCAL_ATRIAL_TACHYCARDIA to "Focal atrial tachycardia",
        RhythmMechanism.MULTIFOCAL_ATRIAL_TACHYCARDIA to "Multifocal atrial tachycardia",
        RhythmMechanism.ATRIAL_FIBRILLATION to "Atrial fibrillation",
        RhythmMechanism.TYPICAL_ATRIAL_FLUTTER to "Typical atrial flutter",
        RhythmMechanism.ATYPICAL_ATRIAL_FLUTTER to "Atypical atrial flutter",
        RhythmMechanism.AVNRT to "AV nodal re-entrant tachycardia (AVNRT)",
        RhythmMechanism.ORTHODROMIC_AVRT to "Orthodromic AV re-entrant tachycardia (AVRT)",
        RhythmMechanism.ANTIDROMIC_AVRT to "Antidromic AV re-entrant tachycardia (AVRT)",
        RhythmMechanism.PERMANENT_JUNCTIONAL_RECIPROCATING_TACHYCARDIA to "Permanent junctional reciprocating tachycardia (PJRT)",
        RhythmMechanism.UNCLASSIFIED_REGULAR_NARROW_COMPLEX_SVT to "Regular narrow-complex SVT, mechanism unresolved",
        RhythmMechanism.UNCLASSIFIED_IRREGULAR_NARROW_COMPLEX_TACHYCARDIA to "Irregular narrow-complex tachycardia, mechanism unresolved",
        RhythmMechanism.JUNCTIONAL_ESCAPE_RHYTHM to "Junctional escape rhythm",
        RhythmMechanism.ACCELERATED_JUNCTIONAL_RHYTHM to "Accelerated junctional rhythm",
        RhythmMechanism.JUNCTIONAL_TACHYCARDIA to "Junctional tachycardia",
        RhythmMechanism.PREMATURE_VENTRICULAR_COMPLEX to "Premature ventricular complex",
        RhythmMechanism.VENTRICULAR_BIGEMINY to "Ventricular bigeminy",
        RhythmMechanism.VENTRICULAR_TRIGEMINY to "Ventricular trigeminy",
        RhythmMechanism.COUPLET to "Ventricular couplet",
        RhythmMechanism.TRIPLET to "Ventricular triplet",
        RhythmMechanism.IDIOVENTRICULAR_RHYTHM to "Idioventricular rhythm",
        RhythmMechanism.ACCELERATED_IDIOVENTRICULAR_RHYTHM to "Accelerated idioventricular rhythm",
        RhythmMechanism.MONOMORPHIC_VENTRICULAR_TACHYCARDIA to "Monomorphic ventricular tachycardia",
        RhythmMechanism.POLYMORPHIC_VENTRICULAR_TACHYCARDIA to "Polymorphic ventricular tachycardia",
        RhythmMechanism.TORSADES_DE_POINTES to "Torsades de pointes",
        RhythmMechanism.VENTRICULAR_FLUTTER to "Ventricular flutter",
        RhythmMechanism.VENTRICULAR_FIBRILLATION to "Ventricular fibrillation",
        RhythmMechanism.FIRST_DEGREE_AV_BLOCK to "First-degree AV block",
        RhythmMechanism.SECOND_DEGREE_AV_BLOCK_MOBITZ_I to "Second-degree AV block (Mobitz I/Wenckebach)",
        RhythmMechanism.SECOND_DEGREE_AV_BLOCK_MOBITZ_II to "Second-degree AV block (Mobitz II)",
        RhythmMechanism.TWO_TO_ONE_AV_BLOCK to "2:1 AV block",
        RhythmMechanism.HIGH_GRADE_AV_BLOCK to "High-grade AV block",
        RhythmMechanism.COMPLETE_AV_BLOCK to "Complete AV block",
        RhythmMechanism.PREEXCITATION_PATTERN to "Ventricular pre-excitation pattern",
        RhythmMechanism.PREEXCITED_ATRIAL_FIBRILLATION to "Pre-excited atrial fibrillation",
        RhythmMechanism.PACED_ATRIAL_RHYTHM to "Atrial paced rhythm",
        RhythmMechanism.PACED_VENTRICULAR_RHYTHM to "Ventricular paced rhythm",
        RhythmMechanism.DUAL_CHAMBER_PACING to "Dual-chamber paced rhythm",
        RhythmMechanism.PACEMAKER_UNDERSENSING to "Pacemaker undersensing pattern",
        RhythmMechanism.PACEMAKER_OVERSENSING to "Pacemaker oversensing pattern",
        RhythmMechanism.FAILURE_TO_CAPTURE to "Failure to capture",
        RhythmMechanism.SUPRAVENTRICULAR_TACHYCARDIA_WITH_ABERRANCY to "SVT with aberrant ventricular conduction",
        RhythmMechanism.RATE_RELATED_BUNDLE_BRANCH_ABERRANCY to "Rate-related bundle branch aberrancy",
        RhythmMechanism.BUNDLE_BRANCH_BLOCK_PATTERN to "Bundle branch block pattern",
        RhythmMechanism.ARTIFACT_MIMICKING_TACHYCARDIA to "Non-cardiac artifact mimicking tachycardia",
        RhythmMechanism.ARTIFACT_MIMICKING_BRADYCARDIA_OR_PAUSE to "Non-cardiac artifact mimicking bradycardia/pause",
        RhythmMechanism.UNDERSENSING_MIMICKING_PAUSE to "Undersensing mimicking pause/asystole",
        RhythmMechanism.OVERSENSING_MIMICKING_TACHYCARDIA to "Oversensing mimicking tachycardia",
        RhythmMechanism.UNCLASSIFIED_CARDIAC_RHYTHM to "Unclassified cardiac rhythm"
    )

    fun all(): List<RhythmMechanism> = RhythmMechanism.entries
}
