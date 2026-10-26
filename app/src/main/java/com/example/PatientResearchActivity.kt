package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class PatientResearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(Modifier.fillMaxSize()) { PatientResearchScreen() }
            }
        }
    }
}

private fun readableTime(epoch: Long): String =
    if (epoch <= 0L) "Not checked" else DateFormat.getDateTimeInstance().format(Date(epoch))

@Composable
private fun PatientResearchScreen() {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val store=remember { MedicalResearchStore(context.applicationContext) }
    var state by remember { mutableStateOf(store.read()) }
    var importBusy by remember { mutableStateOf(false) }
    var researchBusy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Import clinical documents to begin.") }
    var newTopic by remember { mutableStateOf("") }
    var showRecords by remember { mutableStateOf(true) }
    var showFindings by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun update(next: MedicalResearchWorkspace) {
        state=next
        store.write(next)
    }

    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                importBusy=true
                var success=0
                val errors=mutableListOf<String>()
                try {
                    for (uri in uris) {
                        try {
                            val record=PatientDocumentReader.import(context,uri)
                            val extracted=withContext(Dispatchers.Default) {
                                MedicalFindingExtractor.extract(record.text,record.filename)
                            }
                            val merged=state.findings.associateBy { it.id }.toMutableMap()
                            extracted.forEach { merged[it.id]=it }
                            update(state.copy(
                                documents=state.documents + UploadedRecordSummary(
                                    record.filename,System.currentTimeMillis(),record.extractionNote),
                                findings=merged.values.toList()))
                            success++
                        } catch (ex: Exception) {
                            errors.add((uri.lastPathSegment ?: "File") + ": " +
                                (ex.message ?: "Could not read document"))
                        }
                    }
                    message="Imported "+success+" documents; indexed "+state.findings.size+" findings. " +
                        (if(errors.isNotEmpty()) "Problems: "+errors.take(3).joinToString(" | ")
                            else "Verify extracted wording and OCR against originals.")
                } finally { importBusy=false }
            }
        }
    }

    suspend fun refreshResearch() {
        if (!state.allowResearchQueries || researchBusy) return
        val grouped=state.findings.filter { it.query.isNotBlank() }
            .groupBy { it.query.trim().lowercase() }
        if(grouped.isEmpty()) { message="No mapped research topics. Add or review a finding."; return }
        researchBusy=true
        var checked=0
        var failures=0
        try {
            for ((_, duplicates) in grouped) {
                val representative=duplicates.first()
                progress="Searching "+(checked+1)+" of "+grouped.size+": "+representative.topic
                try {
                    val result=PatientLiteratureSearch.search(representative)
                    val updated=state.literature.toMutableMap()
                    duplicates.forEach { updated[it.id]=result.copy(findingId=it.id) }
                    update(state.copy(literature=updated))
                } catch (ex: Exception) {
                    failures++
                    val updated=state.literature.toMutableMap()
                    duplicates.forEach {
                        val previous=updated[it.id]
                        updated[it.id]=FindingLiterature(
                            it.id,previous?.papers.orEmpty(),previous?.checkedAt ?: 0L,
                            "Refresh failed: " + (ex.message ?: "network unavailable"))
                    }
                    update(state.copy(literature=updated))
                }
                checked++
                delay(350)
            }
            message="Checked "+checked+" distinct topics; "+failures+" searches failed. Article matches do not establish diagnoses."
        } finally { researchBusy=false;progress="" }
    }

    val researchKey=state.findings.joinToString("|") { it.id + ":" + it.query }
    LaunchedEffect(state.allowResearchQueries,researchKey) {
        if(state.allowResearchQueries && researchKey.isNotEmpty()) refreshResearch()
    }

    if(showClearConfirm) {
        AlertDialog(
            onDismissRequest={ showClearConfirm=false },
            title={ Text("Delete local clinical workspace?") },
            text={ Text("This permanently deletes extracted evidence snippets, research cache and finding history inside the app. Original documents remain wherever you saved them.") },
            confirmButton={ TextButton(onClick={
                store.clear()
                state=MedicalResearchWorkspace()
                message="Local workspace deleted."
                showClearConfirm=false
            }) { Text("Delete workspace") } },
            dismissButton={ TextButton(onClick={showClearConfirm=false}) { Text("Cancel") } }
        )
    }

    LazyColumn(
        modifier=Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Medical Record Research",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("Upload clinical documents, extract findings, and search fresh medical literature for each mapped topic. No ECG tracing investigation.")
            Text("Research assistance only. Neither a record mention nor a matching paper proves a diagnosis. This is not a validated diagnostic or prescribing system.",
                style=MaterialTheme.typography.bodySmall)
        }
        item {
            Card {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("1. Upload medical documents",fontWeight=FontWeight.Bold)
                    Text("PDFs, scanned images, DOCX, TXT, CSV, JSON, XML and Markdown. Multiple imports supported. Source excerpts are stored privately on the device.",
                        style=MaterialTheme.typography.bodySmall)
                    Button(onClick={
                        picker.launch(arrayOf("application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "image/*","text/*","application/json","application/xml","application/octet-stream"))
                    },enabled=!importBusy) { Text(if(importBusy) "Reading documents…" else "Choose medical documents") }
                    Text("PDF OCR reads at most 60 pages and 20 MB per file. Import notes flag incomplete records; review the original for extraction mistakes.",
                        style=MaterialTheme.typography.bodySmall)
                    Text(message,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("2. Verify medical findings",fontWeight=FontWeight.Bold)
                    Text("Suspected, family-history, negated and measured findings are labeled, not automatically treated as diagnoses. Unknown clinical statements require manual review.",
                        style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value=newTopic,onValueChange={newTopic=it.take(120)},
                        label={Text("Add a missing clinical research topic")},
                        placeholder={Text("e.g. reduced venous return")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    Button(enabled=newTopic.trim().length in 3..120,onClick={
                        val topic=newTopic.trim()
                        val id="manual-"+System.nanoTime().toString(16)
                        update(state.copy(findings=state.findings+RecordFinding(
                            id,topic,"Manually entered research topic",
                            "User-entered topic","POSSIBLE","USER_REVIEW",topic)))
                        newTopic=""
                    }) { Text("Add finding") }
                    Text("Indexed findings: "+state.findings.size+" · documents: "+state.documents.size)
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("3. Search latest research on opening",fontWeight=FontWeight.Bold)
                    Text("Each time this screen opens it searches every mapped topic in Europe PMC, including PubMed-indexed studies. Search topics leave the phone, but original documents and patient excerpts are never transmitted by this module. A topic can still be sensitive.",
                        style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked=state.allowResearchQueries,
                            onCheckedChange={checked->update(state.copy(allowResearchQueries=checked))})
                        Text("I consent to sending medical-topic search queries to Europe PMC.",
                            modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    }
                    Button(enabled=state.allowResearchQueries && !researchBusy && state.findings.any {it.query.isNotBlank()},
                        onClick={scope.launch { refreshResearch() }}) {
                        Text(if(researchBusy) "Research running…" else "Refresh every finding now")
                    }
                    if(progress.isNotBlank()) {
                        LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                        Text(progress,style=MaterialTheme.typography.bodySmall)
                    }
                    Text("Research is refreshed per topic, with publication links, timestamps, visible errors, cached results and source evidence. No retrieved study silently changes clinical rules.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { TextButton(onClick={showRecords=!showRecords}) { Text(if(showRecords) "Hide imported documents" else "Show imported documents") } }
        if(showRecords) {
            items(state.documents) { record ->
                Card { Column(Modifier.padding(12.dp)) {
                    Text(record.name,fontWeight=FontWeight.Bold)
                    Text("Imported: "+readableTime(record.importedAt),style=MaterialTheme.typography.bodySmall)
                    Text(record.extractionNote,style=MaterialTheme.typography.bodySmall)
                } }
            }
        }
        item { TextButton(onClick={showFindings=!showFindings}) { Text(if(showFindings) "Hide findings and literature" else "Show findings and literature") } }
        if(showFindings) {
            items(state.findings,key={it.id}) { finding ->
                val literature=state.literature[finding.id]
                Card {
                    Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(finding.topic,fontWeight=FontWeight.Bold)
                        Text("Record category: "+finding.status+" / "+finding.category,style=MaterialTheme.typography.labelSmall)
                        Text("Source: "+finding.source,style=MaterialTheme.typography.bodySmall)
                        Text("Evidence: "+finding.evidence,style=MaterialTheme.typography.bodySmall)
                        if(finding.query.isBlank()) Text("Unmapped: review and add this topic manually.",style=MaterialTheme.typography.bodySmall)
                        else Text("Search terms: "+finding.query,style=MaterialTheme.typography.bodySmall)
                        if(literature!=null) {
                            Text("Last successful research: "+readableTime(literature.checkedAt),style=MaterialTheme.typography.labelSmall)
                            if(literature.error.isNotBlank()) Text(literature.error,
                                color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                            if(literature.papers.isEmpty() && literature.error.isBlank())
                                Text("No indexed article matched; this does not prove absence of evidence.")
                            literature.papers.forEach { paper ->
                                HorizontalDivider()
                                Text(paper.title,fontWeight=FontWeight.SemiBold)
                                Text(paper.journal+" · "+paper.date+" · PMID "+paper.pmid,
                                    style=MaterialTheme.typography.bodySmall)
                                if(paper.publicationTypes.isNotBlank()) Text("Publication type: "+paper.publicationTypes,
                                    style=MaterialTheme.typography.bodySmall)
                                if(paper.abstractExcerpt.isNotBlank()) Text(paper.abstractExcerpt.take(500),
                                    style=MaterialTheme.typography.bodySmall)
                                TextButton(onClick={
                                    val safe=runCatching { Uri.parse(paper.url) }.getOrNull()
                                    if(safe?.scheme=="https" && safe.host in setOf("pubmed.ncbi.nlm.nih.gov","europepmc.org"))
                                        context.startActivity(Intent(Intent.ACTION_VIEW,safe))
                                }) { Text("Read source article") }
                            }
                        } else Text(if(state.allowResearchQueries) "Awaiting research refresh." else "Live search not authorized.",
                            style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={
                            update(state.copy(findings=state.findings.filterNot { it.id==finding.id },
                                literature=state.literature-finding.id))
                        }) { Text("Remove finding") }
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Text("Clinical interpretation boundaries",fontWeight=FontWeight.Bold)
            Text("Literature matches are research leads, not individualized diagnostic decisions. Study design, effect size, age applicability, exclusions, contradictory records, and missing tests still need critical review. Confirm decisions with a qualified clinician.",
                style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={showClearConfirm=true}) { Text("Delete local clinical workspace") }
            Spacer(Modifier.height(30.dp))
        }
    }
}
