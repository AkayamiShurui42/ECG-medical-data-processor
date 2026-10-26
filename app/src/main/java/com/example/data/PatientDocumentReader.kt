package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Html
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ImportedMedicalText(val filename: String, val text: String, val extractionNote: String)

object PatientDocumentReader {
    private const val MAX_FILE_BYTES = 20 * 1024 * 1024
    private const val MAX_TEXT = 1_500_000
    private const val MAX_PDF_PAGES = 60

    suspend fun import(context: Context, uri: Uri): ImportedMedicalText = withContext(Dispatchers.IO) {
        val name = documentName(context, uri)
        val mime = (context.contentResolver.getType(uri) ?: "").lowercase()
        val extension = name.substringAfterLast('.', "").lowercase()
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(8192)
            val out = ByteArrayOutputStream()
            while (true) {
                coroutineContext.ensureActive()
                val n = stream.read(buffer)
                if (n < 0) break
                if (out.size() + n > MAX_FILE_BYTES) {
                    throw IllegalArgumentException("Document exceeds 20 MB import limit. Split it before importing.")
                }
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } ?: throw IllegalArgumentException("Unable to open document")

        val result = when {
            mime == "application/pdf" || extension == "pdf" -> pdfText(context, bytes)
            mime.startsWith("image/") || extension in setOf("png","jpg","jpeg","webp","bmp") -> {
                val img = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                    ?: throw IllegalArgumentException("Unable to decode image")
                val bitmap = downsize(img)
                val found = ocr(bitmap)
                if (bitmap !== img) bitmap.recycle()
                img.recycle()
                found to "Image OCR; inspect original for missed values and formatting."
            }
            mime.contains("wordprocessingml") || extension == "docx" -> {
                wordText(bytes) to "DOCX paragraph text; tables and embedded pictures may need separate review."
            }
            extension == "json" || mime == "application/json" -> {
                StructuredMedicalText.jsonToClinicalLines(bytes.toString(Charsets.UTF_8)) to
                    "Structured JSON flattened to searchable clinical lines; validate coding and provenance."
            }
            extension == "xml" || mime == "application/xml" -> {
                StructuredMedicalText.xmlToClinicalLines(bytes.toString(Charsets.UTF_8)) to
                    "XML content and attributes extracted; validate terminology and context."
            }
            extension in setOf("txt","csv","md","tsv") || mime.startsWith("text/") -> {
                bytes.toString(Charsets.UTF_8) to "Text imported; verify encoding and clinical context."
            }
            else -> throw IllegalArgumentException("Supported: PDF, DOCX, image, TXT, CSV, JSON, XML, TSV or Markdown.")
        }
        if (result.first.isBlank()) throw IllegalArgumentException("No readable text found; verify that the file contains a legible report.")
        val note = if (result.first.length > MAX_TEXT) result.second + " Text capped at 1.5 million characters; document is incomplete." else result.second
        ImportedMedicalText(name,result.first.take(MAX_TEXT),note)
    }

    private fun documentName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)
        cursor?.use {
            if (it.moveToFirst()) return it.getString(0)?.take(120) ?: "Medical document"
        }
        return "Medical document"
    }

    private suspend fun pdfText(context: Context, bytes: ByteArray): Pair<String,String> {
        val temp = File.createTempFile("medical_import_", ".pdf", context.cacheDir)
        try {
            temp.writeBytes(bytes)
            ParcelFileDescriptor.open(temp,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val count = minOf(renderer.pageCount,MAX_PDF_PAGES)
                    val output = StringBuilder()
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try {
                        for (index in 0 until count) {
                            coroutineContext.ensureActive()
                            renderer.openPage(index).use { page ->
                                val scale = minOf(2f,2200f / maxOf(page.width,page.height).coerceAtLeast(1))
                                val width = (page.width * scale).toInt().coerceAtLeast(1)
                                val height = (page.height * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val scannedText = suspendCancellableCoroutine<String> { continuation ->
                                        recognizer.process(InputImage.fromBitmap(bitmap,0))
                                            .addOnSuccessListener { if(continuation.isActive) continuation.resume(it.text) }
                                            .addOnFailureListener { if(continuation.isActive) continuation.resumeWithException(it) }
                                    }
                                    output.append("\n[PDF page ").append(index+1).append("]\n").append(scannedText).append("\n")
                                } finally { bitmap.recycle() }
                            }
                        }
                    } finally { recognizer.close() }
                    val note = "PDF OCR: " + count + " of " + renderer.pageCount + " pages scanned. " +
                        (if (renderer.pageCount > count) "INCOMPLETE: split and import remaining pages. " else "") +
                        "Review the original for OCR and table errors."
                    return output.toString() to note
                }
            }
        } finally { temp.delete() }
    }

    private suspend fun ocr(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap,0))
                    .addOnSuccessListener { if(continuation.isActive) continuation.resume(it.text) }
                    .addOnFailureListener { if(continuation.isActive) continuation.resumeWithException(it) }
            }
        } finally { recognizer.close() }
    }

    private fun downsize(bitmap: Bitmap): Bitmap {
        val edge = maxOf(bitmap.width,bitmap.height)
        if (edge <= 2400) return bitmap
        val scale=2400f/edge
        return Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt(),(bitmap.height*scale).toInt(),true)
    }

    private fun wordText(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry=zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    val xml=zip.readBytes().toString(Charsets.UTF_8)
                    val chunks=xml.replace(Regex("""</w:(?:p|tr)>"""),"\n")
                        .replace(Regex("""</w:tc>"""),"\t")
                        .replace(Regex("""<[^>]+>"""),"")
                    return Html.fromHtml(chunks,Html.FROM_HTML_MODE_LEGACY).toString()
                }
            }
        }
        throw IllegalArgumentException("DOCX has no readable main document")
    }
}
