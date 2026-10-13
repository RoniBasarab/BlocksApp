package com.roni.blocks.core.storage

import com.roni.blocks.core.model.Categories
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.CategorySet
import javax.inject.Inject
import javax.inject.Singleton

/** The outcome of a category change; [Saved] carries the new category (null when it was deleted). */
sealed interface CategoryResult {
    data class Saved(val info: CategoryInfo?) : CategoryResult
    data object EmptyName : CategoryResult
    data object NameTaken : CategoryResult
    data object TooLong : CategoryResult
    data object BuiltIn : CategoryResult
}

/**
 * Stores the categories in use (3.0.0). 3.1.0 adds the three writes the screens need: custom
 * categories can be added and deleted, every category can be renamed, recoloured and toggled.
 * Built-ins can never be deleted, and a built-in always keeps its `work` flag.
 */
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

    /** Rename, recolour or toggle Pomodoro. The name is trimmed, 1–16 characters, and unique (case-insensitive). */
    suspend fun update(info: CategoryInfo): CategoryResult {
        val name = info.name.trim()
        if (name.isEmpty()) return CategoryResult.EmptyName
        if (name.length > 16) return CategoryResult.TooLong
        val set = Categories.current
        val taken = (set.builtIns + set.customs).any { it.id != info.id && it.name.equals(name, ignoreCase = true) }
        if (taken) return CategoryResult.NameTaken
        val updated = if (info.builtIn) {
            // A built-in keeps its work flag and builtIn marker no matter what the caller sends.
            val existing = set.builtIns.firstOrNull { it.id == info.id } ?: return CategoryResult.BuiltIn
            info.copy(name = name, work = existing.work)
        } else {
            info.copy(name = name)
        }
        val next = if (updated.builtIn) {
            set.copy(builtIns = set.builtIns.map { if (it.id == updated.id) updated else it })
        } else {
            set.copy(customs = set.customs.map { if (it.id == updated.id) updated else it })
        }
        save(next)
        return CategoryResult.Saved(updated)
    }

    /** A new custom category, keyed `cat_<n>`. The name is checked the same way [update] checks it. */
    suspend fun add(name: String, colorArgb: Long, work: Boolean, pomodoro: Boolean): CategoryResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return CategoryResult.EmptyName
        if (trimmed.length > 16) return CategoryResult.TooLong
        val set = Categories.current
        if ((set.builtIns + set.customs).any { it.name.equals(trimmed, ignoreCase = true) }) return CategoryResult.NameTaken
        val highest = set.customs.mapNotNull { it.id.removePrefix("cat_").toIntOrNull() }.maxOrNull() ?: 0
        val info = CategoryInfo("cat_${highest + 1}", trimmed, colorArgb, work, pomodoro, builtIn = false)
        save(set.copy(customs = set.customs + info))
        return CategoryResult.Saved(info)
    }

    /** Deletes a custom category; built-ins are refused. */
    suspend fun delete(id: String): CategoryResult {
        val set = Categories.current
        if (set.builtIns.any { it.id == id }) return CategoryResult.BuiltIn
        save(set.copy(customs = set.customs.filterNot { it.id == id }))
        return CategoryResult.Saved(null)
    }

    private companion object {
        const val KEY = "categories"
    }
}