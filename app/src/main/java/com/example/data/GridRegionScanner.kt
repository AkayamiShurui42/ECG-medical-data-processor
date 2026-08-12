package com.example.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Grid-first ECG/IEGM strip localization.
 *
 * This scanner intentionally does not depend on a paper-ECG layout. It searches
 * the whole rendered page for rectangular areas containing repeated horizontal
 * and vertical line periodicity plus darker waveform-like ink. That works for
 * large ILR/ICM plotting blocks as well as ordinary ECG graph paper.
 */
object GridRegionScanner {

    data class GridRegion(
        val rect: Rect,
        val confidence: Float,
        val horizontalSpacingPx: Float?,
        val verticalSpacingPx: Float?,
        val gridTileCount: Int,
        val waveformInkFraction: Float
    )

    private data class TileScore(
        val row: Int,
        val col: Int,
        val rect: Rect,
        val score: Float,
        val hSpacing: Float?,
        val vSpacing: Float?,
        val waveformInk: Float
    )

    private data class Periodicity(val score: Float, val spacing: Float?)

    fun scan(bitmap: Bitmap): GridRegion? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 120 || height < 120) return null

        val tile = (min(width, height) / 8).coerceIn(72, 180)
        val step = max(36, tile / 2)
        val cols = max(1, ((width - tile).coerceAtLeast(0) / step) + 1)
        val rows = max(1, ((height - tile).coerceAtLeast(0) / step) + 1)

        val scores = Array(rows) { arrayOfNulls<TileScore>(cols) }
        for (r in 0 until rows) {
            val top = min(r * step, max(0, height - tile))
            val bottom = min(height, top + tile)
            for (c in 0 until cols) {
                val left = min(c * step, max(0, width - tile))
                val right = min(width, left + tile)
                val rect = Rect(left, top, right, bottom)
                val scored = scoreTile(bitmap, rect, r, c)
                if (scored.score >= 0.43f) scores[r][c] = scored
            }
        }

        val visited = Array(rows) { BooleanArray(cols) }
        var bestComponent: List<TileScore> = emptyList()
        var bestComponentValue = 0.0

        for (r in 0 until rows) for (c in 0 until cols) {
            if (visited[r][c] || scores[r][c] == null) continue
            val queue = ArrayDeque<Pair<Int, Int>>()
            val component = mutableListOf<TileScore>()
            queue.add(r to c)
            visited[r][c] = true
            while (queue.isNotEmpty()) {
                val (cr, cc) = queue.removeFirst()
                scores[cr][cc]?.let(component::add)
                for (dr in -1..1) for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = cr + dr
                    val nc = cc + dc
                    if (nr !in 0 until rows || nc !in 0 until cols) continue
                    if (!visited[nr][nc] && scores[nr][nc] != null) {
                        visited[nr][nc] = true
                        queue.add(nr to nc)
                    }
                }
            }

            val union = union(component.map { it.rect })
            val areaFraction = union.width().toDouble() * union.height().toDouble() /
                (width.toDouble() * height.toDouble())
            val meanScore = component.map { it.score }.average()
            val aspectBonus = if (union.width() > union.height() * 1.35) 0.20 else 0.0
            val sizeBonus = min(0.35, areaFraction * 1.8)
            val value = meanScore + aspectBonus + sizeBonus + min(0.20, component.size / 20.0)
            if (value > bestComponentValue) {
                bestComponentValue = value
                bestComponent = component
            }
        }

        if (bestComponent.isEmpty()) return null
        val raw = union(bestComponent.map { it.rect })
        if (raw.width() < width * 0.22 || raw.height() < height * 0.06) return null

        val padX = max(4, raw.width() / 80)
        val padY = max(4, raw.height() / 30)
        val region = Rect(
            max(0, raw.left - padX),
            max(0, raw.top - padY),
            min(width, raw.right + padX),
            min(height, raw.bottom + padY)
        )

        val hSpacing = robustMedian(bestComponent.mapNotNull { it.hSpacing })
        val vSpacing = robustMedian(bestComponent.mapNotNull { it.vSpacing })
        val meanScore = bestComponent.map { it.score }.average().toFloat()
        val ink = bestComponent.map { it.waveformInk }.average().toFloat()
        val areaFraction = region.width().toFloat() * region.height().toFloat() / (width.toFloat() * height.toFloat())
        val confidence = (meanScore * 0.70f + min(1f, areaFraction * 2.2f) * 0.20f + min(1f, bestComponent.size / 10f) * 0.10f)
            .coerceIn(0f, 0.99f)

        return GridRegion(
            rect = region,
            confidence = confidence,
            horizontalSpacingPx = hSpacing,
            verticalSpacingPx = vSpacing,
            gridTileCount = bestComponent.size,
            waveformInkFraction = ink
        )
    }

    private fun scoreTile(bitmap: Bitmap, rect: Rect, row: Int, col: Int): TileScore {
        val w = rect.width()
        val h = rect.height()
        val rowGrid = DoubleArray(h)
        val colGrid = DoubleArray(w)
        var strongInk = 0
        var total = 0

        for (yy in 0 until h) {
            val y = rect.top + yy
            var rowCount = 0
            for (xx in 0 until w) {
                val x = rect.left + xx
                val p = bitmap.getPixel(x, y)
                if (isGridLike(p)) {
                    rowCount++
                    colGrid[xx] += 1.0
                }
                if (isStrongWaveformInk(p)) strongInk++
                total++
            }
            rowGrid[yy] = rowCount.toDouble() / w.coerceAtLeast(1)
        }
        for (i in colGrid.indices) colGrid[i] /= h.coerceAtLeast(1)

        val hPeriod = periodicity(rowGrid)
        val vPeriod = periodicity(colGrid)
        val waveformInk = strongInk.toFloat() / total.coerceAtLeast(1).toFloat()

        // A real tracing grid usually has periodic evidence on both axes. We still
        // allow one axis to be weaker because ILR reports can use coarse or faint grids.
        val periodic = hPeriod.score * 0.46f + vPeriod.score * 0.46f
        val balance = min(hPeriod.score, vPeriod.score) * 0.18f
        val inkBonus = min(0.18f, waveformInk * 8f)
        val score = (periodic + balance + inkBonus).coerceIn(0f, 1f)

        return TileScore(row, col, Rect(rect), score, hPeriod.spacing, vPeriod.spacing, waveformInk)
    }

    private fun periodicity(values: DoubleArray): Periodicity {
        if (values.size < 20) return Periodicity(0f, null)
        val mean = values.average()
        val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size.coerceAtLeast(1))
        val threshold = max(mean + sd * 0.55, 0.08)

        val peaks = mutableListOf<Int>()
        for (i in 1 until values.lastIndex) {
            if (values[i] >= threshold && values[i] >= values[i - 1] && values[i] >= values[i + 1]) {
                if (peaks.isEmpty() || i - peaks.last() >= 3) peaks += i
                else if (values[i] > values[peaks.last()]) peaks[peaks.lastIndex] = i
            }
        }
        if (peaks.size < 3) return Periodicity(0f, null)

        val gaps = peaks.zipWithNext { a, b -> (b - a).toFloat() }.filter { it >= 3f }
        if (gaps.size < 2) return Periodicity(0f, null)
        val median = robustMedian(gaps) ?: return Periodicity(0f, null)
        val deviations = gaps.map { abs(it - median) }
        val mad = robustMedian(deviations) ?: 0f
        val regularity = (1f - (mad / median.coerceAtLeast(1f)) * 1.8f).coerceIn(0f, 1f)
        val repetition = min(1f, gaps.size / 6f)
        val prominence = min(1f, ((peaks.map { values[it] }.average() - mean) / max(0.04, sd)).toFloat() / 2.2f)
        return Periodicity((regularity * 0.55f + repetition * 0.25f + prominence * 0.20f).coerceIn(0f, 1f), median)
    }

    private fun isGridLike(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val chroma = maxC - minC
        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b

        val coloredGrid = chroma >= 8 && lum in 105.0..248.0
        val neutralGridOrLine = chroma < 30 && lum in 115.0..242.0
        val darkLine = lum < 120.0
        return coloredGrid || neutralGridOrLine || darkLine
    }

    private fun isStrongWaveformInk(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val chroma = max(r, max(g, b)) - min(r, min(g, b))
        return lum < 105.0 || (lum < 145.0 && chroma > 35)
    }

    private fun union(rects: List<Rect>): Rect {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        rects.forEach {
            left = min(left, it.left)
            top = min(top, it.top)
            right = max(right, it.right)
            bottom = max(bottom, it.bottom)
        }
        return if (rects.isEmpty()) Rect() else Rect(left, top, right, bottom)
    }

    private fun robustMedian(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
    }
}
