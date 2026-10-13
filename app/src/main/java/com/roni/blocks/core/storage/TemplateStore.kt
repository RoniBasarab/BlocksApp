package com.roni.blocks.core.storage

import android.content.Context
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.Pomodoro
import com.roni.blocks.core.model.Profile
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.UNEMPLOYED_PROFILE
import com.roni.blocks.core.model.builtInTemplateSeeds
import com.roni.blocks.core.schedule.TemplateRules
import com.roni.blocks.core.schedule.toPlanBlocks
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The templates as stored data (3.0.0): one built-in profile ("Unemployed") holding the four
 * templates 2.x shipped. Seeded from assets + [builtInTemplateSeeds] on the first launch, then read
 * from prefs. Everything is synchronous after [load]; before it, the getters return the seed.
 */
@Singleton
class TemplateStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PrefsStorage,
    private val logger: AppLogger,
) {
    private val seed: List<StoredTemplate> = parseSeed()

    @Volatile private var stored: List<StoredTemplate> = seed
    @Volatile private var profile: Profile = UNEMPLOYED_PROFILE

    init {
        instance = this
    }

    /** The active profile's templates, in its order. */
    fun all(): List<StoredTemplate> = profile.templateIds.mapNotNull { id -> stored.firstOrNull { it.id == id } }

    fun get(id: TemplateId): StoredTemplate = all().firstOrNull { it.id == id } ?: seed.first { it.id == id }

    /** The first template of the active profile whose weekdays contain [day]. */
    fun suggestedFor(day: DayOfWeek): TemplateId? = all().firstOrNull { day.isoDayNumber in it.weekdays }?.id

    suspend fun load() {
        val templates = prefs.read(PrefsFile.SETTINGS, KEY_TEMPLATES, ListSerializer(StoredTemplate.serializer()))
        val profiles = prefs.read(PrefsFile.SETTINGS, KEY_PROFILES, ListSerializer(Profile.serializer()))
        val activeId = prefs.read(PrefsFile.SETTINGS, KEY_ACTIVE, String.serializer())
        if (templates == null || profiles == null || activeId == null) {
            saveSeed()
            return
        }
        stored = templates.map { validated(it) }
        profile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull() ?: UNEMPLOYED_PROFILE
    }

    /** A template that breaks the rules is replaced by its seed (3.0 does not edit templates). */
    private fun validated(template: StoredTemplate): StoredTemplate {
        if (template.id == TemplateId.OFF) return template
        if (TemplateRules.problems(template.blocks.toPlanBlocks()).isEmpty()) return template
        logger.info("template ${template.id.key} failed validation; using its seed")
        return seed.first { it.id == template.id }
    }

    private suspend fun saveSeed() {
        stored = seed
        profile = UNEMPLOYED_PROFILE
        prefs.write(PrefsFile.SETTINGS, KEY_TEMPLATES, seed, ListSerializer(StoredTemplate.serializer()))
        prefs.write(PrefsFile.SETTINGS, KEY_PROFILES, listOf(UNEMPLOYED_PROFILE), ListSerializer(Profile.serializer()))
        prefs.write(PrefsFile.SETTINGS, KEY_ACTIVE, UNEMPLOYED_PROFILE.id, String.serializer())
    }

    private fun parseSeed(): List<StoredTemplate> {
        val text = context.assets.open("templates.json").bufferedReader().use { it.readText() }
        val file = JSON.decodeFromString<TemplateFile>(text)
        return file.templates.map { dto ->
            val meta = builtInTemplateSeeds.getValue(dto.id)
            StoredTemplate(
                id = dto.id,
                title = meta.title,
                subtitle = meta.subtitle,
                short = meta.short,
                iconKey = meta.iconKey,
                accentArgb = meta.accentArgb,
                weekdays = meta.weekdays,
                blocks = dto.blocks.map { it.toSpec() },
            )
        }
    }

    companion object {
        /** The one store this process reads display data from; null until one is built (previews, pure tests). */
        @Volatile var instance: TemplateStore? = null
            private set

        private const val KEY_TEMPLATES = "templates"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "active_profile"

        private val JSON = Json { ignoreUnknownKeys = true }
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
        start = kotlinx.datetime.LocalTime.parse(start),
        end = kotlinx.datetime.LocalTime.parse(end),
        pomodoro = pomodoro,
        quiet = quiet,
    )
}
