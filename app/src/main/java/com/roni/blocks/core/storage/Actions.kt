package com.roni.blocks.core.storage

import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
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
    suspend fun create(title: String): StoredTemplate = store.create(title)
    suspend fun save(t: StoredTemplate): Boolean = store.save(t).also { if (it) day.onTemplateSaved(t.id) }
    suspend fun delete(id: TemplateId): Boolean = store.delete(id)
    suspend fun setWeekdays(id: TemplateId, days: Set<Int>) = store.setWeekdays(id, days)
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