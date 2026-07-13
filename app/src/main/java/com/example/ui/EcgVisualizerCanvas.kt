package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArtifactPoint
import com.example.data.EcgWavePoint
import com.example.data.WaveSegment

@Composable
fun EcgVisualizerCanvas(
    points: List<EcgWavePoint>,
    segments: List<WaveSegment>,
    artifacts: List<ArtifactPoint>,
    showXai: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Colors
    val gridColorMinor = Color(0xFFFDE8E8) // Light medical red minor grid
    val gridColorMajor = Color(0xFFF9C2C2) // Medical red major grid
    val waveColor = Color(0xFF1E293B)      // Clean slate/charcoal wave
    val xaiColorGlow = Color(0x33FF007F)   // Neon pink glow for XAI

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(Color(0xFFFFFDFD), shape = RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE2E8F0), shape = RoundedCornerShape(12.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val maxPoints = 500

            if (points.isEmpty()) return@Canvas

            // 1. Draw Medical ECG Grid Lines
            val minorInterval = 15f
            val majorInterval = 75f

            // Vertical minor grid lines
            var xGrid = 0f
            while (xGrid < width) {
                drawLine(
                    color = gridColorMinor,
                    start = Offset(xGrid, 0f),
                    end = Offset(xGrid, height),
                    strokeWidth = 0.5f
                )
                xGrid += minorInterval
            }

            // Horizontal minor grid lines
            var yGrid = 0f
            while (yGrid < height) {
                drawLine(
                    color = gridColorMinor,
                    start = Offset(0f, yGrid),
                    end = Offset(width, yGrid),
                    strokeWidth = 0.5f
                )
                yGrid += minorInterval
            }

            // Vertical major grid lines
            xGrid = 0f
            while (xGrid < width) {
                drawLine(
                    color = gridColorMajor,
                    start = Offset(xGrid, 0f),
                    end = Offset(xGrid, height),
                    strokeWidth = 1.2f
                )
                xGrid += majorInterval
            }

            // Horizontal major grid lines
            yGrid = 0f
            while (yGrid < height) {
                drawLine(
                    color = gridColorMajor,
                    start = Offset(0f, yGrid),
                    end = Offset(width, yGrid),
                    strokeWidth = 1.2f
                )
                yGrid += majorInterval
            }

            // Helper lambda to map index and value to coordinate offset
            fun getCoordinate(index: Int, value: Float): Offset {
                val pointX = (index.toFloat() / maxPoints) * width
                // Map value -2.0..2.0 to vertical axis
                val pointY = centerY - (value * (height / 4.5f))
                return Offset(pointX, pointY)
            }

            // 2. Draw Wave Segment Highlights (P-wave blue, QRS green, T-wave purple)
            segments.forEach { segment ->
                val startX = (segment.startIndex.toFloat() / maxPoints) * width
                val endX = (segment.endIndex.toFloat() / maxPoints) * width
                val color = when (segment.type) {
                    "P" -> Color(0x1F3B82F6) // Translucent Blue
                    "QRS" -> Color(0x1F10B981) // Translucent Green
                    else -> Color(0x1F8B5CF6)  // Translucent Purple (T)
                }

                drawRect(
                    color = color,
                    topLeft = Offset(startX, 0f),
                    size = Size(endX - startX, height)
                )

                // Label segment at bottom
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${segment.type}-wave",
                    topLeft = Offset(startX + 4f, height - 22f),
                    style = TextStyle(
                        color = when (segment.type) {
                            "P" -> Color(0xFF1D4ED8)
                            "QRS" -> Color(0xFF047857)
                            else -> Color(0xFF6D28D9)
                        },
                        fontSize = 9.sp
                    )
                )
            }

            // 3. Draw Explainable AI Heatmap (Glow Overlay)
            if (showXai) {
                val xaiPath = Path()
                points.forEachIndexed { i, pt ->
                    val coord = getCoordinate(pt.index, pt.value)
                    if (i == 0) {
                        xaiPath.moveTo(coord.x, coord.y)
                    } else {
                        xaiPath.lineTo(coord.x, coord.y)
                    }
                }
                // Draw wide neon brush below to highlight important regions
                points.forEach { pt ->
                    if (pt.xaiHeatmapWeight > 0.4f) {
                        val coord = getCoordinate(pt.index, pt.value)
                        drawCircle(
                            color = xaiColorGlow.copy(alpha = pt.xaiHeatmapWeight * 0.3f),
                            radius = 16f * pt.xaiHeatmapWeight,
                            center = coord
                        )
                    }
                }
            }

            // 4. Plot the ECG Waveform Path
            val wavePath = Path()
            points.forEachIndexed { idx, pt ->
                val coord = getCoordinate(pt.index, pt.value)
                if (idx == 0) {
                    wavePath.moveTo(coord.x, coord.y)
                } else {
                    wavePath.lineTo(coord.x, coord.y)
                }
            }
            drawPath(
                path = wavePath,
                color = waveColor,
                style = Stroke(width = 2.5f)
            )

            // 5. Map the RR Intervals directly onto the canvas
            // Identify consecutive QRS peaks
            val rPeaks = artifacts
                .filter { it.label.contains("R-Wave", ignoreCase = true) || it.label.contains("Hypertrophy", ignoreCase = true) }
                .sortedBy { it.index }

            if (rPeaks.size >= 2) {
                for (k in 0 until rPeaks.size - 1) {
                    val p1 = rPeaks[k]
                    val p2 = rPeaks[k + 1]

                    val pt1 = points.firstOrNull { it.index == p1.index } ?: continue
                    val pt2 = points.firstOrNull { it.index == p2.index } ?: continue

                    val c1 = getCoordinate(pt1.index, pt1.value)
                    val c2 = getCoordinate(pt2.index, pt2.value)

                    // Draw RR interval bracket at the top of the waveform
                    val bracketY = centerY - 100f
                    drawLine(
                        color = Color(0xFF64748B),
                        start = Offset(c1.x, bracketY),
                        end = Offset(c2.x, bracketY),
                        strokeWidth = 1.5f
                    )
                    // Tick marks
                    drawLine(
                        color = Color(0xFF64748B),
                        start = Offset(c1.x, bracketY - 6f),
                        end = Offset(c1.x, bracketY + 6f),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = Color(0xFF64748B),
                        start = Offset(c2.x, bracketY - 6f),
                        end = Offset(c2.x, bracketY + 6f),
                        strokeWidth = 1.5f
                    )

                    // Draw the calculated RR interval length (e.g. RR: 820ms)
                    val rPeakDistance = p2.index - p1.index
                    val rrMs = rPeakDistance * 10 // Synthetic conversion: 1 sample = 10ms
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "RR: ${rrMs}ms",
                        topLeft = Offset((c1.x + c2.x) / 2f - 30f, bracketY - 20f),
                        style = TextStyle(
                            color = Color(0xFF475569),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            // 6. Draw Polarization Tip Points & Artifact Circles (color-coded)
            artifacts.forEach { artifact ->
                val pt = points.firstOrNull { it.index == artifact.index } ?: return@forEach
                val coord = getCoordinate(pt.index, pt.value)

                val color = when (artifact.classification) {
                    "absolute" -> Color(0xFFEF4444) // Absolutely Artifact: Red
                    "potential" -> Color(0xFFF59E0B) // Might be Artifact: Yellow/Amber
                    else -> Color(0xFF10B981) // Not an Artifact: Green (Physiological depolarization)
                }

                // Draw a pulsing outer circle for clinical artifacts
                if (artifact.classification != "none") {
                    drawCircle(
                        color = color.copy(alpha = 0.3f),
                        radius = 12f,
                        center = coord
                    )
                }

                // Draw core tip point marker
                drawCircle(
                    color = color,
                    radius = 5.5f,
                    center = coord
                )

                // Label each polarization/artifact point
                val labelOffset = if (coord.y < centerY) Offset(coord.x - 20f, coord.y - 18f) else Offset(coord.x - 20f, coord.y + 10f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = artifact.label,
                    topLeft = labelOffset,
                    style = TextStyle(
                        color = if (artifact.classification == "absolute") Color(0xFF991B1B) else if (artifact.classification == "potential") Color(0xFF92400E) else Color(0xFF065F46),
                        fontSize = 9.sp
                    )
                )
            }
        }
    }
}
