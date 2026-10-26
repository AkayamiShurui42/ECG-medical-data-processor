package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Flattens structured clinical exports into searchable lines while preserving
 * labels. This is not FHIR schema validation or a clinical coding system.
 */
object StructuredMedicalText {
    fun jsonToClinicalLines(raw: String): String {
        val result=StringBuilder()
        fun visit(value: Any?, path: String, depth: Int) {
            if(depth>18 || result.length>2_000_000) return
            when(value) {
                is JSONObject -> {
                    val iter=value.keys()
                    while(iter.hasNext()) {
                        val name=iter.next()
                        val key=if(path.isBlank()) name else path+"."+name
                        visit(value.opt(name),key,depth+1)
                    }
                }
                is JSONArray -> for(i in 0 until minOf(value.length(),25000))
                    visit(value.opt(i),path,depth+1)
                JSONObject.NULL,null -> Unit
                else -> {
                    val v=value.toString().replace(Regex("""[\r\n\t]+""")," ").trim()
                    if(v.isNotEmpty()) result.append(path.takeLast(100)).append(": ").append(v.take(450)).append('\n')
                }
            }
        }
        visit(JSONTokener(raw).nextValue(),"",0)
        return result.toString()
    }

    fun xmlToClinicalLines(raw: String): String {
        // This helper extracts text and simple attributes; it does NOT parse
        // arbitrary entity declarations or download XML external resources.
        val text=raw.replace(Regex("""<[^>]+>"""),"\n").replace("&amp;","&")
            .replace("&lt;","<").replace("&gt;",">")
        val attr=Regex("\\b(?:displayName|display|value|code|text)=\"([^\"]{2,220})\"")
            .findAll(raw).take(25000).map { it.groupValues[1] }.joinToString("\n")
        return text+"\n"+attr
    }
}
