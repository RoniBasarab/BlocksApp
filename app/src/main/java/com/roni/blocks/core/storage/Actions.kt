package com.roni.blocks.core.storage

import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.profiles.ProfileEdit
import com.roni.blocks.core.profiles.Profiles
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only way the screens write templates (3.1.0): a save also tells today's plan to follow
 * when the day hasn't started yet.
 */
@Singleton
class TemplateActions @Inject constructor(
    private val store: TemplateStore,
    private val day: DayOrchestrator,
) {
    suspend fun createIn(profileId: String, title: String): StoredTemplate = store.createIn(profileId, title)
    suspend fun save(t: StoredTemplate): Boolean = store.save(t).also { if (it) day.onTemplateSaved(t.id) }
    suspend fun delete(id: TemplateId): Boolean = store.delete(id)
}

/** The only way the screens write profiles (3.2.0): every write goes through [TemplateStore.edit]. */
@Singleton
class ProfileActions @Inject constructor(private val store: TemplateStore) {
    suspend fun create(name: String, copyActive: Boolean) =
        store.edit { Profiles.create(it, name, copyActive) }.also { if (it is ProfileEdit.Ok) store.markProfileChosen() }
    suspend fun rename(id: String, name: String) = store.edit { Profiles.rename(it, id, name) }
    suspend fun delete(id: String) = store.edit { Profiles.delete(it, id) }
    suspend fun switchTo(id: String) =
        store.edit { Profiles.switchTo(it, id) }.also { if (it is ProfileEdit.Ok) store.markProfileChosen() }
    suspend fun addTemplate(profileId: String, id: TemplateId) = store.edit { Profiles.addTemplate(it, profileId, id) }
    suspend fun removeTemplate(profileId: String, id: TemplateId) = store.edit { Profiles.removeTemplate(it, profileId, id) }
    suspend fun setDay(profileId: String, iso: Int, id: TemplateId?) = store.edit { Profiles.setDay(it, profileId, iso, id) }
}

/**
 * The only way the screens write categories (3.1.0): a custom category change also rewrites the
 * templates' and today's blocks that use it (by id, or to free time when deleted).
 */
@Singleton
class CategoryActions @Inject constructor(
    private val categories: CategoryStore,
    private val templates: TemplateStore,
    private val day: DayOrchestrator,
) {
    suspend fun update(info: CategoryInfo): CategoryResult {
        val result = categories.update(info)
        if (result is CategoryResult.Saved && result.info?.builtIn == false) {
            sync(info.id, result.info)
        }
        return result
    }

    suspend fun add(name: String, colorArgb: Long, work: Boolean, pomodoro: Boolean): CategoryResult {
        val result = categories.add(name, colorArgb, work, pomodoro)
        if (result is CategoryResult.Saved) result.info?.let { sync(it.id, it) }
        return result
    }

    suspend fun delete(id: String): CategoryResult {
        val result = categories.delete(id)
        if (result is CategoryResult.Saved) sync(id, null)
        return result
    }

    private suspend fun sync(id: String, info: CategoryInfo?) {
        templates.replaceCategory(id, info)
        day.onCategoryChanged(id, info)
    }
}