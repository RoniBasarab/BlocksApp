package com.roni.blocks.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.Work
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.builtInTemplateSeeds
import com.roni.blocks.core.storage.TemplateStore

@Immutable
data class TemplateDisplay(
    val title: String,
    val subtitle: String,
    val short: String,
    val icon: ImageVector,
    val accent: Color,
)

private fun iconFor(key: String): ImageVector = when (key) {
    "fitness" -> Icons.Outlined.FitnessCenter
    "code" -> Icons.Outlined.Code
    "weekend" -> Icons.Outlined.Weekend
    "games" -> Icons.Outlined.SportsEsports
    "work" -> Icons.Outlined.Work
    "school" -> Icons.Outlined.School
    "home" -> Icons.Outlined.Home
    else -> Icons.Outlined.Event
}

/** The stored template when the store is up, else the built-in seed, so a preview never blocks. */
fun templateDisplay(id: TemplateId): TemplateDisplay {
    val stored = TemplateStore.instance?.find(id)
    val seed = builtInTemplateSeeds[id]
    return TemplateDisplay(
        title = stored?.title ?: seed?.title ?: id.key,
        subtitle = stored?.subtitle ?: seed?.subtitle.orEmpty(),
        short = stored?.short ?: seed?.short.orEmpty(),
        icon = iconFor(stored?.iconKey ?: seed?.iconKey.orEmpty()),
        accent = Color(stored?.accentArgb ?: seed?.accentArgb ?: 0xFF7A9CC6),
    )
}

fun templateTitle(id: TemplateId): String = templateDisplay(id).title
