package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Local image-to-waveform extraction.
 *
 * The input calibration is expressed in ORIGINAL SOURCE pixels. The digitizer
 * crops to the detected strip first and then adjusts calibration automatically
 * if it downsizes the crop for processing. This avoids page margins, headings,
 * or internal resize operations corrupting time/amplitude measurements.
 */
object LocalWaveformDigitizer {

    data class DigitizerConfig(
        val pixelsPerSecond: Float = 250f,
        val pixelsPerMv: Float = 100f,
        val paperSpeedMmPerSec: Float? = null,
        val gainMmPerMv: Float? = null,
        val sourceCropRect: Rect? = null,
        val timeOriginMs: Double = 0.0,
        val calibrationSource: ValueProvenance = ValueProvenance.MEASURED,
        val leadName: String = "Single channel",
        val maxWorkingWidth: Int = 2200
    )

    data class PixelTracePoint(
        val x: Int,
        val y: Int,
        val inkConfidence: Float
    )

    data class LocalTraceResult(
        val sourceWidthPx: Int,
        val sourceHeightPx: Int,
        val sourceCropRect: Rect,
        val workingWidthPx: Int,
        val workingHeightPx: Int,
        val pixelTrace: List<PixelTracePoint>,
        val waveformWindow: WaveformWindow,
        val events: List<DetectedWaveformEvent>,
        val intervals: List<BeatToBeatInterval>,
        val notes: List<String>
    )

    suspend fun digitizeImage(
        context: Context,
        uri: Uri,
        config: DigitizerConfig
    ): LocalTraceResult = withContext(Dispatchers.Default) {
        val sourceBitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        } ?: error("Unable to decode image")

        val sourceWidth = sourceBitmap.width
        val sourceHeight = sourceBitmap.height
        val cropRect = sanitizeCrop(config.sourceCropRect, sourceWidth, sourceHeight)
        val cropped = if (cropRect.left == 0 && cropRect.top == 0 && cropRect.right == sourceWidth && cropRect.bottom == sourceHeight) {
            sourceBitmap
        } else {
            Bitmap.createBitmap(sourceBitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        }

        val workingScale = if (cropped.width > config.maxWorkingWidth) {
            config.maxWorkingWidth.toFloat() / cropped.width.toFloat()
        } else 1f

        val working = if (workingScale < 1f) {
            Bitmap.createScaledBitmap(
                cropped,
                config.maxWorkingWidth,
                max(1, (cropped.height * workingScale).roundToInt()),
                true
            )
        } else cropped

        val trace = extractCenterline(working)
        if (trace.size < 16) error("Could not extract a continuous waveform centerline")

        // Source calibration must be transformed into working-image pixels after
        // any internal resize. Previously this scaling step was missing.
        val pxPerSec = (config.pixelsPerSecond * workingScale).coerceAtLeast(1f)
        val pxPerMv = (config.pixelsPerMv * workingScale).coerceAtLeast(1f)
        val msPerPixel = 1000.0 / pxPerSec.toDouble()
        val baselineY = trace.map { it.y }.sorted()[trace.size / 2].toDouble()

        val samples = trace.mapIndexed { index, point ->
            val amplitude = (baselineY - point.y.toDouble()) / pxPerMv.toDouble()
            val priorY = trace.getOrNull(index - 1)?.y
            val nextY = trace.getOrNull(index + 1)?.y
            val localJump = listOfNotNull(
                priorY?.let { abs(point.y - it) },
                nextY?.let { abs(point.y - it) }
            ).maxOrNull() ?: 0
            WaveformSample(
                sampleIndex = index.toLong(),
                timeMs = config.timeOriginMs + index * msPerPixel,
                amplitudeMv = amplitude,
                leadName = config.leadName,
                signalQuality = point.inkConfidence,
                artifactFlag = localJump > working.height * 0.18
            )
        }

        val calibration = CalibrationInfo(
            paperSpeedMmPerSec = config.paperSpeedMmPerSec,
            gainMmPerMv = config.gainMmPerMv,
            pixelsPerSecond = pxPerSec,
            pixelsPerMv = pxPerMv,
            sampleRateHz = pxPerSec,
            calibrationSource = config.calibrationSource
        )

        val window = WaveformWindow(
            leadName = config.leadName,
            startTimeMs = samples.firstOrNull()?.timeMs ?: config.timeOriginMs,
            endTimeMs = samples.lastOrNull()?.timeMs ?: config.timeOriginMs,
            samples = samples,
            calibration = calibration,
            sourceUri = uri.toString()
        )

        val events = detectCandidates(samples)
        val intervals = ContinuousBeatTiming.calculateIntervals(events)

        LocalTraceResult(
            sourceWidthPx = sourceWidth,
            sourceHeightPx = sourceHeight,
            sourceCropRect = cropRect,
            workingWidthPx = working.width,
            workingHeightPx = working.height,
            pixelTrace = trace,
            waveformWindow = window,
            events = events,
            intervals = intervals,
            notes = listOf(
                "Tracing region cropped before waveform extraction: ${cropRect.left},${cropRect.top} → ${cropRect.right},${cropRect.bottom} source px.",
                "Centerline extracted directly from source pixels.",
                "Source calibration was transformed by internal image scale ${"%.4f".format(workingScale)}.",
                "Red/pink grid-like pixels are penalized during line tracking.",
                "No fixed post-R or T-wave blanking window is applied."
            )
        )
    }

    private fun sanitizeCrop(input: Rect?, width: Int, height: Int): Rect {
        if (input == null) return Rect(0, 0, width, height)
        val left = input.left.coerceIn(0, max(0, width - 2))
        val top = input.top.coerceIn(0, max(0, height - 2))
        val right = input.right.coerceIn(left + 1, width)
        val bottom = input.bottom.coerceIn(top + 1, height)
        return Rect(left, top, right, bottom)
    }

    private fun extractCenterline(bitmap: Bitmap): List<PixelTracePoint> {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 4 || height < 4) return emptyList()

        val result = ArrayList<PixelTracePoint>(width)
        var previousY: Int? = null
        val continuityRadius = max(12, height / 8)

        for (x in 0 until width) {
            var bestY = previousY ?: height / 2
            var bestScore = Float.NEGATIVE_INFINITY
            var bestInk = 0f

            val yStart = previousY?.let { max(0, it - continuityRadius) } ?: 0
            val yEnd = previousY?.let { min(height - 1, it + continuityRadius) } ?: (height - 1)

            fun evaluateRange(start: Int, end: Int) {
                for (y in start..end) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b)
                    var ink = ((255.0 - luminance) / 255.0).toFloat()
                    val redDominance = (r - max(g, b)).coerceAtLeast(0) / 255f
                    ink -= redDominance * 0.85f

                    val continuityPenalty = previousY?.let {
                        abs(y - it).toFloat() / continuityRadius.toFloat() * 0.34f
                    } ?: 0f
                    val score = ink - continuityPenalty
                    if (score > bestScore) {
                        bestScore = score
                        bestY = y
                        bestInk = ink.coerceIn(0f, 1f)
                    }
                }
            }

            evaluateRange(yStart, yEnd)
            if (bestInk < 0.10f && previousY != null) {
                bestScore = Float.NEGATIVE_INFINITY
                evaluateRange(0, height - 1)
            }

            result += PixelTracePoint(x, bestY, bestInk)
            previousY = bestY
        }

        return result.mapIndexed { index, point ->
            val local = (max(0, index - 2)..min(result.lastIndex, index + 2))
                .map { result[it].y }
                .sorted()
            point.copy(y = local[local.size / 2])
        }
    }

    private fun detectCandidates(samples: List<WaveformSample>): List<DetectedWaveformEvent> {
        if (samples.size < 9) return emptyList()

        val absValues = samples.map { abs(it.amplitudeMv) }
        val sorted = absValues.sorted()
        val amplitudeThreshold = sorted[(sorted.size * 0.72).toInt().coerceIn(0, sorted.lastIndex)]
            .coerceAtLeast(0.03)

        val slopes = DoubleArray(samples.size)
        for (i in 2 until samples.size - 2) {
            val dt = samples[i + 2].timeMs - samples[i - 2].timeMs
            if (dt > 0.0) {
                slopes[i] = abs(samples[i + 2].amplitudeMv - samples[i - 2].amplitudeMv) / (dt / 1000.0)
            }
        }
        val slopeSorted = slopes.filter { it > 0.0 }.sorted()
        val steepThreshold = if (slopeSorted.isNotEmpty()) {
            slopeSorted[(slopeSorted.size * 0.72).toInt().coerceIn(0, slopeSorted.lastIndex)]
        } else 0.0

        val events = mutableListOf<DetectedWaveformEvent>()
        var eventIndex = 0L

        for (i in 2 until samples.size - 2) {
            val a = abs(samples[i].amplitudeMv)
            if (a < amplitudeThreshold) continue

            val isLocalExtreme = a >= abs(samples[i - 1].amplitudeMv) &&
                a >= abs(samples[i + 1].amplitudeMv) &&
                (a > abs(samples[i - 2].amplitudeMv) || a > abs(samples[i + 2].amplitudeMv))
            if (!isLocalExtreme) continue

            val quality = samples[i].signalQuality ?: 0.5f
            val slope = slopes[i]
            val steepnessRatio = if (steepThreshold > 0.0) (slope / steepThreshold).toFloat() else 0f
            val qrsProbability = (0.35f + steepnessRatio.coerceIn(0f, 1.5f) * 0.35f + quality * 0.20f)
                .coerceIn(0f, 0.99f)
            val slowWaveProbability = (1f - qrsProbability).coerceIn(0.01f, 0.85f)

            val primary = when {
                samples[i].artifactFlag || quality < 0.08f -> WaveformEventClass.EXTRACTION_ARTIFACT
                qrsProbability >= 0.58f -> WaveformEventClass.QRS_COMPLEX
                else -> WaveformEventClass.CARDIAC_UNCLASSIFIED
            }
            val accepted = primary == WaveformEventClass.QRS_COMPLEX && qrsProbability >= 0.58f

            events += DetectedWaveformEvent(
                eventIndex = eventIndex++,
                sampleIndex = samples[i].sampleIndex,
                timeMs = samples[i].timeMs,
                amplitudeMv = samples[i].amplitudeMv,
                leadName = samples[i].leadName,
                primaryClass = primary,
                classProbabilities = listOf(
                    EventProbability(WaveformEventClass.QRS_COMPLEX, qrsProbability),
                    EventProbability(WaveformEventClass.T_WAVE, slowWaveProbability * 0.55f),
                    EventProbability(WaveformEventClass.CARDIAC_UNCLASSIFIED, slowWaveProbability * 0.45f)
                ),
                morphologyConfidence = qrsProbability,
                signalQuality = quality,
                acceptedAsVentricularBeat = accepted,
                notes = listOf(
                    "Candidate retained without a time-based blanking rule",
                    "Peak slope=${"%.3f".format(slope)} mV/s"
                )
            )
        }

        return events
    }
}