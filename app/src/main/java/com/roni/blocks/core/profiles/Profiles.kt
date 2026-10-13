package com.roni.blocks.core.profiles

import com.roni.blocks.core.model.Profile
import com.roni.blocks.core.model.TemplateId

/** Every profile plus the active one's id (3.2.0). Invariants: at least one profile; ids and names unique; every profile holds Off, last. */
data class ProfileBook(val profiles: List<Profile>, val activeId: String) {
    val active: Profile get() = profiles.first { it.id == activeId }
    fun find(id: String): Profile? = profiles.firstOrNull { it.id == id }
}

/** The outcome of one profile edit. */
sealed interface ProfileEdit {
    data class Ok(val book: ProfileBook) : ProfileEdit
    data class Refused(val why: ProfileRefusal) : ProfileEdit
}

enum class ProfileRefusal {
    /** "Give it a name." */
    EMPTY_NAME,
    /** "That name is taken." */
    NAME_TAKEN,
    /** "24 characters at most." */
    TOO_LONG,
    /** "You need at least one profile." */
    LAST_PROFILE,
    /** "Switch to another profile first." */
    ACTIVE_PROFILE,
    /** "Off day is in every profile." */
    OFF_STAYS,
    /** The profile or template isn't there (a stale screen); the UI shows nothing. */
    UNKNOWN,
}

/**
 * Profiles (3.2.0), pure. Templates are a shared library; a profile is a name, the library templates it
 * uses (in picker order, Off always last) and which of them each ISO weekday suggests (Mon = 1 … Sun = 7).
 * A day points at most at one template, and only at one the profile uses.
 * Talks to: nothing. TemplateStore persists the book after every Ok.
 */
object Profiles {
    const val MAX_NAME = 24

    /**
     * Load-time repair and the 3.1 → 3.2 move of weekdays: a profile whose [Profile.days] is null takes
     * them from its templates' 3.1 weekdays ([templateDays]; the first template in its order wins a day).
     * Every profile ends with Off exactly once, days only point at templates it uses, and a missing
     * active id falls back to the first profile.
     */
    fun repair(profiles: List<Profile>, activeId: String, templateDays: Map<TemplateId, Set<Int>>, fallback: Profile): ProfileBook {
        val fixed = profiles.ifEmpty { listOf(fallback) }.map { p ->
            val ids = p.templateIds.distinct().filter { it != TemplateId.OFF } + TemplateId.OFF
            val days = p.days ?: buildMap {
                for (id in ids) for (d in templateDays[id].orEmpty().sorted()) if (d in 1..7 && d !in this) put(d, id)
            }
            p.copy(templateIds = ids, days = days.filter { (d, id) -> d in 1..7 && id in ids }.sorted())
        }
        val active = if (fixed.any { it.id == activeId }) activeId else fixed.first().id
        return ProfileBook(fixed, active)
    }

    /** A new profile, "profile_<n>": only Off, or a copy of the active profile's templates and days. Returns the book; the new one is last. */
    fun create(book: ProfileBook, name: String, copyActive: Boolean): ProfileEdit {
        val clean = name.trim()
        nameProblem(book, clean, exceptId = null)?.let { return ProfileEdit.Refused(it) }
        val n = (book.profiles.mapNotNull { it.id.removePrefix("profile_").takeIf { r -> r != it.id }?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        val source = book.active
        val profile = if (copyActive) {
            Profile("profile_$n", clean, source.templateIds, source.days.orEmpty())
        } else {
            Profile("profile_$n", clean, listOf(TemplateId.OFF), emptyMap())
        }
        return ProfileEdit.Ok(book.copy(profiles = book.profiles + profile))
    }

    fun rename(book: ProfileBook, id: String, name: String): ProfileEdit {
        val clean = name.trim()
        if (book.find(id) == null) return ProfileEdit.Refused(ProfileRefusal.UNKNOWN)
        nameProblem(book, clean, exceptId = id)?.let { return ProfileEdit.Refused(it) }
        return ok(book, id) { it.copy(name = clean) }
    }

    /** Deletes a profile that isn't active and isn't the last. Its templates stay in the library. */
    fun delete(book: ProfileBook, id: String): ProfileEdit = when {
        book.find(id) == null -> ProfileEdit.Refused(ProfileRefusal.UNKNOWN)
        book.profiles.size == 1 -> ProfileEdit.Refused(ProfileRefusal.LAST_PROFILE)
        id == book.activeId -> ProfileEdit.Refused(ProfileRefusal.ACTIVE_PROFILE)
        else -> ProfileEdit.Ok(book.copy(profiles = book.profiles.filter { it.id != id }))
    }

    fun switchTo(book: ProfileBook, id: String): ProfileEdit =
        if (book.find(id) == null) ProfileEdit.Refused(ProfileRefusal.UNKNOWN) else ProfileEdit.Ok(book.copy(activeId = id))

    /** Adds a library template to a profile, just before Off. Already there: unchanged. */
    fun addTemplate(book: ProfileBook, profileId: String, templateId: TemplateId): ProfileEdit {
        val p = book.find(profileId) ?: return ProfileEdit.Refused(ProfileRefusal.UNKNOWN)
        if (templateId in p.templateIds) return ProfileEdit.Ok(book)
        return ok(book, profileId) { it.copy(templateIds = it.templateIds.filter { t -> t != TemplateId.OFF } + templateId + TemplateId.OFF) }
    }

    /** Takes a template out of a profile (it stays in the library); its days in that profile become empty. Off stays. */
    fun removeTemplate(book: ProfileBook, profileId: String, templateId: TemplateId): ProfileEdit {
        if (templateId == TemplateId.OFF) return ProfileEdit.Refused(ProfileRefusal.OFF_STAYS)
        val p = book.find(profileId) ?: return ProfileEdit.Refused(ProfileRefusal.UNKNOWN)
        if (templateId !in p.templateIds) return ProfileEdit.Refused(ProfileRefusal.UNKNOWN)
        return ok(book, profileId) { it.copy(templateIds = it.templateIds - templateId, days = it.days.orEmpty().filterValues { t -> t != templateId }) }
    }

    /** Day [iso] of a profile suggests [templateId], or nothing with null. The template must be in the profile. */
    fun setDay(book: ProfileBook, profileId: String, iso: Int, templateId: TemplateId?): ProfileEdit {
        val p = book.find(profileId) ?: return ProfileEdit.Refused(ProfileRefusal.UNKNOWN)
        if (iso !in 1..7) return ProfileEdit.Refused(ProfileRefusal.UNKNOWN)
        if (templateId != null && templateId !in p.templateIds) return ProfileEdit.Refused(ProfileRefusal.UNKNOWN)
        return ok(book, profileId) {
            val days = it.days.orEmpty().toMutableMap()
            if (templateId == null) days.remove(iso) else days[iso] = templateId
            it.copy(days = days.sorted())
        }
    }

    /** A library template was deleted: it leaves every profile and every day. */
    fun dropTemplate(book: ProfileBook, templateId: TemplateId): ProfileBook =
        if (templateId == TemplateId.OFF) book
        else book.copy(profiles = book.profiles.map { p -> p.copy(templateIds = p.templateIds - templateId, days = p.days.orEmpty().filterValues { it != templateId }) })

    /** The template the active profile suggests on [iso], if any. */
    fun suggestedFor(book: ProfileBook, iso: Int): TemplateId? = book.active.days.orEmpty()[iso]

    /** The days a template is suggested on in [profile]: what the picker card shows ("SUN · TUE · WED"). */
    fun daysOf(profile: Profile, templateId: TemplateId): Set<Int> = profile.days.orEmpty().filterValues { it == templateId }.keys

    private fun Map<Int, TemplateId>.sorted(): Map<Int, TemplateId> = entries.sortedBy { it.key }.associate { it.key to it.value }

    private fun nameProblem(book: ProfileBook, clean: String, exceptId: String?): ProfileRefusal? = when {
        clean.isEmpty() -> ProfileRefusal.EMPTY_NAME
        clean.length > MAX_NAME -> ProfileRefusal.TOO_LONG
        book.profiles.any { it.id != exceptId && it.name.equals(clean, ignoreCase = true) } -> ProfileRefusal.NAME_TAKEN
        else -> null
    }

    private fun ok(book: ProfileBook, id: String, change: (Profile) -> Profile): ProfileEdit =
        ProfileEdit.Ok(book.copy(profiles = book.profiles.map { if (it.id == id) change(it) else it }))
}
