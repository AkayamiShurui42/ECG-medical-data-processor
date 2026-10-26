package com.example.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Only extracted review items and citation metadata are retained in app-private
 * storage. Original document bytes are never copied to permanent storage.
 */
data class MedicalResearchWorkspace(
    val documents: List<UploadedRecordSummary> = emptyList(),
    val findings: List<RecordFinding> = emptyList(),
    val literature: Map<String,FindingLiterature> = emptyMap(),
    val allowResearchQueries: Boolean = false
)

class MedicalResearchStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir,"medical_record_research.json"))

    fun read(): MedicalResearchWorkspace {
        return try {
        if (!file.baseFile.exists()) return MedicalResearchWorkspace()
        val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        val documents = json.optJSONArray("documents") ?: JSONArray()
        val findings = json.optJSONArray("findings") ?: JSONArray()
        val articles = json.optJSONObject("literature") ?: JSONObject()
        val docList=(0 until documents.length()).map { i ->
            val a=documents.getJSONObject(i)
            UploadedRecordSummary(a.optString("name"),a.optLong("importedAt"),a.optString("note"))
        }
        val findingList=(0 until findings.length()).map { i ->
            val a=findings.getJSONObject(i)
            RecordFinding(a.optString("id"),a.optString("topic"),a.optString("evidence"),
                a.optString("source"),a.optString("status"),a.optString("category"),
                a.optString("query"))
        }
        val cache=mutableMapOf<String,FindingLiterature>()
        articles.keys().forEach { key ->
            val a=articles.getJSONObject(key)
            val papers=a.optJSONArray("papers") ?: JSONArray()
            cache[key]=FindingLiterature(key,(0 until papers.length()).map { i ->
                val p=papers.getJSONObject(i)
                ResearchPaper(p.optString("title"),p.optString("authors"),p.optString("journal"),
                    p.optString("date"),p.optString("pmid"),p.optString("doi"),
                    p.optString("abstract"),p.optString("url"),p.optString("types"))
            },a.optLong("checkedAt"),a.optString("error"))
        }
        MedicalResearchWorkspace(docList,findingList,cache,json.optBoolean("consent",false))
    } catch (_: Exception) {
        // An invalid store must never be interpreted as a healthy or diagnosed patient.
        MedicalResearchWorkspace()
    }
    }

    fun write(workspace: MedicalResearchWorkspace) {
        val json=JSONObject()
        json.put("consent",workspace.allowResearchQueries)
        val docs=JSONArray()
        workspace.documents.forEach { docs.put(JSONObject().put("name",it.name).put("importedAt",it.importedAt).put("note",it.extractionNote)) }
        json.put("documents",docs)
        val findings=JSONArray()
        workspace.findings.forEach {
            findings.put(JSONObject().put("id",it.id).put("topic",it.topic).put("evidence",it.evidence)
                .put("source",it.source).put("status",it.status).put("category",it.category).put("query",it.query))
        }
        json.put("findings",findings)
        val evidence=JSONObject()
        workspace.literature.forEach { (key,item) ->
            val papers=JSONArray()
            item.papers.forEach {
                papers.put(JSONObject().put("title",it.title).put("authors",it.authors)
                    .put("journal",it.journal).put("date",it.date).put("pmid",it.pmid)
                    .put("doi",it.doi).put("abstract",it.abstractExcerpt).put("url",it.url)
                    .put("types",it.publicationTypes))
            }
            evidence.put(key,JSONObject().put("checkedAt",item.checkedAt)
                .put("error",item.error).put("papers",papers))
        }
        json.put("literature",evidence)
        val stream=file.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (ex: Exception) {
            file.failWrite(stream)
            throw ex
        }
    }

    fun clear() {
        file.delete()
    }
}

/**
 * Europe PMC returns PubMed/PMC article metadata and abstracts with publication
 * dates. Results are retrieved on demand; an abstract is NOT a verified
 * clinical guideline or automatically accepted diagnostic rule.
 */
object PatientLiteratureSearch {
    private val client=OkHttpClient.Builder()
        .connectTimeout(12,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).build()

    suspend fun search(finding: RecordFinding): FindingLiterature = withContext(Dispatchers.IO) {
        val topic=finding.query.trim().take(130)
        require(topic.isNotBlank()) {
            "Research topic must be confirmed before searching."
        }
        val url="https://www.ebi.ac.uk/europepmc/webservices/rest/search".toHttpUrl()
            .newBuilder()
            .addQueryParameter("query","($topic) AND (SRC:MED OR SRC:PMC) sort_date:y")
            .addQueryParameter("format","json")
            .addQueryParameter("pageSize","6")
            .addQueryParameter("resultType","core")
            .build()
        val req=Request.Builder().url(url).header("Accept","application/json").build()
        val raw=client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Literature server HTTP " + response.code)
            response.body?.string() ?: throw IllegalStateException("Literature server returned an empty response")
        }
        val arr=JSONObject(raw).optJSONObject("resultList")?.optJSONArray("result") ?: JSONArray()
        val papers=(0 until arr.length()).mapNotNull { index ->
            val p=arr.optJSONObject(index) ?: return@mapNotNull null
            val pmid=p.optString("pmid")
            val id=p.optString("id")
            val source=p.optString("source")
            val link=if(pmid.matches(Regex("""\d+"""))) "https://pubmed.ncbi.nlm.nih.gov/$pmid/"
                     else "https://europepmc.org/article/$source/$id"
            val types=p.optJSONObject("pubTypeList")?.optJSONArray("pubType")
            val tags=(0 until (types?.length() ?: 0)).map { types!!.optString(it) }.joinToString(", ")
            ResearchPaper(
                title=p.optString("title"),authors=p.optString("authorString"),
                journal=p.optString("journalTitle"),date=p.optString("firstPublicationDate",p.optString("firstIndexDate")),
                pmid=pmid,doi=p.optString("doi"),
                abstractExcerpt=p.optString("abstractText").replace(Regex("""<[^>]+>""")," ").take(1800),
                url=link,publicationTypes=tags
            )
        }
        FindingLiterature(finding.id,papers,System.currentTimeMillis())
    }
}
