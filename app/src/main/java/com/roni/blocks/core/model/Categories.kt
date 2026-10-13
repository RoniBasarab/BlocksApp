package com.roni.blocks.core.model

import kotlinx.serialization.Serializable

/** The colours a category can take (3.1.0): the four built-ins first, then eight that read well on the dark calendar. */
val CategoryPalette: List<Long> = listOf(
    0xFFA99BC9, 0xFF7A9CC6, 0xFF8FB58A, 0xFFD9C38A,
    0xFFE08A7A, 0xFFE3A35F, 0xFFC2C46A, 0xFF6FBFAF,
    0xFF6FB7D6, 0xFF8A92E0, 0xFFD58FC4, 0xFFA89A88,
)

/** One of your own block kinds (3.0.0). The block carries a copy, so history keeps what it was. */
@Serializable
data class CategoryInfo(
    val id: String,
    val name: String,
    val colorArgb: Long,
    val work: Boolean,
    val pomodoro: Boolean,
    val builtIn: Boolean,
)

@Serializable
data class CategorySet(val builtIns: List<CategoryInfo>, val customs: List<CategoryInfo> = emptyList()) {
    companion object {
        val DEFAULT = CategorySet(
            listOf(
                CategoryInfo("cv", "CV", 0xFFA99BC9, work = true, pomodoro = false, builtIn = true),
                CategoryInfo("coding", "Coding", 0xFF7A9CC6, work = true, pomodoro = true, builtIn = true),
                CategoryInfo("gym", "Gym", 0xFF8FB58A, work = true, pomodoro = false, builtIn = true),
                CategoryInfo("free", "Free", 0xFFD9C38A, work = false, pomodoro = false, builtIn = true),
            ),
        )
    }
}

/** The categories in use, readable from pure mappers and composables. Defaults until CategoryStore loads. */
object Categories {
    @Volatile var current: CategorySet = CategorySet.DEFAULT
    fun infoFor(type: BlockType): CategoryInfo? = when (type) {
        BlockType.Cv -> current.builtIns.first { it.id == "cv" }
        BlockType.Coding -> current.builtIns.first { it.id == "coding" }
        BlockType.Gym -> current.builtIns.first { it.id == "gym" }
        BlockType.Free -> current.builtIns.first { it.id == "free" }
        is BlockType.Custom -> CategoryInfo(type.id, type.name, type.colorArgb, type.work, type.pomodoro, builtIn = false)
        else -> null                                                  // kid time, Dishes, evening, Walk: not categories
    }
}
