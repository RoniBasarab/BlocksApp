package com.roni.blocks.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.TemplateId

@Immutable
data class TemplateDisplay(
    val title: String,
    val subtitle: String,
    val short: String,
    val icon: ImageVector,
    val accent: Color,
)

private val displays: Map<TemplateId, TemplateDisplay> = mapOf(
    TemplateId.MON_THU to TemplateDisplay(
        title = "Monday / Thursday",
        subtitle = "Gym day",
        short = "MON · THU",
        icon = Icons.Outlined.FitnessCenter,
        accent = blockColors(BlockType.Gym).accent,
    ),
    TemplateId.SUN_TUE_WED to TemplateDisplay(
        title = "Sunday / Tuesday / Wednesday",
        subtitle = "Focus day",
        short = "SUN · TUE · WED",
        icon = Icons.Outlined.Code,
        accent = blockColors(BlockType.Coding).accent,
    ),
    TemplateId.FRI_SAT to TemplateDisplay(
        title = "Friday / Saturday",
        subtitle = "Family day",
        short = "FRI · SAT",
        icon = Icons.Outlined.Weekend,
        accent = blockColors(BlockType.Free).accent,
    ),
    TemplateId.OFF to TemplateDisplay(
        title = "Off day",
        subtitle = "Anything goes",
        short = "ANY DAY",
        icon = Icons.Outlined.SportsEsports,
        accent = blockColors(BlockType.EveningChoice(Choice.CHILL)).accent,
    ),
)

fun templateDisplay(id: TemplateId): TemplateDisplay = displays.getValue(id)

fun templateTitle(id: TemplateId): String = templateDisplay(id).title
