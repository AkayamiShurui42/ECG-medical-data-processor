package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Local image-to-waveform extraction for the first testable analyzer build.
 *
 * This is deliberately deterministic and auditable: it extracts a centerline
 * from the actual source pixels, converts the centerline to calibrated samples,
 * and runs morphology-based candidate detection without any fixed refractory or
 * T-wave blanking window. The raw candidates remain visible even when they are
 * not accepted as ventricular beats.
 */
object LocalWaveformDigitizer {

    data class DigitizerConfig(
        val pixelsPerSecond: Float = 250f,
        val pixelsPerMv: Float = 100f,
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
        val working = if (sourceWidth > config.maxWorkingWidth) {
            val scale = config.maxWorkingWidth.toFloat() / sourceWidth.toFloat()
            Bitmap.createScaledBitmap(
                sourceBitmap,
                config.maxWorkingWidth,
                max(1, (sourceHeight * scale).roundToInt()),
                true
            )
        } else sourceBitmap

        val trace = extractCenterline(working)
        if (trace.size < 16) error("Could not extract a continuous waveform centerline")

        val baselineY = trace.map { it.y }.sorted()[trace.size / 2].toDouble()
        val pxPerSec = config.pixelsPerSecond.coerceAtLeast(1f)
        val pxPerMv = config.pixelsPerMv.coerceAtLeast(1f)
        val msPerPixel = 1000.0 / pxPerSec.toDouble()

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
                timeMs = index * msPerPixel,
                amplitudeMv = amplitude,
                leadName = config.leadName,
                signalQuality = point.inkConfidence,
                artifactFlag = localJump > working.height * 0.18
            )
        }

        val calibration = CalibrationInfo(
            pixelsPerSecond = pxPerSec,
            pixelsPerMv = pxPerMv,
            sampleRateHz = pxPerSec,
            calibrationSource = ValueProvenance.REPORTED
        )

        val window = WaveformWindow(
            leadName = config.leadName,
            startTimeMs = samples.firstOrNull()?.timeMs ?: 0.0,
            endTimeMs = samples.lastOrNull()?.timeMs ?: 0.0,
            samples = samples,
            calibration = calibration,
            sourceUri = uri.toString()
        )

        val events = detectCandidates(samples)
        val intervals = ContinuousBeatTiming.calculateIntervals(events)

        LocalTraceResult(
            sourceWidthPx = sourceWidth,
            sourceHeightPx = sourceHeight,
            workingWidthPx = working.width,
            workingHeightPx = working.height,
            pixelTrace = trace,
            waveformWindow = window,
            events = events,
            intervals = intervals,
            notes = listOf(
                "Centerline extracted directly from source pixels.",
                "Red/pink grid-like pixels are penalized during line tracking.",
                "No fixed post-R or T-wave blanking window is applied.",
                "Calibration is user-adjustable; measurements are only as accurate as pixel/time and pixel/mV calibration."
            )
        )
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

                    // ECG paper grids are commonly red/pink; penalize chromatic red
                    // while retaining dark/black waveform strokes.
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

            // If the local continuity window contains almost no ink, search the
            // whole column instead of forcing the trace to remain in a blank area.
            if (bestInk < 0.10f && previousY != null) {
                bestScore = Float.NEGATIVE_INFINITY
                evaluateRange(0, height - 1)
            }

            result += PixelTracePoint(x, bestY, bestInk)
            previousY = bestY
        }

        // Small median smoother suppresses isolated pixel errors without deleting
        // actual candidate deflections from the waveform event stream.
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
