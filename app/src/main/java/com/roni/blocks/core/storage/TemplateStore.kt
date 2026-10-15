package com.roni.blocks.core.storage

import android.content.Context
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.model.BlockSpec
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.CategoryInfo
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.LocalDateSerializer
import com.roni.blocks.core.model.Pomodoro
import com.roni.blocks.core.model.Profile
import com.roni.blocks.core.model.StoredTemplate
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.UNEMPLOYED_PROFILE
import com.roni.blocks.core.model.accentFor
import com.roni.blocks.core.model.builtInTemplateSeeds
import com.roni.blocks.core.model.subtitleFor
import com.roni.blocks.core.profiles.ProfileBook
import com.roni.blocks.core.profiles.ProfileEdit
import com.roni.blocks.core.profiles.Profiles
import com.roni.blocks.core.schedule.TemplateEditor
import com.roni.blocks.core.schedule.TemplateRules
import com.roni.blocks.core.schedule.toBlockSpecs
import com.roni.blocks.core.schedule.toPlanBlocks
import com.roni.blocks.core.steps.StepStateSerializer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    @Volatile private var chosen = false
    @Volatile private var book: ProfileBook = Profiles.repair(
        listOf(UNEMPLOYED_PROFILE), UNEMPLOYED_PROFILE.id, seed.associate { it.id to it.weekdays }, UNEMPLOYED_PROFILE,
    )

    private val _templates = MutableStateFlow(all())

    /** The active profile's templates, in its order (3.1.1). Every write and every load publishes. */
    val templates: StateFlow<List<StoredTemplate>> = _templates.asStateFlow()

    private val _profiles = MutableStateFlow(book)

    /** Every profile plus the active one (3.2.0). Every write and every load publishes. */
    val profiles: StateFlow<ProfileBook> = _profiles.asStateFlow()

    init {
        instance = this
    }

    /** The active profile's templates, in its order (deleted ones are archived and no longer listed). */
    fun all(): List<StoredTemplate> = profileTemplates(book.activeId)

    /** Every stored template that isn't archived (3.2.0): built-ins first, then yours in creation order. */
    fun library(): List<StoredTemplate> {
        val live = stored.filter { !it.archived }
        return live.filter { isBuiltIn(it.id) } + live.filter { !isBuiltIn(it.id) }
    }

    /** That profile's templates in its order (3.2.0). */
    fun profileTemplates(profileId: String): List<StoredTemplate> {
        val ids = book.find(profileId)?.templateIds ?: return emptyList()
        return ids.mapNotNull { id -> stored.firstOrNull { it.id == id && !it.archived } }
    }

    /** The stored template, falling back to its built-in seed; null only for an unknown id. */
    fun find(id: TemplateId): StoredTemplate? = stored.firstOrNull { it.id == id } ?: seed.firstOrNull { it.id == id }

    /** True for the four templates 2.x shipped: they cannot be saved, deleted, or lose their seed. */
    fun isBuiltIn(id: TemplateId): Boolean = id in TemplateId.builtIns

    /** The template the active profile suggests on [day] (3.2.0). */
    fun suggestedFor(day: DayOfWeek): TemplateId? = Profiles.suggestedFor(book, day.isoDayNumber)

    /** The active profile's display name, for the day it starts (3.2.0). */
    fun activeProfileName(): String = book.active.name

    /** The active profile's id. */
    fun activeProfileId(): String = book.activeId

    /** Whether a profile was ever chosen (3.3.0): false only on a fresh install, until one is picked or made. */
    fun profileChosen(): Boolean = chosen

    /** Remembers that a profile was chosen (3.3.0); the first-run menu is only for fresh installs. */
    suspend fun markProfileChosen() {
        chosen = true
        prefs.write(PrefsFile.SETTINGS, KEY_CHOSEN, true, Boolean.serializer())
    }

    suspend fun load() {
        val templates = prefs.read(PrefsFile.SETTINGS, KEY_TEMPLATES, ListSerializer(StoredTemplate.serializer()))
        val profiles = prefs.read(PrefsFile.SETTINGS, KEY_PROFILES, ListSerializer(Profile.serializer()))
        val activeId = prefs.read(PrefsFile.SETTINGS, KEY_ACTIVE, String.serializer())
        if (templates == null || profiles == null || activeId == null) {
            // 2.6.2 stored no templates at all. If the app already holds a day, a history record or a
            // step state it isn't a fresh install: seed the profile silently and skip the welcome
            // screen (3.4.0). Only a truly empty app is left unchosen.
            val existing = templates != null || hasExistingData()
            saveSeed()
            if (existing) markProfileChosen()
            return
        }
        // The stored templates were read, not seeded now: this install has chosen before (3.3.0).
        val chosenFlag = prefs.read(PrefsFile.SETTINGS, KEY_CHOSEN, Boolean.serializer())
        chosen = chosenFlag ?: true
        if (chosenFlag == null) prefs.write(PrefsFile.SETTINGS, KEY_CHOSEN, true, Boolean.serializer())
        stored = templates.map { validated(it) }
        val repaired = Profiles.repair(profiles, activeId, stored.associate { it.id to it.weekdays }, UNEMPLOYED_PROFILE)
        book = repaired
        if (repaired.profiles != profiles || repaired.activeId != activeId) {
            prefs.write(PrefsFile.SETTINGS, KEY_PROFILES, book.profiles, ListSerializer(Profile.serializer()))
            prefs.write(PrefsFile.SETTINGS, KEY_ACTIVE, book.activeId, String.serializer())
        }
        _profiles.value = book
        _templates.value = all()
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

    /**
     * A new blank own template (`user_<n>`), added to the library and to [profileId]'s templates
     * before Off (3.2.0). New templates write `emptySet()` / `""`.
     */
    suspend fun createIn(profileId: String, title: String): StoredTemplate {
        val highest = stored.mapNotNull { it.id.key.removePrefix("user_").toIntOrNull() }.maxOrNull() ?: 0
        val key = "user_${highest + 1}"
        val id = TemplateId(key)
        val blocks = TemplateEditor.blank(key).toBlockSpecs(emptyList())
        val created = StoredTemplate(
            id = id,
            title = title,
            subtitle = subtitleFor(blocks),
            short = "",
            iconKey = "event",
            accentArgb = accentFor(blocks),
            weekdays = emptySet(),
            blocks = blocks,
        )
        stored = stored + created
        prefs.write(PrefsFile.SETTINGS, KEY_TEMPLATES, stored, ListSerializer(StoredTemplate.serializer()))
        edit { Profiles.addTemplate(it, profileId, id) }
        return created
    }

    /** Runs one profile change through [Profiles] (3.2.0); on Ok, persists the book and publishes both flows. */
    suspend fun edit(change: (ProfileBook) -> ProfileEdit): ProfileEdit {
        val result = change(book)
        if (result is ProfileEdit.Ok) {
            book = result.book
            prefs.write(PrefsFile.SETTINGS, KEY_PROFILES, book.profiles, ListSerializer(Profile.serializer()))
            prefs.write(PrefsFile.SETTINGS, KEY_ACTIVE, book.activeId, String.serializer())
            _profiles.value = book
            _templates.value = all()
        }
        return result
    }

    /** Saves an own template; false for built-ins or a template that breaks the rules. Recomputes the display subtitle and accent. */
    suspend fun save(template: StoredTemplate): Boolean {
        if (isBuiltIn(template.id)) return false
        if (TemplateRules.problems(template.blocks.toPlanBlocks()).isNotEmpty()) return false
        val saved = template.copy(subtitle = subtitleFor(template.blocks), accentArgb = accentFor(template.blocks))
        stored = stored.map { if (it.id == saved.id) saved else it }
        prefs.write(PrefsFile.SETTINGS, KEY_TEMPLATES, stored, ListSerializer(StoredTemplate.serializer()))
        _templates.value = all()
        return true
    }

    /** Deletes an own template: it stays in stored (archived) so History still resolves its title, and leaves every profile (3.2.0). */
    suspend fun delete(id: TemplateId): Boolean {
        if (isBuiltIn(id)) return false
        stored = stored.map { if (it.id == id) it.copy(archived = true) else it }
        prefs.write(PrefsFile.SETTINGS, KEY_TEMPLATES, stored, ListSerializer(StoredTemplate.serializer()))
        edit { ProfileEdit.Ok(Profiles.dropTemplate(it, id)) }
        return true
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
        _templates.value = all()
    }

    /** True when the app already holds a day, a history record or a step state (3.4.0). */
    private suspend fun hasExistingData(): Boolean =
        prefs.read(PrefsFile.DAY, DAY_DATE, LocalDateSerializer) != null ||
            prefs.read(PrefsFile.DAY, STEP_STATE, StepStateSerializer) != null ||
            prefs.keys(PrefsFile.HISTORY).any { it.startsWith(RECORD_PREFIX) }

    private suspend fun saveSeed() {
        stored = seed
        book = Profiles.repair(
            listOf(UNEMPLOYED_PROFILE), UNEMPLOYED_PROFILE.id, seed.associate { it.id to it.weekdays }, UNEMPLOYED_PROFILE,
        )
        prefs.write(PrefsFile.SETTINGS, KEY_TEMPLATES, seed, ListSerializer(StoredTemplate.serializer()))
        prefs.write(PrefsFile.SETTINGS, KEY_PROFILES, book.profiles, ListSerializer(Profile.serializer()))
        prefs.write(PrefsFile.SETTINGS, KEY_ACTIVE, book.activeId, String.serializer())
        _profiles.value = book
        _templates.value = all()
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
        private const val KEY_CHOSEN = "profile_chosen"
        // The orchestrator's own DAY keys and HistoryManager's day prefix (3.4.0), read only to tell
        // an upgrade from 2.6.2 apart from a fresh install.
        private const val DAY_DATE = "today_date"
        private const val STEP_STATE = "step_state"
        private const val RECORD_PREFIX = "record_"

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
