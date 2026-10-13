package com.roni.blocks.core.storage

import com.roni.blocks.core.model.Categories
import com.roni.blocks.core.model.CategorySet
import javax.inject.Inject
import javax.inject.Singleton

/** Stores the categories in use (3.0.0). No UI yet; loaded once at restore, ready for 3.1/3.2. */
@Singleton
class CategoryStore @Inject constructor(
    private val prefs: PrefsStorage,
) {
    suspend fun load(): CategorySet {
        val set = prefs.read(PrefsFile.SETTINGS, KEY, CategorySet.serializer()) ?: CategorySet.DEFAULT
        Categories.current = set
        return set
    }

    suspend fun save(set: CategorySet) {
        prefs.write(PrefsFile.SETTINGS, KEY, set, CategorySet.serializer())
        Categories.current = set
    }

    private companion object {
        const val KEY = "categories"
    }
}
