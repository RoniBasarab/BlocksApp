package com.roni.blocks.core.time

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * System time plus a stored offset, so the debug build can be walked through a whole day without
 * waiting for it. It lives in `src/main` because the renderer and the time-travel receiver both
 * have to understand it; the binding that installs it is debug-only.
 */
@Singleton
class DebugClock @Inject constructor(
    @ApplicationContext context: Context,
) : Clock {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Volatile
    private var offset: Duration = Duration.ZERO

    init {
        offset = prefs.getInt(KEY_OFFSET, 0).minutes
    }

    override fun now(): Instant = Clock.System.now() + offset

    val offsetMinutes: Int get() = offset.inWholeMinutes.toInt()

    fun setOffsetMinutes(minutes: Int) {
        offset = minutes.minutes
        prefs.edit().putInt(KEY_OFFSET, minutes).apply()
    }

    /**
     * App time is real time plus the offset, so converting back to wall-clock means subtracting it.
     * The offset is read on every call, so a jump takes effect on the very next notification.
     */
    fun asMapper(): SystemTimeMapper = SystemTimeMapper { (it - offset).toEpochMilliseconds() }

    private companion object {
        const val FILE = "debug_clock"
        const val KEY_OFFSET = "offset_minutes"
    }
}
