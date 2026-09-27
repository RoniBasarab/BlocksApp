package com.roni.blocks.core.time

import kotlin.time.Instant

/**
 * Converts an app-clock instant into wall-clock milliseconds the Android framework understands.
 *
 * The app runs on a simulated clock (see `DebugClock`) so the debug build can jump through a whole
 * day. `Notification.setWhen` and `AlarmManager` always need real elapsed time, so every value that
 * leaves the app is mapped through here. Release maps identity; debug subtracts the offset.
 */
fun interface SystemTimeMapper {
    fun systemMillis(at: Instant): Long

    companion object {
        val Identity: SystemTimeMapper = SystemTimeMapper { it.toEpochMilliseconds() }
    }
}
