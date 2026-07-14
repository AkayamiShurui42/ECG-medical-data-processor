package com.example.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object PubMedSearch {
    private val client = OkHttpClient()

    fun searchPubMed(query: String): List<ClinicalLiterature> {
        val results = mutableListOf<ClinicalLiterature>()
        try {
            // 1. Search for IDs
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=pubmed&term=$encodedQuery&retmode=json&retmax=10"
            
            val searchRequest = Request.Builder().url(searchUrl).build()
            val searchResponse = client.newCall(searchRequest).execute()
            val searchBody = searchResponse.body?.string() ?: return emptyList()
            
            val searchJson = JSONObject(searchBody)
            val eSearchResult = searchJson.optJSONObject("esearchresult") ?: return emptyList()
            val idListJson = eSearchResult.optJSONArray("idlist") ?: return emptyList()
            
            if (idListJson.length() == 0) return emptyList()
            
            val ids = mutableListOf<String>()
            for (i in 0 until idListJson.length()) {
                ids.add(idListJson.getString(i))
            }
            val idParam = ids.joinToString(",")
            
            // 2. Fetch Summaries
            val summaryUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi?db=pubmed&id=$idParam&retmode=json"
            val summaryRequest = Request.Builder().url(summaryUrl).build()
            val summaryResponse = client.newCall(summaryRequest).execute()
            val summaryBody = summaryResponse.body?.string() ?: return emptyList()
            
            val summaryJson = JSONObject(summaryBody)
            val resultObj = summaryJson.optJSONObject("result") ?: return emptyList()
            
            for (id in ids) {
                val docInfo = resultObj.optJSONObject(id) ?: continue
                val rawTitle = docInfo.optString("title")
                // Clean HTML-like tags from PubMed title (e.g. &lt;i&gt;)
                val title = rawTitle.replace(Regex("<[^>]*>"), "")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                
                // Get authors
                val authorsArray = docInfo.optJSONArray("authors")
                val authorNames = mutableListOf<String>()
                if (authorsArray != null) {
                    for (j in 0 until authorsArray.length()) {
                        val authorObj = authorsArray.getJSONObject(j)
                        authorNames.add(authorObj.optString("name"))
                    }
                }
                val authorsStr = if (authorNames.isNotEmpty()) {
                    if (authorNames.size > 3) "${authorNames.take(3).joinToString(", ")}, et al."
                    else authorNames.joinToString(", ")
                } else {
                    "Unknown Authors"
                }
                
                val source = docInfo.optString("source")
                val pubDate = docInfo.optString("pubdate")
                val year = try {
                    pubDate.split(" ")[0].split("-")[0].toInt()
                } catch (e: Exception) {
                    2025
                }
                
                val pubTypeArray = docInfo.optJSONArray("pubtype")
                val pubType = if (pubTypeArray != null && pubTypeArray.length() > 0) pubTypeArray.getString(0) else "Journal Article"
                
                results.add(
                    ClinicalLiterature(
                        title = title,
                        authors = authorsStr,
                        source = "$source ($pubType)",
                        year = year,
                        summary = "NIH PubMed Indexed Publication (PMID: $id). Guided research for clinical cardiology decision systems.",
                        category = "PubMed / NIH Search",
                        levelOfEvidence = "PMID $id (NIH)"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("PubMedSearch", "Error searching PubMed", e)
        }
        return results
    }
}
