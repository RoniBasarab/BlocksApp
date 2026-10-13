package com.roni.blocks.core.model

import kotlinx.serialization.Serializable

/** A template as stored data (3.0.0), instead of the fixed list 2.x shipped. */
@Serializable
data class StoredTemplate(
    val id: TemplateId,
    val title: String,                 // "Monday / Thursday"
    val subtitle: String,              // "Gym day"
    /** 3.1 only; read once by Profiles.repair (3.2.0). */
    val short: String,                 // "MON · THU"
    val iconKey: String,               // "fitness" | "code" | "weekend" | "games"
    val accentArgb: Long,
    /** 3.1 only; read once by Profiles.repair (3.2.0). */
    val weekdays: Set<Int>,            // ISO day numbers it's suggested on (Mon = 1 … Sun = 7); plain Ints serialize safely
    val blocks: List<BlockSpec>,
    val archived: Boolean = false,     // 3.1.0: a deleted own template stays in stored so History's titles still resolve
)

/** "SUN · TUE · WED": ISO days, Sunday first; no days → "NO DAYS". */
fun shortFor(days: Set<Int>): String =
    if (days.isEmpty()) "NO DAYS" else days.sortedBy { it % 7 }.joinToString(" · ") { listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")[it - 1] }

/** "6h 30m work", "No work" — the subtitle of your own templates (built-ins keep theirs). */
fun subtitleFor(blocks: List<BlockSpec>): String {
    val minutes = blocks.filter { it.type.isWork }.sumOf { it.minutes() }
    if (minutes == 0) return "No work"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "${h}h work" else "${h}h ${m}m work"
}

/** The colour of the work category with the most minutes; kid-time grey 0xFF9C968C if none. */
fun accentFor(blocks: List<BlockSpec>): Long {
    val work = blocks.filter { it.type.isWork }
    if (work.isEmpty()) return 0xFF9C968C
    return work.groupBy { Categories.infoFor(it.type)?.colorArgb }
        .filterKeys { it != null }
        .maxByOrNull { (_, bs) -> bs.sumOf { it.minutes() } }?.key ?: 0xFF9C968C
}

private fun BlockSpec.minutes(): Int = (end.hour * 60 + end.minute) - (start.hour * 60 + start.minute)

/** The icons your own templates can take (3.1.0); each maps to an icon in TemplateDisplay's [iconFor]. */
val OWN_TEMPLATE_ICONS = listOf("event", "code", "fitness", "weekend", "games", "work", "school", "home")

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val templateIds: List<TemplateId>,
    /** ISO day (Mon = 1 … Sun = 7) → the template it suggests (3.2.0). Null only in 3.1 data, until [Profiles.repair] moves the weekdays in. */
    val days: Map<Int, TemplateId>? = null,
)

/** The display seed and suggestion weekdays for the four templates 2.x shipped (3.0.0). Blocks come from assets. */
data class TemplateSeed(
    val title: String,
    val subtitle: String,
    val short: String,
    val iconKey: String,
    val accentArgb: Long,
    val weekdays: Set<Int>,
)

/** Today's `TemplateDisplay` texts/icons/accents, as the seed's plain data. */
val builtInTemplateSeeds: Map<TemplateId, TemplateSeed> = mapOf(
    TemplateId.MON_THU to TemplateSeed("Monday / Thursday", "Gym day", "MON · THU", "fitness", 0xFF8FB58A, setOf(1, 4)),
    TemplateId.SUN_TUE_WED to TemplateSeed(
        "Sunday / Tuesday / Wednesday", "Focus day", "SUN · TUE · WED", "code", 0xFF7A9CC6, setOf(7, 2, 3),
    ),
    TemplateId.FRI_SAT to TemplateSeed("Friday / Saturday", "Family day", "FRI · SAT", "weekend", 0xFFD9C38A, setOf(5, 6)),
    TemplateId.OFF to TemplateSeed("Off day", "Anything goes", "ANY DAY", "games", 0xFFB58FAA, emptySet()),
)

/** The one profile 3.0 creates: today's four templates, in picker order. */
val UNEMPLOYED_PROFILE = Profile("unemployed", "Unemployed", TemplateId.builtIns)
