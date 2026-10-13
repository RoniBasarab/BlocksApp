package com.roni.blocks.core.storage

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.serializer

/** The five first-visit explainer cards (3.3.0); each shows once, until reset. */
enum class ExplainerKey { PROFILES, TEMPLATES, DAYS, BLOCKS, CATEGORIES }

/** Remembers which explainer cards were seen (3.3.0), in [PrefsFile.SETTINGS]. */
@Singleton
class ExplainerStore @Inject constructor(private val prefs: PrefsStorage) {
    suspend fun seen(key: ExplainerKey): Boolean =
        prefs.read(PrefsFile.SETTINGS, keyName(key), Boolean.serializer()) ?: false

    suspend fun markSeen(key: ExplainerKey) {
        prefs.write(PrefsFile.SETTINGS, keyName(key), true, Boolean.serializer())
    }

    /** Debug: shows every explainer again, and the Profiles "New here?" hint. */
    suspend fun resetAll() {
        prefs.keys(PrefsFile.SETTINGS)
            .filter { it.startsWith(PREFIX) || it == HOW_TO_SEEN }
            .forEach { prefs.remove(PrefsFile.SETTINGS, it) }
    }

    private fun keyName(key: ExplainerKey): String = "$PREFIX${key.name.lowercase()}"

    private companion object {
        const val PREFIX = "explained_"
        const val HOW_TO_SEEN = "how_to_seen"
    }
}
