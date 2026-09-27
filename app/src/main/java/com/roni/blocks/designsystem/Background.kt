package com.roni.blocks.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Page backdrop: vertical gradient plus two soft static glows, drawn once via [drawWithCache]. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalAppColors.current
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val base = Brush.verticalGradient(listOf(c.backgroundTop, c.backgroundBottom))
                val blueGlow = Brush.radialGradient(
                    listOf(c.primary.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.05f),
                    radius = size.width * 0.9f,
                )
                val sandGlow = Brush.radialGradient(
                    listOf(c.secondary.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.95f),
                    radius = size.width * 0.8f,
                )
                onDrawBehind {
                    drawRect(base)
                    drawRect(blueGlow)
                    drawRect(sandGlow)
                }
            },
        content = content,
    )
}
