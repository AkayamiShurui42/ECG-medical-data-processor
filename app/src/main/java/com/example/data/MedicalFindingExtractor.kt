package com.example.data

/**
 * Offline, reviewable extraction of clinical concepts from user-supplied text.
 * This is an indexer, NOT a diagnostic classifier or clinical NLP model.
 * The original evidence and its source are always available for checking.
 */
data class RecordFinding(
    val id: String,
    val topic: String,
    val evidence: String,
    val source: String,
    val status: String = "MENTIONED",
    val category: String = "CONDITION",
    val query: String = topic
)

data class ResearchPaper(
    val title: String,
    val authors: String,
    val journal: String,
    val date: String,
    val pmid: String,
    val doi: String,
    val abstractExcerpt: String,
    val url: String,
    val publicationTypes: String
)

data class FindingLiterature(
    val findingId: String,
    val papers: List<ResearchPaper>,
    val checkedAt: Long,
    val error: String = ""
)

data class UploadedRecordSummary(
    val name: String,
    val importedAt: Long,
    val extractionNote: String
)

object MedicalFindingExtractor {
    private data class Rule(val label: String, val expression: Regex, val category: String, val query: String)

    private fun rule(label: String, pattern: String, category: String = "CONDITION", query: String = label) =
        Rule(label, Regex(pattern, RegexOption.IGNORE_CASE), category, query)

    // Pattern registry covers common terminology, not every possible finding in medicine.
    // Unknown conclusions appear as review items instead of being silently discarded.
    private val rules = listOf(
        rule("Heart failure", """\b(?:congestive )?heart failure\b|\bHFpEF\b|\bHFrEF\b"""),
        rule("Low cardiac output", """\blow(?: cardiac)? output\b|\blow.flow\b|\breduced cardiac index\b"""),
        rule("Diastolic dysfunction", """\bdiastolic dysfunction\b|\bimpaired relaxation\b"""),
        rule("Systolic dysfunction", """\bsystolic dysfunction\b"""),
        rule("Cardiomyopathy", """\bcardiomyopath(?:y|ies)\b"""),
        rule("Atrial fibrillation", """\b(?:atrial fibrillation|AFib|AF)\b""", query="atrial fibrillation"),
        rule("Atrial flutter", """\batrial flutter\b"""),
        rule("Premature ventricular contractions", """\bPVCs?\b|\bpremature ventricular\b"""),
        rule("Ventricular tachycardia", """\bventricular tachycardia\b|\bNSVT\b"""),
        rule("Atrioventricular block", """\b(?:AV block|atrioventricular block|heart block)\b"""),
        rule("Short PR interval", """\bshort PR\b|\bPR shortening\b"""),
        rule("Long QT", """\blong QT\b|\bQT prolongation\b"""),
        rule("Syncope", """\b(?:syncope|fainting|presyncope|near.syncope)\b"""),
        rule("Orthostatic hypotension", """\borthostatic hypotension\b|\bpostural hypotension\b"""),
        rule("Orthostatic intolerance", """\borthostatic intolerance\b"""),
        rule("POTS", """\bPOTS\b|\bpostural orthostatic tachycardia\b"""),
        rule("Dysautonomia", """\bdysautonomia\b|\bautonomic dysfunction\b|\bbaroreflex failure\b"""),
        rule("Hypertension", """\bhypertension\b|\bhigh blood pressure\b"""),
        rule("Hypotension", """\bhypotension\b|\blow blood pressure\b"""),
        rule("Pulmonary hypertension", """\bpulmonary hypertension\b"""),
        rule("Coronary artery disease", """\bcoronary artery disease\b|\bCAD\b"""),
        rule("Myocardial infarction", """\bmyocardial infarction\b|\bheart attack\b|\bSTEMI\b|\bNSTEMI\b"""),
        rule("Myocardial ischemia", """\bmyocardial ischemia\b|\bcardiac ischemia\b"""),
        rule("Carotid stenosis", """\bcarotid (?:artery )?stenosis\b"""),
        rule("Vertebral artery insufficiency", """\bvertebral artery insufficiency\b|\bBow Hunter'?s syndrome\b"""),
        rule("Aortic stenosis", """\baortic stenosis\b"""),
        rule("Aortic regurgitation", """\baortic (?:regurgitation|insufficiency)\b"""),
        rule("Mitral regurgitation", """\bmitral regurgitation\b"""),
        rule("Mitral stenosis", """\bmitral stenosis\b"""),
        rule("Tricuspid regurgitation", """\btricuspid regurgitation\b"""),
        rule("Congenital heart disease", """\bcongenital heart (?:defect|disease)\b"""),
        rule("Sleep apnea", """\b(?:obstructive|central|mixed)?\s*sleep apn(?:ea|oea)\b|\bOSA\b|\bCSA\b"""),
        rule("Hypoxemia", """\bhypoxemia\b|\blow oxygen saturation\b|\bdesaturation\b"""),
        rule("Dyspnea", """\bdyspn(?:ea|oea)\b|\bshortness of breath\b"""),
        rule("Chest pain", """\bchest pain\b|\bangina\b"""),
        rule("Palpitations", """\bpalpitations?\b"""),
        rule("Peripheral edema", """\bperipheral edema\b|\bleg swelling\b"""),
        rule("Diabetes mellitus", """\bdiabetes(?: mellitus)?\b|\bT2DM\b"""),
        rule("Chronic kidney disease", """\bchronic kidney disease\b|\bCKD\b"""),
        rule("Acute kidney injury", """\bacute kidney injury\b|\bAKI\b"""),
        rule("Anemia", """\ban(?:a|ae)mia\b"""),
        rule("Thrombocytopenia", """\bthrombocytopenia\b"""),
        rule("Leukocytosis", """\bleukocytosis\b"""),
        rule("Hyperkalemia", """\bhyperkalemia\b"""),
        rule("Hypokalemia", """\bhypokalemia\b"""),
        rule("Hyperlipidemia", """\bhyperlipid(?:emia|aemia)\b|\bdyslipidemia\b"""),
        rule("Hypothyroidism", """\bhypothyroidism\b"""),
        rule("Hyperthyroidism", """\bhyperthyroidism\b"""),
        rule("Thromboembolism", """\b(?:DVT|deep vein thrombosis|pulmonary embolism|PE)\b"""),
        rule("Stroke", """\b(?:ischemic stroke|hemorrhagic stroke|cerebrovascular accident|CVA)\b"""),
        rule("Transient ischemic attack", """\btransient ischemic attack\b|\bTIA\b"""),
        rule("Seizure", """\bseizures?\b|\bepilepsy\b"""),
        rule("Migraine", """\bmigraine\b"""),
        rule("Asthma", """\basthma\b"""),
        rule("COPD", """\bCOPD\b|\bchronic obstructive pulmonary disease\b"""),
        rule("Autoimmune disease", """\bautoimmune disease\b|\bsystemic lupus\b|\brheumatoid arthritis\b"""),
        rule("Infection", """\binfection\b|\bsepsis\b"""),
        rule("Inflammation", """\binflammation\b|\bvasculitis\b"""),
        rule("Vitamin D deficiency", """\bvitamin D deficiency\b"""),
        // Measurements are indexed verbatim; no abnormal/normal label is inferred from a value.
        rule("Stroke volume index", """\b(?:SVI|stroke volume index)\b\s*[:=]?\s*(?:[0-9]+(?:\.[0-9]+)?)?""","MEASUREMENT","stroke volume index reference range prognosis"),
        rule("Cardiac index", """\b(?:cardiac index|CI)\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","cardiac index hemodynamic"),
        rule("Ejection fraction", """\b(?:EF|LVEF|ejection fraction)\b\s*(?:[:=]|of)?\s*[0-9]+(?:\.[0-9]+)?\s*%?""","MEASUREMENT","left ventricular ejection fraction interpretation"),
        rule("LV mass index", """\b(?:LVMI|LV mass index|left ventricular mass index)\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","left ventricular mass index echocardiography"),
        rule("LV end diastolic dimension", """\b(?:LVIDd|LVEDD|LV end.diastolic diameter)\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","left ventricular end diastolic dimension echo"),
        rule("E/e′", """\bE/e[′']?\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","E/e prime filling pressure echocardiography"),
        rule("NT-proBNP", """\bNT.proBNP\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","NT-proBNP heart failure"),
        rule("BNP", """\bBNP\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","BNP heart failure"),
        rule("Troponin", """\btroponin(?: I| T)?\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","cardiac troponin interpretation"),
        rule("Heart rate", """\b(?:heart rate|HR)\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","heart rate clinical interpretation"),
        rule("PR interval", """\bPR interval\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","PR interval electrocardiogram"),
        rule("QTc interval", """\bQTc\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","corrected QT interval ECG"),
        rule("QRS duration", """\bQRS(?: duration)?\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","QRS duration ECG"),
        rule("Oxygen saturation", """\b(?:SpO2|SaO2|oxygen saturation)\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?\s*%?""","MEASUREMENT","oxygen saturation hypoxemia"),
        rule("Blood pressure", """\b(?:BP|blood pressure)\b\s*[:=]?\s*[0-9]{2,3}\s*/\s*[0-9]{2,3}""","MEASUREMENT","blood pressure clinical interpretation"),
        rule("Hemoglobin", """\b(?:hemoglobin|haemoglobin|Hgb)\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","hemoglobin laboratory reference"),
        rule("eGFR", """\beGFR\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","estimated glomerular filtration rate"),
        rule("Creatinine", """\bcreatinine\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","serum creatinine clinical interpretation"),
        rule("Potassium", """\bpotassium\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","serum potassium clinical interpretation"),
        rule("Sodium", """\bsodium\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","serum sodium clinical interpretation"),
        rule("HbA1c", """\b(?:HbA1c|A1c)\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","hemoglobin A1c diabetes"),
        rule("White blood cell count", """\b(?:WBC|white blood cell count)\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","white blood cell count reference"),
        rule("Platelets", """\bplatelets?\b\s*[:=]?\s*[0-9]+(?:\.[0-9]+)?""","MEASUREMENT","platelet count clinical interpretation"),
    )

    private val sectionLine = Regex(
        """^\s*(?:assessment|impression|diagnosis|diagnoses|problem list|clinical findings|conclusion|abnormal findings|plan)\s*[:\-]\s*(.{3,190})""",
        RegexOption.IGNORE_CASE
    )
    private val negation = Regex("""\b(?:no evidence of|no history of|denies|without|negative for|ruled out|does not have)\b""",RegexOption.IGNORE_CASE)
    private val uncertainty = Regex("""\b(?:possible|suspected|rule out|r/o|consider|concern for|question of|may have)\b""",RegexOption.IGNORE_CASE)
    private val family = Regex("""\b(?:family history|mother|father|sibling)\b""",RegexOption.IGNORE_CASE)

    fun extract(text: String, source: String): List<RecordFinding> {
        val output = linkedMapOf<String, RecordFinding>()
        val lines = text.split('\n', '\r').asSequence().map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.length in 4..600 }.take(25000)
        lines.forEachIndexed { index, line ->
            val found = rules.filter { it.expression.containsMatchIn(line) }
            val status = when {
                negation.containsMatchIn(line) -> "NEGATED"
                family.containsMatchIn(line) -> "FAMILY_HISTORY"
                uncertainty.containsMatchIn(line) -> "POSSIBLE"
                else -> "MENTIONED"
            }
            found.forEach { rule ->
                val hit = rule.expression.find(line)?.value?.trim().orEmpty()
                val suffix = if (rule.category == "MEASUREMENT") ": $hit" else ""
                val id = (source+"|"+index+"|"+rule.label+"|"+hit).hashCode().toUInt().toString(16)
                output[id] = RecordFinding(id,rule.label+suffix,line.take(400),source,status,rule.category,rule.query)
            }
            // Don't manufacture diagnoses out of unrecognized impressions: surface
            // them for human review before a generic research search is attempted.
            if (found.isEmpty()) {
                val match = sectionLine.find(line)
                if (match != null) {
                    val phrase = match.groupValues[1].take(150)
                    val id = (source+"|"+index+"|unmapped").hashCode().toUInt().toString(16)
                    output[id] = RecordFinding(id,"Review unrecognized clinical finding",phrase,source,"NEEDS_REVIEW","UNMAPPED","")
                }
            }
        }
        return output.values.toList()
    }
}
