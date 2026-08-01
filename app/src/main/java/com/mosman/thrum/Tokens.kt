package com.mosman.thrum

import androidx.compose.ui.unit.dp

/**
 * The design system's raw values, in one place.
 *
 * After this file there are no dp literals in composables. Values invented at
 * the point of use are how a screen drifts into looking like every other
 * generated screen — the spacing stops meaning anything because nothing chose it.
 *
 * Full reasoning in `design-brief.md` Phase 3.
 */
object Space {
    /** 4-pt base. Everything is a multiple; nothing between. */
    val S1 = 4.dp
    val S2 = 8.dp
    val S3 = 12.dp
    val S4 = 16.dp
    val S5 = 24.dp
    val S6 = 32.dp
    val S7 = 48.dp
    val S8 = 64.dp
}

object Touch {
    /** M3's floor. The visible element may be smaller; the target may not. */
    val min = 48.dp
}

object Radius {
    val small = 6.dp
    val medium = 10.dp
    val large = 16.dp

    /**
     * The ribbon's bars are square, deliberately. A rounded bar reads as a
     * decorative chart; this is a machine part shown at its real shape.
     */
    val none = 0.dp
}

object Motion {
    /** Micro — a colour or a value settling. */
    const val QUICK = 120

    /** Structural — something entering or leaving. */
    const val MOVE = 240

    /** The ribbon's playhead, which must not lag behind the vibration it marks. */
    const val FRAME = 16L
}

object Ribbon {
    /** Tall enough to read a rhythm's shape, short enough to leave room for the action. */
    val height = 96.dp
    val barWidth = 3.dp
    val barGap = 1.dp

    /** A bar this tall marks a step the motor is not moving in. */
    val silentHeight = 1.dp
}
