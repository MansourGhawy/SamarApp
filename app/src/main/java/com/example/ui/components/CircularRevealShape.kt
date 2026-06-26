package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.hypot

// Circular Reveal Shape for Liquid Morphing Effect
class CircularRevealShape(
    val progress: Float,
    val centerOffset: Offset,
    val isRelative: Boolean = false
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val maxRadius = hypot(size.width, size.height)
        val radius = maxRadius * progress
        val actualCenter = if (isRelative) {
            Offset(size.width * centerOffset.x, centerOffset.y)
        } else {
            centerOffset
        }
        val path = Path().apply {
            addOval(Rect(actualCenter, radius))
        }
        return Outline.Generic(path)
    }
}
