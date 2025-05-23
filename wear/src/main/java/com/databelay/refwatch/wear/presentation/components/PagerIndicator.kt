package com.databelay.refwatch.wear.presentation.components // Or your components package

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme // For accessing theme colors

@OptIn(ExperimentalFoundationApi::class) // PagerState is experimental
@Composable
fun HorizontalPagerIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colors.primary,
    inactiveColor: Color = activeColor.copy(alpha = 0.5f),
    indicatorWidth: Dp = 8.dp,
    indicatorHeight: Dp = 8.dp,
    spacing: Dp = 8.dp,
    indicatorShape: CornerRadius = CornerRadius(indicatorHeight.value / 2, indicatorHeight.value / 2) // For rounded dots
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val N = pagerState.pageCount // Number of pages
        if (N <= 0) return@Row // Do nothing if no pages

        for (i in 0 until N) {
            val isSelected = pagerState.currentPage == i
            PagerIndicatorDot(
                isSelected = isSelected,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                width = indicatorWidth,
                height = indicatorHeight,
                cornerRadius = indicatorShape
            )
        }
    }
}

@Composable
private fun PagerIndicatorDot(
    isSelected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    width: Dp,
    height: Dp,
    cornerRadius: CornerRadius
) {
    val color = if (isSelected) activeColor else inactiveColor
    Canvas(
        modifier = Modifier
            .size(width = width, height = height)
    ) {
        drawRoundRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(width.toPx(), height.toPx()),
            cornerRadius = cornerRadius
        )
    }
}