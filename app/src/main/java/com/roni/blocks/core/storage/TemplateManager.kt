package com.roni.blocks.core.storage

import android.content.Context
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.Pomodoro
import com.roni.blocks.core.model.Template
import com.roni.blocks.core.model.TemplateId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Singleton
class TemplateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache: Map<TemplateId, Template> by lazy { load() }

    fun all(): List<Template> = TemplateId.builtIns.map { requireNotNull(cache[it]) }

    fun get(id: TemplateId): Template = requireNotNull(cache[id])

    private fun load(): Map<TemplateId, Template> {
        val text = context.assets.open("templates.json").bufferedReader().use { it.readText() }
        return json.decodeFromString<TemplateFile>(text).templates.associate { dto ->
            dto.id to Template(
                id = dto.id,
                name = dto.name,
                blocks = dto.blocks.map { it.toSpec() },
            )
        }
    }
}

@Serializable
private data class TemplateFile(val templates: List<TemplateDto>)

@Serializable
private data class TemplateDto(
    val id: TemplateId,
    val name: String,
    val blocks: List<BlockDto>,
)

@Serializable
private data class BlockDto(
    val id: String,
    val type: String,
    val start: String,
    val end: String,
    val pomodoro: Pomodoro? = null,
    val quiet: Boolean = false,
    val choice: String? = null,
) {
    fun toSpec(): BlockSpec = BlockSpec(
        id = id,
        type = when (type) {
            "cv" -> BlockType.Cv
            "coding" -> BlockType.Coding
            "gym" -> BlockType.Gym
            "free" -> BlockType.Free
            "kidTime" -> BlockType.KidTime
            "dishes" -> BlockType.Dishes
            "evening" -> BlockType.EveningChoice(Choice.valueOf(requireNotNull(choice)))
            else -> error("Unknown block type $type")
        },
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        pomodoro = pomodoro,
        quiet = quiet,
    )
}
