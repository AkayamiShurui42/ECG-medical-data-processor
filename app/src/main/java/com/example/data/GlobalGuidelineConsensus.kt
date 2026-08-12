package com.example.data

/**
 * International, region-neutral guideline synthesis.
 *
 * The app should not treat one country/society as authoritative by default.
 * Guideline text is not embedded here. We store source metadata and independently
 * structured recommendation facts, then cite the original source in the UI.
 */
enum class GuidelineRegion {
    GLOBAL,
    EUROPE,
    NORTH_AMERICA,
    CANADA,
    UNITED_STATES,
    ASIA_PACIFIC,
    JAPAN,
    AUSTRALIA_NEW_ZEALAND,
    LATIN_AMERICA,
    UNITED_KINGDOM,
    PEDIATRIC_INTERNATIONAL,
    OTHER
}

enum class GuidelineOrganization {
    ESC,
    EHRA,
    HRS,
    ACC,
    AHA,
    APHRS,
    LAHRS,
    JCS,
    JHRS,
    CCS,
    CHRS,
    NATIONAL_HEART_FOUNDATION_AUSTRALIA,
    CSANZ,
    NICE,
    ISHNE,
    PACES,
    EACTS,
    ESO,
    OTHER
}

enum class GuidelineDocumentType {
    CLINICAL_PRACTICE_GUIDELINE,
    FOCUSED_UPDATE,
    EXPERT_CONSENSUS,
    SCIENTIFIC_STATEMENT,
    PRACTICE_STANDARD,
    POSITION_STATEMENT,
    SYSTEMATIC_REVIEW,
    OTHER
}

enum class GuidelineTopic {
    ATRIAL_FIBRILLATION,
    ATRIAL_FLUTTER,
    SUPRAVENTRICULAR_TACHYCARDIA,
    VENTRICULAR_ARRHYTHMIA,
    BRADYCARDIA_CONDUCTION,
    SYNCOPE,
    AMBULATORY_ECG,
    IMPLANTABLE_LOOP_RECORDER,
    CARDIAC_DEVICE_MONITORING,
    ECG_INTERPRETATION,
    ARTIFACT_DIFFERENTIATION,
    PREEXCITATION,
    INHERITED_ARRHYTHMIA,
    PEDIATRIC_ARRHYTHMIA,
    SPORTS_ARRHYTHMIA,
    PREGNANCY_ARRHYTHMIA,
    OTHER
}

enum class RecommendationDirection {
    RECOMMENDS,
    FAVORS,
    NEUTRAL_OR_INSUFFICIENT,
    DISCOURAGES,
    RECOMMENDS_AGAINST
}

enum class ConsensusState {
    CONSENSUS,
    MAJORITY_ALIGNMENT,
    REGIONAL_VARIATION,
    DIRECT_CONFLICT,
    INSUFFICIENT_SOURCES
}

enum class EvidenceCertainty {
    HIGH,
    MODERATE,
    LOW,
    VERY_LOW,
    NOT_GRADED,
    UNKNOWN
}

data class GuidelineSource(
    val id: String,
    val title: String,
    val publicationYear: Int,
    val organizations: Set<GuidelineOrganization>,
    val regions: Set<GuidelineRegion>,
    val topics: Set<GuidelineTopic>,
    val documentType: GuidelineDocumentType,
    val doiOrIdentifier: String? = null,
    val officialSourceLabel: String,
    val isCurrentInItsJurisdiction: Boolean = true,
    /** Keep copyrighted guideline prose external; store facts/citations only. */
    val externalReferenceOnly: Boolean = true
)

/**
 * One normalized factual position extracted from a guideline/source.
 * Do not store copied paragraphs. recommendationSummary should be an original,
 * concise factual normalization suitable for comparison across societies.
 */
data class GuidelinePosition(
    val criterionId: String,
    val sourceId: String,
    val topic: GuidelineTopic,
    val populationScope: String,
    val modalityScope: Set<RecordingModality> = emptySet(),
    val recommendationDirection: RecommendationDirection,
    val recommendationSummary: String,
    val numericThreshold: Double? = null,
    val thresholdUnit: String? = null,
    val evidenceCertainty: EvidenceCertainty = EvidenceCertainty.UNKNOWN,
    val sourceRecommendationClass: String? = null,
    val sourceEvidenceLevel: String? = null,
    val sourceLocation: String? = null,
    val notes: String = ""
)

data class GuidelineAgreementGroup(
    val normalizedPosition: RecommendationDirection,
    val sourceIds: List<String>,
    val summaries: List<String>
)

data class HarmonizedGuidelineResult(
    val criterionId: String,
    val state: ConsensusState,
    val participatingSourceIds: List<String>,
    val agreementGroups: List<GuidelineAgreementGroup>,
    val sharedConclusion: String?,
    val disagreements: List<String>,
    val newestPublicationYear: Int?,
    val notes: List<String> = emptyList()
)

/**
 * A transparent harmonizer. It never lets a US/European/Asian source win merely
 * because of geography. It first identifies agreement/disagreement and leaves
 * conflicting recommendations visible to the user.
 */
object GuidelineConsensusEngine {
    fun harmonize(
        criterionId: String,
        positions: List<GuidelinePosition>,
        sources: Map<String, GuidelineSource>
    ): HarmonizedGuidelineResult {
        val matching = positions.filter { it.criterionId == criterionId }
        if (matching.isEmpty()) {
            return HarmonizedGuidelineResult(
                criterionId = criterionId,
                state = ConsensusState.INSUFFICIENT_SOURCES,
                participatingSourceIds = emptyList(),
                agreementGroups = emptyList(),
                sharedConclusion = null,
                disagreements = emptyList(),
                newestPublicationYear = null,
                notes = listOf("No guideline positions have been entered for this criterion.")
            )
        }

        val groups = matching.groupBy { it.recommendationDirection }
            .map { (direction, items) ->
                GuidelineAgreementGroup(
                    normalizedPosition = direction,
                    sourceIds = items.map { it.sourceId }.distinct(),
                    summaries = items.map { it.recommendationSummary }.distinct()
                )
            }
            .sortedByDescending { it.sourceIds.size }

        val regions = matching.flatMap { sources[it.sourceId]?.regions.orEmpty() }.toSet()
        val directionalGroups = groups.filter {
            it.normalizedPosition != RecommendationDirection.NEUTRAL_OR_INSUFFICIENT
        }

        val state = when {
            matching.size < 2 -> ConsensusState.INSUFFICIENT_SOURCES
            directionalGroups.size <= 1 -> ConsensusState.CONSENSUS
            groups.firstOrNull()?.sourceIds?.size ?: 0 > matching.size / 2 -> ConsensusState.MAJORITY_ALIGNMENT
            regions.size > 1 -> ConsensusState.REGIONAL_VARIATION
            else -> ConsensusState.DIRECT_CONFLICT
        }

        val topGroup = groups.firstOrNull()
        val sharedConclusion = when (state) {
            ConsensusState.CONSENSUS,
            ConsensusState.MAJORITY_ALIGNMENT -> topGroup?.summaries?.firstOrNull()
            else -> null
        }

        val disagreements = if (groups.size <= 1) emptyList() else {
            groups.drop(1).flatMap { group ->
                group.summaries.map { summary ->
                    "${group.normalizedPosition}: $summary (${group.sourceIds.joinToString()})"
                }
            }
        }

        return HarmonizedGuidelineResult(
            criterionId = criterionId,
            state = state,
            participatingSourceIds = matching.map { it.sourceId }.distinct(),
            agreementGroups = groups,
            sharedConclusion = sharedConclusion,
            disagreements = disagreements,
            newestPublicationYear = matching.mapNotNull { sources[it.sourceId]?.publicationYear }.maxOrNull(),
            notes = listOf(
                "No geographic region receives automatic precedence.",
                "Conflicting recommendations remain visible instead of being averaged into a false consensus."
            )
        )
    }
}

/**
 * Source registry seed. This is metadata only; recommendation facts are entered
 * separately and linked back to the original publications.
 */
object InternationalGuidelineSourceRegistry {
    val sources: List<GuidelineSource> = listOf(
        GuidelineSource(
            id = "ESC_AF_2024",
            title = "2024 ESC Guidelines for the management of atrial fibrillation",
            publicationYear = 2024,
            organizations = setOf(GuidelineOrganization.ESC, GuidelineOrganization.EHRA, GuidelineOrganization.EACTS),
            regions = setOf(GuidelineRegion.EUROPE),
            topics = setOf(GuidelineTopic.ATRIAL_FIBRILLATION, GuidelineTopic.ATRIAL_FLUTTER),
            documentType = GuidelineDocumentType.CLINICAL_PRACTICE_GUIDELINE,
            doiOrIdentifier = "10.1093/eurheartj/ehae176",
            officialSourceLabel = "European Society of Cardiology"
        ),
        GuidelineSource(
            id = "JCS_JHRS_ARRHYTHMIA_2024",
            title = "JCS/JHRS 2024 Guideline Focused Update on Management of Cardiac Arrhythmias",
            publicationYear = 2024,
            organizations = setOf(GuidelineOrganization.JCS, GuidelineOrganization.JHRS),
            regions = setOf(GuidelineRegion.JAPAN, GuidelineRegion.ASIA_PACIFIC),
            topics = setOf(
                GuidelineTopic.ATRIAL_FIBRILLATION,
                GuidelineTopic.SUPRAVENTRICULAR_TACHYCARDIA,
                GuidelineTopic.VENTRICULAR_ARRHYTHMIA,
                GuidelineTopic.BRADYCARDIA_CONDUCTION
            ),
            documentType = GuidelineDocumentType.FOCUSED_UPDATE,
            doiOrIdentifier = "10.1253/circj.CJ-24-0073",
            officialSourceLabel = "Japanese Circulation Society / Japanese Heart Rhythm Society"
        ),
        GuidelineSource(
            id = "HRS_EHRA_APHRS_LAHRS_REMOTE_2023",
            title = "2023 HRS/EHRA/APHRS/LAHRS Expert Consensus Statement on Practical Management of the Remote Device Clinic",
            publicationYear = 2023,
            organizations = setOf(
                GuidelineOrganization.HRS,
                GuidelineOrganization.EHRA,
                GuidelineOrganization.APHRS,
                GuidelineOrganization.LAHRS
            ),
            regions = setOf(GuidelineRegion.GLOBAL),
            topics = setOf(GuidelineTopic.CARDIAC_DEVICE_MONITORING, GuidelineTopic.IMPLANTABLE_LOOP_RECORDER),
            documentType = GuidelineDocumentType.EXPERT_CONSENSUS,
            officialSourceLabel = "HRS / EHRA / APHRS / LAHRS"
        ),
        GuidelineSource(
            id = "ESC_VA_2022",
            title = "2022 ESC Guidelines for ventricular arrhythmias and prevention of sudden cardiac death",
            publicationYear = 2022,
            organizations = setOf(GuidelineOrganization.ESC, GuidelineOrganization.EHRA),
            regions = setOf(GuidelineRegion.EUROPE),
            topics = setOf(GuidelineTopic.VENTRICULAR_ARRHYTHMIA, GuidelineTopic.INHERITED_ARRHYTHMIA),
            documentType = GuidelineDocumentType.CLINICAL_PRACTICE_GUIDELINE,
            officialSourceLabel = "European Society of Cardiology"
        ),
        GuidelineSource(
            id = "ESC_SVT_2019",
            title = "2019 ESC Guidelines on supraventricular tachycardia",
            publicationYear = 2019,
            organizations = setOf(GuidelineOrganization.ESC, GuidelineOrganization.EHRA),
            regions = setOf(GuidelineRegion.EUROPE),
            topics = setOf(GuidelineTopic.SUPRAVENTRICULAR_TACHYCARDIA, GuidelineTopic.PREEXCITATION),
            documentType = GuidelineDocumentType.CLINICAL_PRACTICE_GUIDELINE,
            officialSourceLabel = "European Society of Cardiology"
        ),
        GuidelineSource(
            id = "CCS_CHRS_AF_2020",
            title = "2020 CCS/CHRS Comprehensive Guidelines for the Management of Atrial Fibrillation",
            publicationYear = 2020,
            organizations = setOf(GuidelineOrganization.CCS, GuidelineOrganization.CHRS),
            regions = setOf(GuidelineRegion.CANADA, GuidelineRegion.NORTH_AMERICA),
            topics = setOf(GuidelineTopic.ATRIAL_FIBRILLATION),
            documentType = GuidelineDocumentType.CLINICAL_PRACTICE_GUIDELINE,
            officialSourceLabel = "Canadian Cardiovascular Society / Canadian Heart Rhythm Society"
        ),
        GuidelineSource(
            id = "NHFA_CSANZ_AF_2018",
            title = "Australian Clinical Guidelines for the Diagnosis and Management of Atrial Fibrillation",
            publicationYear = 2018,
            organizations = setOf(GuidelineOrganization.NATIONAL_HEART_FOUNDATION_AUSTRALIA, GuidelineOrganization.CSANZ),
            regions = setOf(GuidelineRegion.AUSTRALIA_NEW_ZEALAND),
            topics = setOf(GuidelineTopic.ATRIAL_FIBRILLATION),
            documentType = GuidelineDocumentType.CLINICAL_PRACTICE_GUIDELINE,
            officialSourceLabel = "National Heart Foundation of Australia / Cardiac Society of Australia and New Zealand"
        )
    )

    fun asMap(): Map<String, GuidelineSource> = sources.associateBy { it.id }
}
