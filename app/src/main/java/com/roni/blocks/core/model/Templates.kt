package com.roni.blocks.core.model

import kotlinx.serialization.Serializable

/** A template as stored data (3.0.0), instead of the fixed list 2.x shipped. */
@Serializable
data class StoredTemplate(
    val id: TemplateId,
    val title: String,                 // "Monday / Thursday"
    val subtitle: String,              // "Gym day"
    val short: String,                 // "MON · THU"
    val iconKey: String,               // "fitness" | "code" | "weekend" | "games"
    val accentArgb: Long,
    val weekdays: Set<Int>,            // ISO day numbers it's suggested on (Mon = 1 … Sun = 7); plain Ints serialize safely
    val blocks: List<BlockSpec>,
)

@Serializable
data class Profile(val id: String, val name: String, val templateIds: List<TemplateId>)

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
