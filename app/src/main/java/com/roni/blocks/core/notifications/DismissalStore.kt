package com.roni.blocks.core.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which notices the user swiped away, so a plain refresh does not bring back something
 * they deliberately dismissed. A real transition clears the memory: new information is worth
 * showing again.
 */
interface DismissalStore {
    fun isDismissed(key: String): Boolean

    fun remember(key: String)

    fun forget(key: String)
}

@Singleton
class PrefsDismissalStore @Inject constructor(
    @ApplicationContext context: Context,
) : DismissalStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun isDismissed(key: String): Boolean = prefs.getBoolean(key, false)

    override fun remember(key: String) {
        prefs.edit().putBoolean(key, true).apply()
    }

    override fun forget(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val FILE = "dismissed_notices"
    }
}
