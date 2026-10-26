package com.example.data

import org.junit.Assert.*
import org.junit.Test

class MedicalFindingExtractorTest {
    @Test fun detectsMeasurementsWithEvidenceAndSource() {
        val results=MedicalFindingExtractor.extract(
            "LVEF: 55%\nStroke volume index: 25 mL/m2\nPR interval: 108 ms", "echo.txt")
        assertTrue(results.any { it.category=="MEASUREMENT" && it.topic.startsWith("Ejection fraction") })
        assertTrue(results.any { it.topic.startsWith("Stroke volume index") })
        assertTrue(results.all { it.source=="echo.txt" })
        assertTrue(results.all { it.evidence.isNotEmpty() })
    }
    @Test fun doesNotTurnNegatedConditionIntoConfirmedDiagnosis() {
        val results=MedicalFindingExtractor.extract("No evidence of atrial fibrillation","report.txt")
        assertTrue(results.any { it.topic=="Atrial fibrillation" && it.status=="NEGATED" })
    }
    @Test fun keepsPossibleAndFamilyHistoryDistinct() {
        val results=MedicalFindingExtractor.extract(
            "Possible cardiomyopathy\nFamily history: father with myocardial infarction", "visit.txt")
        assertTrue(results.any { it.topic=="Cardiomyopathy" && it.status=="POSSIBLE" })
        assertTrue(results.any { it.topic=="Myocardial infarction" && it.status=="FAMILY_HISTORY" })
    }
    @Test fun unknownAssessmentIsReviewable() {
        val r=MedicalFindingExtractor.extract("Assessment: complex unexplained positional symptoms", "note.txt")
        assertEquals("NEEDS_REVIEW",r.single().status)
        assertEquals("",r.single().query)
    }
    @Test fun doesNotInferDiagnosisFromOrdinaryProse() {
        val r=MedicalFindingExtractor.extract("Patient states they came to appointment on Tuesday.","note.txt")
        assertTrue(r.isEmpty())
    }
}
