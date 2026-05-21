package com.databelay.refwatch.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.databelay.refwatch.common.filterGameMovement
import com.databelay.refwatch.common.LocationSample
import com.databelay.refwatch.common.GameEvent
import com.databelay.refwatch.common.AgeGroup
import kotlin.math.*

@Composable
fun SoccerFieldHeatmap(
    locationHistory: List<LocationSample>,
    ageGroup: AgeGroup?,
    modifier: Modifier = Modifier,
    gameEvents: List<GameEvent> = emptyList(),
    isAssistantReferee: Boolean = false
) {
    if (locationHistory.isEmpty()) return

    val filteredHistory = remember(locationHistory, gameEvents) {
        locationHistory.filterGameMovement(gameEvents)
    }

    val rotationInfo = remember(filteredHistory, ageGroup) {
        if (filteredHistory.isEmpty()) return@remember null
        
        val avgLat = filteredHistory.map { it.latitude }.average()
        val cosLat = cos(avgLat * PI / 180.0)
        
        val first = filteredHistory.first()
        val points = filteredHistory.map { 
            Offset(
                x = ((it.longitude - first.longitude) * 111320.0 * cosLat).toFloat(),
                y = ((it.latitude - first.latitude) * 111320.0).toFloat()
            )
        }
        
        val meanX = points.map { it.x }.average().toFloat()
        val meanY = points.map { it.y }.average().toFloat()
        
        // Simple orientation heuristic
        val step = (points.size / 50).coerceAtLeast(1)
        val sampled = points.filterIndexed { i, _ -> i % step == 0 }
        var maxD2 = -1f
        var pA = Offset.Zero
        var pB = Offset.Zero
        for (i in sampled.indices) {
            for (j in i + 1 until sampled.size) {
                val d2 = (sampled[i] - sampled[j]).getDistanceSquared()
                if (d2 > maxD2) {
                    maxD2 = d2
                    pA = sampled[i]
                    pB = sampled[j]
                }
            }
        }
        
        val angle = if (maxD2 > 0) atan2(pB.y - pA.y, pB.x - pA.x) else 0f
        val finalAngle = (PI / 2.0 - angle).toFloat()
        
        val fieldDims = when (ageGroup) {
            AgeGroup.U8 -> Size(30f, 20f)
            AgeGroup.U10 -> Size(60f, 40f)
            AgeGroup.U11, AgeGroup.U12 -> Size(75f, 50f)
            else -> Size(105f, 68f)
        }
        
        RotationInfo(finalAngle, meanX, meanY, fieldDims.width, fieldDims.height)
    }

    if (rotationInfo == null) return

    val normalizedPoints = remember(filteredHistory, rotationInfo) {
        val avgLat = filteredHistory.map { it.latitude }.average()
        val cosLat = cos(avgLat * PI / 180.0)
        val first = filteredHistory.first()
        
        val s = sin(rotationInfo.angle.toDouble()).toFloat()
        val c = cos(rotationInfo.angle.toDouble()).toFloat()

        filteredHistory.map {
            val px = ((it.longitude - first.longitude) * 111320.0 * cosLat).toFloat()
            val py = ((it.latitude - first.latitude) * 111320.0).toFloat()
            
            val dx = px - rotationInfo.centerX
            val dy = py - rotationInfo.centerY
            
            val rx = dx * c - dy * s
            val ry = dx * s + dy * c
            
            Offset(
                x = (rx / rotationInfo.fieldWidth) + 0.5f,
                y = (ry / rotationInfo.fieldHeight) + 0.5f
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .aspectRatio(0.68f / 1.05f)
            .background(Color(0xFF2E7D32))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val strokeWidth = 2.dp.toPx()
            val lineColor = Color.White

            // 1. Draw Pitch Lines
            drawRect(lineColor, style = Stroke(strokeWidth))
            drawLine(lineColor, Offset(0f, height / 2), Offset(width, height / 2), strokeWidth)
            drawCircle(lineColor, radius = width * 0.15f, center = Offset(width / 2, height / 2), style = Stroke(strokeWidth))
            drawCircle(lineColor, radius = strokeWidth / 2, center = Offset(width / 2, height / 2))

            // Penalty areas
            drawRect(lineColor, Offset(width * 0.2f, 0f), Size(width * 0.6f, height * 0.16f), style = Stroke(strokeWidth))
            drawRect(lineColor, Offset(width * 0.35f, 0f), Size(width * 0.3f, height * 0.05f), style = Stroke(strokeWidth))
            drawRect(lineColor, Offset(width * 0.2f, height * 0.84f), Size(width * 0.6f, height * 0.16f), style = Stroke(strokeWidth))
            drawRect(lineColor, Offset(width * 0.35f, height * 0.95f), Size(width * 0.3f, height * 0.05f), style = Stroke(strokeWidth))

            // 2. Draw Positioning Data
            normalizedPoints.forEach { point ->
                val drawX = point.x.coerceIn(0f, 1f) * width
                val drawY = (1f - point.y).coerceIn(0f, 1f) * height

                drawCircle(
                    color = Color.Yellow.copy(alpha = 0.12f),
                    radius = 6.dp.toPx(),
                    center = Offset(drawX, drawY)
                )
            }
        }
    }
}

private data class RotationInfo(
    val angle: Float,
    val centerX: Float,
    val centerY: Float,
    val fieldWidth: Float,
    val fieldHeight: Float
)
