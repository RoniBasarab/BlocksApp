package com.roni.blocks.core.storage

import android.content.Context
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.model.TemplateId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/** A thin façade over [TemplateStore] (3.0.0), so the day code keeps calling [get]/[all]. */
@Singleton
class TemplateManager @Inject constructor(
    private val store: TemplateStore,
) {
    /** Hand-built tests and previews keep constructing it from a context; they get the seed. */
    constructor(context: Context) : this(
        TemplateStore(context, PrefsStorage(context, Dispatchers.IO), AppLogger(context)),
    )

    fun all(): List<Template> = store.all().map { it.toTemplate() }

    fun get(id: TemplateId): Template = store.get(id).toTemplate()
}

private fun StoredTemplate.toTemplate(): Template = Template(id, title, blocks)
