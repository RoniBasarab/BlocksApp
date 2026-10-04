package com.roni.blocks.designsystem

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

/**
 * The app's only motion token.
 *
 * Rules that must stay true: tab switches and back gestures share this one crossfade, so a back
 * gesture reads as a tab change rather than as a screen being destroyed. The in and out halves are
 * the same duration, otherwise the outgoing screen lingers and the transition looks lopsided.
 */
object Motion {
    /** Crossfade duration in milliseconds. */
    const val crossfadeMs: Int = 250

    /** A symmetric crossfade: both halves run for [crossfadeMs]. */
    val crossfade: ContentTransform =
        fadeIn(tween(crossfadeMs)) togetherWith fadeOut(tween(crossfadeMs))
}