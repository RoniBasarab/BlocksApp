package com.roni.blocks.core.storage

import android.content.Context
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.Pomodoro
import com.roni.blocks.core.model.Profile
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.UNEMPLOYED_PROFILE
import com.roni.blocks.core.model.accentFor
import com.roni.blocks.core.model.builtInTemplateSeeds
import com.roni.blocks.core.model.shortFor
import com.roni.blocks.core.model.subtitleFor
import com.roni.blocks.core.schedule.TemplateEditor
import com.roni.blocks.core.schedule.TemplateRules
import com.roni.blocks.core.schedule.toBlockSpecs
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

    /** The active profile's templates, in its order (deleted ones are archived and no longer listed). */
    fun all(): List<StoredTemplate> = profile.templateIds.mapNotNull { id -> stored.firstOrNull { it.id == id && !it.archived } }

    /** The stored template, falling back to its built-in seed; null only for an unknown id. */
    fun find(id: TemplateId): StoredTemplate? = stored.firstOrNull { it.id == id } ?: seed.firstOrNull { it.id == id }

    fun get(id: TemplateId): StoredTemplate = find(id) ?: error("Unknown template ${id.key}")

    /** True for the four templates 2.x shipped: they cannot be saved, deleted, or lose their seed. */
    fun isBuiltIn(id: TemplateId): Boolean = id in TemplateId.builtIns

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

    /**
     * A template that breaks the rules falls back to its seed when it has one (3.0); an own template
     * without a seed is kept but archived so it can never crash a load (3.1).
     */
    private fun validated(template: StoredTemplate): StoredTemplate {
        if (template.id == TemplateId.OFF) return template
        if (TemplateRules.problems(template.blocks.toPlanBlocks()).isEmpty()) return template
        val seedFor = seed.firstOrNull { it.id == template.id }
        if (seedFor != null) {
            logger.info("template ${template.id.key} failed validation; using its seed")
            return seedFor
        }
        logger.info("template ${template.id.key} failed validation; archiving it")
        return template.copy(archived = true)
    }

    // 3.1.0: the only writers. Each persists exactly what it changed and returns its result.

    /** A new blank own template (`user_<n>`), inserted into the active profile before Off. */
    suspend fun create(title: String): StoredTemplate {
        val highest = stored.mapNotNull { it.id.key.removePrefix("user_").toIntOrNull() }.maxOrNull() ?: 0
        val key = "user_${highest + 1}"
        val id = TemplateId(key)
        val blocks = TemplateEditor.blank(key).toBlockSpecs(emptyList())
        val created = StoredTemplate(
            id = id,
            title = title,
            subtitle = subtitleFor(blocks),
            short = shortFor(emptySet()),
            iconKey = "event",
            accentArgb = accentFor(blocks),
            weekdays = emptySet(),
            blocks = blocks,
        )
        stored = stored + created
        val ids = profile.templateIds.toMutableList()
        val off = ids.indexOf(TemplateId.OFF)
        ids.add(if (off >= 0) off else ids.size, id)
        profile = profile.copy(templateIds = ids)
        prefs.write(PrefsFile.SETTINGS, KEY_TEMPLATES, stored, ListSerializer(StoredTemplate.serializer()))
        prefs.write(PrefsFile.SETTINGS, KEY_PROFILES, listOf(profile), ListSerializer(Profile.serializer()))
        return created
    }

    /** Saves an own template; false for built-ins or a template that breaks the rules. Recomputes the display subtitle and accent. */
    suspend fun save(template: StoredTemplate): Boolean {
        if (isBuiltIn(template.id)) return false
        if (TemplateRules.problems(template.blocks.toPlanBlocks()).isNotEmpty()) return false
        val saved = template.copy(subtitle = subtitleFor(template.blocks), accentArgb = accentFor(template.blocks))
        stored = stored.map { if (it.id == saved.id) saved else it }
        prefs.write(PrefsFile.SETTINGS, KEY_TEMPLATES, stored, ListSerializer(StoredTemplate.serializer()))
        return true
    }

    /** Deletes an own template: it stays in stored (archived) so History still resolves its title, and leaves every profile. */
    suspend fun delete(id: TemplateId): Boolean {
        if (isBuiltIn(id)) return false
        stored = stored.map { if (it.id == id) it.copy(archived = true) else it }
        if (id in profile.templateIds) {
            profile = profile.copy(templateIds = profile.templateIds.filter { it != id })
            prefs.write(PrefsFile.SETTINGS, KEY_PROFILES, listOf(profile), ListSerializer(Profile.serializer()))
        }
        prefs.write(PrefsFile.SETTINGS, KEY_TEMPLATES, stored, ListSerializer(StoredTemplate.serializer()))
        return true
    }

    /** Claims [days] for [id] (built-ins too): the days leave every other template and land here; shorts are recomputed for what changed. */
    suspend fun setWeekdays(id: TemplateId, days: Set<Int>) {
        var changed = false
        stored = stored.map { t ->
            when {
                t.id == id -> {
                    changed = true
                    t.copy(weekdays = days, short = shortFor(days))
                }
                t.weekdays.any { it in days } -> {
                    changed = true
                    val rest = t.weekdays - days
                    t.copy(weekdays = rest, short = shortFor(rest))
                }
                else -> t
            }
        }
        if (changed) prefs.write(PrefsFile.SETTINGS, KEY_TEMPLATES, stored, ListSerializer(StoredTemplate.serializer()))
    }

    /**
     * Replaces every occurrence of custom category [categoryId] in stored templates by [info], or by
     * free time when [info] is null; the blocks are re-normalized so the result stays a valid template.
     */
    suspend fun replaceCategory(categoryId: String, info: CategoryInfo?) {
        val replacement = info?.let { BlockType.Custom(it.id, it.name, it.colorArgb, it.work, it.pomodoro) }
            ?: BlockType.Free
        stored = stored.map { t ->
            val originals = t.blocks
            val replaced = originals.map { b ->
                val custom = b.type as? BlockType.Custom
                if (custom != null && custom.id == categoryId) b.copy(type = replacement) else b
            }
            if (replaced == originals) t
            else t.copy(blocks = TemplateRules.normalize(replaced.toPlanBlocks()).toBlockSpecs(originals))
        }
        prefs.write(PrefsFile.SETTINGS, KEY_TEMPLATES, stored, ListSerializer(StoredTemplate.serializer()))
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
