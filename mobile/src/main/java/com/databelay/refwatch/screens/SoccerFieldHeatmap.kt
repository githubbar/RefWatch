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
import com.databelay.refwatch.common.LocationSample
import kotlin.math.*

@Composable
fun SoccerFieldHeatmap(
    locationHistory: List<LocationSample>,
    modifier: Modifier = Modifier,
    isAssistantReferee: Boolean = false
) {
    if (locationHistory.isEmpty()) return

    // 1. Filter outliers and halftime walk-abouts
    val filteredHistory = remember(locationHistory) {
        if (locationHistory.size < 10) return@remember locationHistory
        
        // Use 10th and 90th percentile to focus on the field and ignore locker room visits
        val sortedLat = locationHistory.map { it.latitude }.sorted()
        val sortedLon = locationHistory.map { it.longitude }.sorted()
        val lowIdx = (locationHistory.size * 0.10).toInt()
        val highIdx = (locationHistory.size * 0.90).toInt()
        
        val latMin = sortedLat[lowIdx]
        val latMax = sortedLat[highIdx]
        val lonMin = sortedLon[lowIdx]
        val lonMax = sortedLon[highIdx]
        
        locationHistory.filter { 
            it.latitude in latMin..latMax && it.longitude in lonMin..lonMax 
        }
    }

    // 2. Calculate field rotation using Principal Component Analysis approach
    val rotationInfo = remember(filteredHistory) {
        if (filteredHistory.isEmpty()) return@remember null
        
        val avgLat = filteredHistory.map { it.latitude }.average()
        val cosLat = cos(avgLat * PI / 180.0)
        
        // Convert to local meters (approx) relative to first point
        val first = filteredHistory.first()
        val points = filteredHistory.map { 
            Offset(
                x = ((it.longitude - first.longitude) * 111320.0 * cosLat).toFloat(),
                y = ((it.latitude - first.latitude) * 111320.0).toFloat()
            )
        }
        
        val meanX = points.map { it.x }.average().toFloat()
        val meanY = points.map { it.y }.average().toFloat()
        
        var covXX = 0.0
        var covYY = 0.0
        var covXY = 0.0
        points.forEach { p ->
            val dx = p.x - meanX
            val dy = p.y - meanY
            covXX += dx * dx
            covYY += dy * dy
            covXY += dx * dy
        }
        
        // Principal Component angle
        val angle = 0.5 * atan2(2 * covXY, covXX - covYY)
        
        // Align long axis (max variance) to vertical (Y axis)
        var finalAngle = (PI / 2.0 - angle).toFloat()
        
        fun rotate(p: Offset, a: Float): Offset {
            val s = sin(a.toDouble()).toFloat()
            val c = cos(a.toDouble()).toFloat()
            val dx = p.x - meanX
            val dy = p.y - meanY
            return Offset(dx * c - dy * s, dx * s + dy * c)
        }
        
        var rotated = points.map { rotate(it, finalAngle) }
        var minX = rotated.minOf { it.x }
        var maxX = rotated.maxOf { it.x }
        var minY = rotated.minOf { it.y }
        var maxY = rotated.maxOf { it.y }
        
        // Ensure height > width for vertical field display
        if ((maxX - minX) > (maxY - minY)) {
            finalAngle += (PI / 2.0).toFloat()
            rotated = points.map { rotate(it, finalAngle) }
            minX = rotated.minOf { it.x }
            maxX = rotated.maxOf { it.x }
            minY = rotated.minOf { it.y }
            maxY = rotated.maxOf { it.y }
        }
        
        RotationInfo(finalAngle, meanX, meanY, maxX - minX, maxY - minY, minX, minY)
    }

    if (rotationInfo == null) return

    // 3. Normalize points into [0, 1] field coordinates
    val normalizedPoints = remember(filteredHistory, rotationInfo) {
        val avgLat = filteredHistory.map { it.latitude }.average()
        val cosLat = cos(avgLat * PI / 180.0)
        val first = filteredHistory.first()
        
        filteredHistory.map {
            val px = ((it.longitude - first.longitude) * 111320.0 * cosLat).toFloat()
            val py = ((it.latitude - first.latitude) * 111320.0).toFloat()
            
            val s = sin(rotationInfo.angle.toDouble()).toFloat()
            val c = cos(rotationInfo.angle.toDouble()).toFloat()
            val dx = px - rotationInfo.centerX
            val dy = py - rotationInfo.centerY
            
            val rx = dx * c - dy * s
            val ry = dx * s + dy * c
            
            Offset(
                x = if (rotationInfo.width > 0) (rx - rotationInfo.minX) / rotationInfo.width else 0.5f,
                y = if (rotationInfo.height > 0) (ry - rotationInfo.minY) / rotationInfo.height else 0.5f
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .aspectRatio(0.68f / 1.05f) // Typical pitch aspect ratio (rotated for vertical display)
            .background(Color(0xFF2E7D32)) // Soccer green
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val strokeWidth = 2.dp.toPx()
            val lineColor = Color.White

            // 1. Draw Pitch Lines
            // Outer boundary
            drawRect(lineColor, style = Stroke(strokeWidth))

            // Center line
            drawLine(lineColor, Offset(0f, height / 2), Offset(width, height / 2), strokeWidth)

            // Center circle
            drawCircle(lineColor, radius = width * 0.15f, center = Offset(width / 2, height / 2), style = Stroke(strokeWidth))
            drawCircle(lineColor, radius = strokeWidth / 2, center = Offset(width / 2, height / 2))

            // Penalty areas
            // Top
            drawRect(lineColor, Offset(width * 0.2f, 0f), Size(width * 0.6f, height * 0.16f), style = Stroke(strokeWidth))
            drawRect(lineColor, Offset(width * 0.35f, 0f), Size(width * 0.3f, height * 0.05f), style = Stroke(strokeWidth))
            // Bottom
            drawRect(lineColor, Offset(width * 0.2f, height * 0.84f), Size(width * 0.6f, height * 0.16f), style = Stroke(strokeWidth))
            drawRect(lineColor, Offset(width * 0.35f, height * 0.95f), Size(width * 0.3f, height * 0.05f), style = Stroke(strokeWidth))

            // 2. Draw Positioning Data
            normalizedPoints.forEach { point ->
                // Account for Assistant Ref (Linesman)
                val drawX = if (isAssistantReferee) {
                    // Place points along the right touchline with a small horizontal spread
                    width * 0.97f + (point.x - 0.5f) * width * 0.04f
                } else {
                    point.x * width
                }
                
                // Flip Y as Lat increases North but Canvas Y increases South
                val drawY = (1f - point.y) * height

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
    val width: Float,
    val height: Float,
    val minX: Float,
    val minY: Float
)
