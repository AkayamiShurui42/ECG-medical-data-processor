package com.example.data

/**
 * Cross-checks a person's indexed record without treating record mentions or
 * research search hits as diagnostic proof.
 *
 * This intentionally produces review questions, NOT diagnoses.
 */
data class CaseResearchObservation(
    val title: String,
    val description: String,
    val evidenceSources: List<String>,
    val kind: String
)

object ClinicalResearchSynthesis {
    private fun isPresent(f: RecordFinding): Boolean =
        f.status != "NEGATED" && f.status != "FAMILY_HISTORY" &&
        f.status != "NEEDS_REVIEW"

    private fun number(findings: List<RecordFinding>, prefix: String): Double? {
        val matching=findings.filter { isPresent(it) && it.category=="MEASUREMENT" &&
            it.topic.startsWith(prefix,ignoreCase=true) }
        for (finding in matching) {
            val matchingText=finding.topic.substringAfter(':',"")
            val number=Regex("""\d+(?:\.\d+)?""").find(matchingText)?.value?.toDoubleOrNull()
            if(number!=null) return number
        }
        return null
    }

    fun evaluate(
        findings: List<RecordFinding>,
        literature: Map<String,FindingLiterature>
    ): List<CaseResearchObservation> {
        val outputs=mutableListOf<CaseResearchObservation>()
        if(findings.isEmpty()) return outputs
        val active=findings.filter(::isPresent)
        fun has(topic: String) = active.any { it.topic.startsWith(topic,ignoreCase=true) }
        fun sources(vararg topics:String): List<String> =
            active.filter { f-> topics.any { f.topic.startsWith(it,ignoreCase=true) } }
                .map { it.source }.distinct()

        val svi=number(active,"Stroke volume index")
        val ef=number(active,"Ejection fraction")
        if(svi!=null && svi<35 && ef!=null && ef>=50) {
            outputs.add(CaseResearchObservation(
                "Low indexed stroke volume alongside preserved ejection fraction",
                "The entered records contain an SVI below an illustrative 35 mL/m² low-flow boundary and LVEF at least 50%. Preserved EF does not establish normal forward output. Verify that these values were acquired under comparable conditions and consider the clinical explanation, preload, loading conditions and objective HF evidence. This is not a confirmed diagnosis.",
                sources("Stroke volume index","Ejection fraction"),
                "HEMODYNAMIC_REVIEW"))
        } else if (svi!=null && svi<35) {
            outputs.add(CaseResearchObservation(
                "Reduced stroke-volume-index research flag",
                "The record contains SVI below an illustrative 35 mL/m² research boundary. This measurement by itself cannot confirm HF, its stage, or the cause of low flow.",
                sources("Stroke volume index"),"HEMODYNAMIC_REVIEW"))
        }

        if(has("Syncope") && (has("Orthostatic hypotension") ||
                has("Orthostatic intolerance") || has("POTS") || has("Dysautonomia"))) {
            outputs.add(CaseResearchObservation(
                "Positional symptoms and circulatory regulation",
                "Syncope or near-syncope and an orthostatic/autonomic finding appear in the records. An evaluation may need time-linked posture, BP, HR, medications and differential testing. Association in a record is not proof of causation.",
                sources("Syncope","Orthostatic hypotension","Orthostatic intolerance","POTS","Dysautonomia"),
                "RELATIONSHIP_TO_REVIEW"))
        }

        if(has("Sleep apnea") && has("Hypoxemia")) {
            outputs.add(CaseResearchObservation(
                "Sleep-disordered breathing with low-oxygen finding",
                "Sleep apnea and low oxygen are both described. Clarify whether desaturations occurred during sleep, their quality, timing and correlation with a diagnostic sleep study before attributing cause.",
                sources("Sleep apnea","Hypoxemia"),"RELATIONSHIP_TO_REVIEW"))
        }

        val conflicts=findings.groupBy { it.topic.substringBefore(':').trim().lowercase() }
            .filterValues { group -> group.any { it.status=="NEGATED" } &&
                group.any { it.status=="MENTIONED" || it.status=="POSSIBLE" } }
        conflicts.forEach { (topic, group) ->
            outputs.add(CaseResearchObservation(
                "Conflicting record statements: "+topic,
                "Some records describe this finding while others negate it. Check dates, test modalities, and whether a rule-out statement was mistaken for a historical diagnosis.",
                group.map { it.source }.distinct(),"EVIDENCE_CONFLICT"))
        }

        val pending=findings.filter { it.query.isBlank() }
        if(pending.isNotEmpty()) outputs.add(CaseResearchObservation(
            "Unmapped clinical statements need review",
            pending.size.toString()+" clinical statement(s) were not mapped to known concepts. They cannot be guaranteed to receive research coverage until individually reviewed or entered as medical topics.",
            pending.map { it.source }.distinct(),"MISSING_MAPPING"))

        val searched=findings.filter { it.query.isNotBlank() }
        val completed=searched.count { f-> (literature[f.id]?.checkedAt ?: 0L)>0L }
        outputs.add(CaseResearchObservation(
            "Evidence coverage",
            completed.toString()+" of "+searched.size+" mapped finding entries have cached publication search results. A search hit is not proof that a study included a similar person or reached a clinically actionable conclusion. Compare methods, populations, control groups, confidence intervals, exclusions and guideline strength.",
            emptyList(),"COVERAGE"))
        return outputs
    }
}
