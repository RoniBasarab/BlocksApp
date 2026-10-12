package com.roni.blocks.core.walk

/** Which way the walk is going. DONE once the end has passed (until the orchestrator logs it). */
enum class WalkPhase { OUT, HOME, DONE }

/** Everything the walk card and the notification show, for one moment. */
data class WalkView(val phase: WalkPhase, val secondsLeft: Long, val progress: Float, val turnSec: Long, val text: String)

/**
 * The walk's clock (2.6.0). Every time here is **epoch seconds** (`Instant.epochSeconds`): start, end and now. Never
 * minutes of the day; mixing the two is what showed "29862554:24" in 2.5.0. The turn-back moment is always the middle
 * of start and end, so after "Turn back now" (end = now + (now − start)) it is exactly the moment he turned.
 * Talks to: nothing (pure). HomeViewModel calls [view] every second; the orchestrator uses [turnSec].
 */
object WalkClock {
    fun turnSec(startSec: Long, endSec: Long): Long = startSec + (endSec - startSec) / 2

    fun view(startSec: Long, endSec: Long, nowSec: Long): WalkView {
        val turn = turnSec(startSec, endSec)
        val span = (endSec - startSec).coerceAtLeast(1)
        val progress = ((nowSec - startSec).toFloat() / span).coerceIn(0f, 1f)
        val (phase, left) = when {
            nowSec >= endSec -> WalkPhase.DONE to 0L
            nowSec >= turn -> WalkPhase.HOME to endSec - nowSec
            else -> WalkPhase.OUT to turn - nowSec
        }
        return WalkView(phase, left, progress, turn, format(left))
    }

    /** "04:05", or "1:02:03" from an hour up. Negative counts as zero. */
    fun format(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    /** The end after "Turn back now" at [nowSec]: home takes as long as the way out did. Null once past half time. */
    fun turnBackEnd(startSec: Long, endSec: Long, nowSec: Long): Long? =
        if (nowSec < turnSec(startSec, endSec) && nowSec >= startSec) nowSec + (nowSec - startSec) else null
}
