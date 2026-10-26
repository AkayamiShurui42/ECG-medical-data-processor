package com.example.data

import org.junit.Assert.*
import org.junit.Test

class ClinicalResearchSynthesisTest {
    private fun f(topic:String,value:String,status:String="MENTIONED",id:String=topic) =
        RecordFinding(id,topic+": "+value,topic+": "+value,"test-record",status,"MEASUREMENT",topic)

    @Test fun flagsLowSviWithPreservedEfWithoutDiagnosing() {
        val observations=ClinicalResearchSynthesis.evaluate(
            listOf(f("Stroke volume index","25"),f("Ejection fraction","55")),emptyMap())
        assertTrue(observations.any { it.title.contains("preserved ejection fraction") })
        assertFalse(observations.any { it.title.contains("Stage C confirmed") })
    }

    @Test fun reconcilesOpposingRecordMentions() {
        val mentions=listOf(
            RecordFinding("1","Atrial fibrillation","Atrial fibrillation","old.txt","MENTIONED"),
            RecordFinding("2","Atrial fibrillation","No atrial fibrillation","new.txt","NEGATED")
        )
        val observations=ClinicalResearchSynthesis.evaluate(mentions,emptyMap())
        assertTrue(observations.any { it.kind=="EVIDENCE_CONFLICT" })
    }

    @Test fun negativeHemodynamicValueDoesNotGenerateLowFlowInsight() {
        val observations=ClinicalResearchSynthesis.evaluate(
            listOf(f("Stroke volume index","25","NEGATED")),emptyMap())
        assertFalse(observations.any { it.kind=="HEMODYNAMIC_REVIEW" })
    }
}
