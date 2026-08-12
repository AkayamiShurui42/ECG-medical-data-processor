package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Automatic calibration for rendered ECG/IEGM documents.
 *
 * The detector deliberately separates two jobs:
 *  1) find the local tracing/graph region inside a larger report page;
 *  2) infer horizontal and vertical calibration from printed references/grid.
 *
 * A detected scale may come from direct scale bars (e.g. 1 s or 0.5 mV) or
 * from paper settings (e.g. 25 mm/s and 10 mm/mV) combined with detected grid
 * spacing. Manual override remains possible when the source genuinely provides
 * no reliable calibration reference.
 */
object AutoCalibrationDetector {

    data class DetectionResult(
        val traceRegionSourcePx: Rect,
        val pixelsPerSecond: Float?,
        val pixelsPerMv: Float?,
        val paperSpeedMmPerSec: Float?,
        val gainMmPerMv: Float?,
        val gridPixelsPerMm: Float?,
        val timeReferenceSource: String,
        val amplitudeReferenceSource: String,
        val recognizedCalibrationText: List<String>,
        val confidence: Float,
        val warnings: List<String> = emptyList()
    ) {
        val hasTimeCalibration: Boolean get() = pixelsPerSecond != null && pixelsPerSecond > 0f
        val hasAmplitudeCalibration: Boolean get() = pixelsPerMv != null && pixelsPerMv > 0f
    }

    private data class OcrLine(val text: String, val box: Rect)

    suspend fun detect(context: Context, uri: Uri): DetectionResult = withContext(Dispatchers.Default) {
        val source = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        } ?: error("Unable to decode calibration image")

        val maxWidth = 1800
        val scale = if (source.width > maxWidth) maxWidth.toFloat() / source.width else 1f
        val work = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, maxWidth, max(1, (source.height * scale).roundToInt()), true)
        } else source

        val ocr = recognize(work)
        val lines = ocr.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line -> line.boundingBox?.let { OcrLine(line.text, Rect(it)) } }
        }
        val textBoxes = ocr.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { it.boundingBox?.let(::Rect) }

        val traceRegionWork = detectTraceRegion(work, textBoxes)
        val sourceScaleX = source.width.toFloat() / work.width.toFloat()
        val sourceScaleY = source.height.toFloat() / work.height.toFloat()
        val traceRegionSource = Rect(
            (traceRegionWork.left * sourceScaleX).roundToInt().coerceIn(0, source.width - 1),
            (traceRegionWork.top * sourceScaleY).roundToInt().coerceIn(0, source.height - 1),
            (traceRegionWork.right * sourceScaleX).roundToInt().coerceIn(1, source.width),
            (traceRegionWork.bottom * sourceScaleY).roundToInt().coerceIn(1, source.height)
        )

        val paperSpeed = lines.firstNotNullOfOrNull { parsePaperSpeed(it.text) }
        val gain = lines.firstNotNullOfOrNull { parseGain(it.text) }
        val gridPxPerMmWork = estimateGridPixelsPerMm(work, traceRegionWork)

        val directTime = detectDirectTimeScale(work, lines, traceRegionWork)
        val directAmplitude = detectDirectAmplitudeScale(work, lines, traceRegionWork)

        val pxPerSecondWork = directTime?.first
            ?: if (paperSpeed != null && gridPxPerMmWork != null) paperSpeed * gridPxPerMmWork else null
        val pxPerMvWork = directAmplitude?.first
            ?: if (gain != null && gridPxPerMmWork != null) gain * gridPxPerMmWork else null

        // Calibration is detected on the working bitmap; convert back to source pixels
        // because the digitizer will crop/scale from the original source.
        val pxPerSecondSource = pxPerSecondWork?.let { it * sourceScaleX }
        val pxPerMvSource = pxPerMvWork?.let { it * sourceScaleY }
        val gridPxPerMmSource = gridPxPerMmWork?.let { it * ((sourceScaleX + sourceScaleY) / 2f) }

        val timeSource = when {
            directTime != null -> directTime.second
            paperSpeed != null && gridPxPerMmWork != null -> "Printed ${trimFloat(paperSpeed)} mm/s + detected ECG grid"
            else -> "No reliable printed time reference detected"
        }
        val amplitudeSource = when {
            directAmplitude != null -> directAmplitude.second
            gain != null && gridPxPerMmWork != null -> "Printed ${trimFloat(gain)} mm/mV + detected ECG grid"
            else -> "No reliable printed amplitude reference detected"
        }

        val usefulText = lines.map { it.text.trim() }
            .filter { line ->
                parsePaperSpeed(line) != null || parseGain(line) != null ||
                    TIME_LABEL.containsMatchIn(line) || MV_LABEL.containsMatchIn(line)
            }
            .distinct()
            .take(12)

        val warnings = buildList {
            if (pxPerSecondSource == null) add("Time calibration could not be established automatically; use Advanced override only if the source truly lacks a time reference.")
            if (pxPerMvSource == null) add("Amplitude calibration could not be established automatically; use Advanced override only if the source truly lacks an amplitude reference.")
            if (traceRegionWork.width() < work.width * 0.35f) add("Detected tracing region is narrow; visually confirm the selected strip before trusting measurements.")
        }
        val confidenceParts = listOfNotNull(
            if (pxPerSecondSource != null) 1f else null,
            if (pxPerMvSource != null) 1f else null,
            if (traceRegionWork.width() >= work.width * 0.5f) 0.8f else 0.45f,
            if (usefulText.isNotEmpty()) 0.8f else 0.45f
        )
        val confidence = (confidenceParts.average().toFloat()).coerceIn(0f, 1f)

        DetectionResult(
            traceRegionSourcePx = traceRegionSource,
            pixelsPerSecond = pxPerSecondSource,
            pixelsPerMv = pxPerMvSource,
            paperSpeedMmPerSec = paperSpeed,
            gainMmPerMv = gain,
            gridPixelsPerMm = gridPxPerMmSource,
            timeReferenceSource = timeSource,
            amplitudeReferenceSource = amplitudeSource,
            recognizedCalibrationText = usefulText,
            confidence = confidence,
            warnings = warnings
        )
    }

    private suspend fun recognize(bitmap: Bitmap): Text {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
                task.addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                task.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                task.addOnCanceledListener { continuation.cancel() }
            }
        } finally {
            recognizer.close()
        }
    }

    private fun parsePaperSpeed(text: String): Float? = PAPER_SPEED.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    private fun parseGain(text: String): Float? = GAIN.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()

    private fun detectDirectTimeScale(bitmap: Bitmap, lines: List<OcrLine>, roi: Rect): Pair<Float, String>? {
        val candidates = mutableListOf<Pair<Float, String>>()
        for (line in lines) {
            val match = TIME_LABEL.find(line.text) ?: continue
            val raw = match.groupValues[1].toFloatOrNull() ?: continue
            val unit = match.groupValues[2].lowercase()
            val seconds = if (unit.startsWith("m")) raw / 1000f else raw
            if (seconds <= 0f) continue
            val length = findNearbyHorizontalBar(bitmap, line.box, roi) ?: continue
            val pxPerSec = length / seconds
            if (pxPerSec in 10f..10000f) {
                candidates += pxPerSec to "Printed ${trimFloat(raw)} ${unit} scale bar"
            }
        }
        return candidates.maxByOrNull { it.first }
    }

    private fun detectDirectAmplitudeScale(bitmap: Bitmap, lines: List<OcrLine>, roi: Rect): Pair<Float, String>? {
        val candidates = mutableListOf<Pair<Float, String>>()
        for (line in lines) {
            // Exclude gain labels such as 10 mm/mV from direct amplitude-scale parsing.
            if (parseGain(line.text) != null) continue
            val match = MV_LABEL.find(line.text) ?: continue
            val mv = match.groupValues[1].toFloatOrNull() ?: continue
            if (mv <= 0f) continue
            val length = findNearbyVerticalBar(bitmap, line.box, roi) ?: continue
            val pxPerMv = length / mv
            if (pxPerMv in 5f..10000f) {
                candidates += pxPerMv to "Printed ${trimFloat(mv)} mV scale bar"
            }
        }
        return candidates.maxByOrNull { it.first }
    }

    /** Find a long dark horizontal scale segment near its OCR label. */
    private fun findNearbyHorizontalBar(bitmap: Bitmap, label: Rect, roi: Rect): Float? {
        val h = max(10, label.height())
        val search = Rect(
            max(roi.left, label.left - bitmap.width / 3),
            max(roi.top, label.top - h * 4),
            min(roi.right, label.right + bitmap.width / 3),
            min(roi.bottom, label.bottom + h * 4)
        )
        if (search.width() <= 4 || search.height() <= 2) return null
        var best = 0
        for (y in search.top until search.bottom) {
            var run = 0
            for (x in search.left until search.right) {
                if (label.contains(x, y)) { run = 0; continue }
                if (isDark(bitmap.getPixel(x, y))) {
                    run++
                    if (run > best) best = run
                } else run = 0
            }
        }
        return best.takeIf { it >= max(18, label.width()) }?.toFloat()
    }

    /** Find a long dark vertical amplitude reference segment near its OCR label. */
    private fun findNearbyVerticalBar(bitmap: Bitmap, label: Rect, roi: Rect): Float? {
        val w = max(10, label.width())
        val search = Rect(
            max(roi.left, label.left - w * 3),
            max(roi.top, label.top - bitmap.height / 5),
            min(roi.right, label.right + w * 3),
            min(roi.bottom, label.bottom + bitmap.height / 5)
        )
        if (search.width() <= 2 || search.height() <= 4) return null
        var best = 0
        for (x in search.left until search.right) {
            var run = 0
            for (y in search.top until search.bottom) {
                if (label.contains(x, y)) { run = 0; continue }
                if (isDark(bitmap.getPixel(x, y))) {
                    run++
                    if (run > best) best = run
                } else run = 0
            }
        }
        return best.takeIf { it >= max(18, label.height()) }?.toFloat()
    }

    /**
     * Finds the strongest broad horizontal graph/trace band after masking OCR text.
     * This prevents report headers/margins from becoming part of the time axis.
     */
    private fun detectTraceRegion(bitmap: Bitmap, textBoxes: List<Rect>): Rect {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 50 || height < 50) return Rect(0, 0, width, height)

        val bandHeight = (height / 7).coerceIn(80, max(80, height / 2))
        val stepY = max(12, bandHeight / 5)
        val stepX = if (width > 1200) 3 else 2
        val pixelStepY = 3
        var bestTop = 0
        var bestScore = -1.0
        var bestLeft = 0
        var bestRight = width

        var top = 0
        while (top + bandHeight <= height) {
            val bottom = top + bandHeight
            var covered = 0
            var firstInk = width
            var lastInk = 0
            var darkCount = 0
            var sampledCount = 0
            var x = 0
            while (x < width) {
                var columnInk = false
                var y = top
                while (y < bottom) {
                    sampledCount++
                    if (!insideAnyTextBox(x, y, textBoxes) && isTraceOrGridPixel(bitmap.getPixel(x, y))) {
                        darkCount++
                        columnInk = true
                    }
                    y += pixelStepY
                }
                if (columnInk) {
                    covered++
                    firstInk = min(firstInk, x)
                    lastInk = max(lastInk, x)
                }
                x += stepX
            }
            val totalColumns = max(1, (width + stepX - 1) / stepX)
            val coverage = covered.toDouble() / totalColumns.toDouble()
            val span = if (lastInk > firstInk) (lastInk - firstInk).toDouble() / width.toDouble() else 0.0
            val density = darkCount.toDouble() / max(1, sampledCount).toDouble()
            // Graph regions tend to span the width but remain relatively sparse.
            val denseTextPenalty = if (density > 0.33) (density - 0.33) * 1.8 else 0.0
            val score = coverage * 0.55 + span * 0.40 + min(density * 2.0, 0.12) - denseTextPenalty
            if (score > bestScore) {
                bestScore = score
                bestTop = top
                bestLeft = if (firstInk < width) max(0, firstInk - width / 100) else 0
                bestRight = if (lastInk > 0) min(width, lastInk + width / 100) else width
            }
            top += stepY
        }

        val padY = max(16, bandHeight / 6)
        return Rect(
            bestLeft.coerceIn(0, width - 1),
            max(0, bestTop - padY),
            bestRight.coerceIn(bestLeft + 1, width),
            min(height, bestTop + bandHeight + padY)
        )
    }

    private fun insideAnyTextBox(x: Int, y: Int, boxes: List<Rect>): Boolean = boxes.any { box ->
        x >= box.left - 2 && x <= box.right + 2 && y >= box.top - 2 && y <= box.bottom + 2
    }

    /** Estimate the smallest repeating grid interval likely to represent 1 mm. */
    private fun estimateGridPixelsPerMm(bitmap: Bitmap, roi: Rect): Float? {
        if (roi.width() < 80 || roi.height() < 40) return null
        val scores = DoubleArray(roi.width())
        val yStep = max(1, roi.height() / 180)
        for (ix in scores.indices) {
            val x = roi.left + ix
            var score = 0.0
            var n = 0
            var y = roi.top
            while (y < roi.bottom) {
                score += gridPixelScore(bitmap.getPixel(x, y))
                n++
                y += yStep
            }
            scores[ix] = if (n > 0) score / n else 0.0
        }
        val mean = scores.average()
        val centered = DoubleArray(scores.size) { scores[it] - mean }
        val variance = centered.sumOf { it * it }
        if (variance <= 1e-9) return null

        val minLag = 4
        val maxLag = min(100, roi.width() / 8)
        if (maxLag <= minLag) return null
        val correlations = mutableListOf<Pair<Int, Double>>()
        for (lag in minLag..maxLag) {
            var numerator = 0.0
            var leftPower = 0.0
            var rightPower = 0.0
            for (i in 0 until centered.size - lag) {
                val a = centered[i]
                val b = centered[i + lag]
                numerator += a * b
                leftPower += a * a
                rightPower += b * b
            }
            val denom = sqrt(leftPower * rightPower)
            if (denom > 1e-9) correlations += lag to (numerator / denom)
        }
        val maxCorr = correlations.maxOfOrNull { it.second } ?: return null
        if (maxCorr < 0.18) return null
        val threshold = max(0.18, maxCorr * 0.68)
        // Prefer the smallest credible repeating interval so a bold 5-mm line is
        // not accidentally treated as one millimetre.
        return correlations.firstOrNull { it.second >= threshold }?.first?.toFloat()
    }

    private fun gridPixelScore(pixel: Int): Double {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val chroma = maxC - minC
        val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b)
        val pinkRedGrid = r > g + 8 && r > b + 8 && r > 120
        val paleNeutralGrid = chroma < 22 && lum in 150.0..245.0
        return when {
            pinkRedGrid -> 1.0
            paleNeutralGrid -> 0.55
            else -> 0.0
        }
    }

    private fun isTraceOrGridPixel(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b)
        val redGrid = r > g + 8 && r > b + 8 && r > 120
        return lum < 170.0 || redGrid
    }

    private fun isDark(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b)
        return lum < 105.0 && abs(r - g) < 80 && abs(g - b) < 80
    }

    private fun trimFloat(value: Float): String = if (abs(value - value.roundToInt()) < 0.001f) {
        value.roundToInt().toString()
    } else "%.3f".format(value).trimEnd('0').trimEnd('.')

    private val PAPER_SPEED = Regex("""(\d+(?:\.\d+)?)\s*mm\s*/\s*(?:s|sec|second)\b""", RegexOption.IGNORE_CASE)
    private val GAIN = Regex("""(\d+(?:\.\d+)?)\s*mm\s*/\s*m\s*v\b""", RegexOption.IGNORE_CASE)
    private val TIME_LABEL = Regex("""(\d+(?:\.\d+)?)\s*(ms|msec|milliseconds?|s|sec|seconds?)\b""", RegexOption.IGNORE_CASE)
    private val MV_LABEL = Regex("""(\d+(?:\.\d+)?)\s*m\s*v\b""", RegexOption.IGNORE_CASE)
}