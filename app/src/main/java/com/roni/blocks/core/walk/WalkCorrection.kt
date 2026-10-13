package com.roni.blocks.core.walk

/** A walk's new times, or why not. Minutes of the walk's own date; [floorMin]/[ceilMin] are what the day allows around it. */
sealed interface WalkFix {
    data class Ok(val startMin: Int, val endMin: Int) : WalkFix
    data class Refused(val why: String) : WalkFix
}

object WalkCorrection {
    /**
     * The one rule for moving a walk (3.4.1): it stays at least 5 minutes, it starts no earlier than
     * [floorMin] and ends no later than [ceilMin], and — while [nowMin] isn't null (the walk is
     * today) — it doesn't end in the future.
     */
    fun check(startMin: Int, endMin: Int, floorMin: Int, ceilMin: Int, nowMin: Int?): WalkFix {
        if (endMin - startMin < 5) return WalkFix.Refused("A walk is at least 5 minutes.")
        if (startMin < floorMin) return WalkFix.Refused("Something else ran until ${hhmm(floorMin)}.")
        if (endMin > ceilMin) return WalkFix.Refused("Something else started at ${hhmm(ceilMin)}.")
        if (nowMin != null && endMin > nowMin) return WalkFix.Refused("That's in the future.")
        return WalkFix.Ok(startMin, endMin)
    }

    private fun hhmm(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
}
