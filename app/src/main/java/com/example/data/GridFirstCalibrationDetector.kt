package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Grid-first wrapper around the calibration detector.
 *
 * The older detector remains responsible for OCR/scale-bar interpretation, while
 * this layer decides where the actual tracing lives. Large ILR/ICM plotting blocks
 * therefore win over report headers, tables, and other dark page content whenever
 * repeated horizontal/vertical grid structure is present.
 */
object GridFirstCalibrationDetector {

    suspend fun detect(context: Context, uri: Uri): AutoCalibrationDetector.DetectionResult = withContext(Dispatchers.Default) {
        val base = AutoCalibrationDetector.detect(context, uri)
        val source = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        } ?: return@withContext base

        val maxWidth = 1800
        val scale = if (source.width > maxWidth) maxWidth.toFloat() / source.width else 1f
        val work: Bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, maxWidth, max(1, (source.height * scale).roundToInt()), true)
        } else source

        val grid = GridRegionScanner.scan(work) ?: return@withContext base
        if (grid.confidence < 0.44f) return@withContext base

        val sx = source.width.toFloat() / work.width.toFloat()
        val sy = source.height.toFloat() / work.height.toFloat()
        val r = grid.rect
        val sourceRect = Rect(
            (r.left * sx).roundToInt().coerceIn(0, source.width - 1),
            (r.top * sy).roundToInt().coerceIn(0, source.height - 1),
            (r.right * sx).roundToInt().coerceIn(1, source.width),
            (r.bottom * sy).roundToInt().coerceIn(1, source.height)
        )

        val old = base.traceRegionSourcePx
        val intersection = Rect()
        val overlaps = intersection.setIntersect(old, sourceRect)
        val oldArea = old.width().toLong() * old.height().toLong()
        val interArea = if (overlaps) intersection.width().toLong() * intersection.height().toLong() else 0L
        val overlapFraction = if (oldArea > 0L) interArea.toDouble() / oldArea.toDouble() else 0.0

        val gridNote = "Grid-first scanner selected plotting block (${grid.gridTileCount} grid tiles, ${"%.0f".format(grid.confidence * 100)}% structural confidence)."
        val warnings = buildList {
            addAll(base.warnings)
            add(gridNote)
            if (overlapFraction < 0.45) {
                add("Grid scanner and legacy dark-band scanner disagreed substantially; the grid-defined plotting block was preferred. Visually verify the crop.")
            }
            if (grid.waveformInkFraction < 0.002f) {
                add("Grid structure is strong but dark waveform ink is sparse; verify that this is a rhythm plot rather than an empty chart/table.")
            }
        }.distinct()

        base.copy(
            traceRegionSourcePx = sourceRect,
            confidence = max(base.confidence, grid.confidence),
            warnings = warnings
        )
    }
}
