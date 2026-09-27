package com.roni.blocks.core.debug

import java.util.concurrent.ConcurrentHashMap

/**
 * Counts recompositions per composable tag.
 *
 * Enabled only from tests. Each composable that must be observed calls
 * [hit] from a `SideEffect` on the first line of its body.
 */
object RecompositionCounter {

    @Volatile
    var enabled: Boolean = false

    private val counts = ConcurrentHashMap<String, Int>()

    fun hit(tag: String) {
        if (enabled) counts.merge(tag, 1, Int::plus)
    }

    fun get(tag: String): Int = counts[tag] ?: 0

    fun reset() = counts.clear()
}
