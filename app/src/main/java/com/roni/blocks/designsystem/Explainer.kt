package com.roni.blocks.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.roni.blocks.core.storage.ExplainerKey
import com.roni.blocks.designsystem.icons.AppIcons

/** One explainer/tour card (3.3.0); the tour and the in-screen hints share this text. */
data class HowToCard(
    val key: ExplainerKey,
    val icon: ImageVector,
    val title: String,
    val hintTitle: String,
    val text: String,
)

/** The five cards, in tour order; the hints look the same up by [ExplainerKey]. */
val HowToCards: List<HowToCard> = listOf(
    HowToCard(
        ExplainerKey.PROFILES,
        AppIcons.People,
        "Profiles",
        "What's a profile?",
        "A profile is how your weeks look right now, like Unemployed or Employed. It holds the templates " +
            "you use and which one each day suggests. Switch it at the top of the morning picker.",
    ),
    HowToCard(
        ExplainerKey.TEMPLATES,
        AppIcons.ViewAgenda,
        "Templates",
        "What's a template?",
        "A template is one kind of day: a gym day, an office day. All templates live in one library, and " +
            "each profile picks the ones it uses. Change a template once and every profile using it follows.",
    ),
    HowToCard(
        ExplainerKey.BLOCKS,
        AppIcons.Schedule,
        "Blocks",
        "Building a day",
        "Open a template and tap + Add block. Choose a category, a start and a length. Blocks after it move " +
            "later to make room, and kid time at the end gets shorter. Built-in templates can't be changed: " +
            "start your own from blank.",
    ),
    HowToCard(
        ExplainerKey.DAYS,
        AppIcons.CalendarMonth,
        "Days",
        "Which day suggests what",
        "On a profile's page, tap a weekday to choose its template. That morning the picker marks it Suggested; " +
            "you can always pick another.",
    ),
    HowToCard(
        ExplainerKey.CATEGORIES,
        AppIcons.Palette,
        "Categories",
        "What are categories?",
        "Categories are the kinds of blocks: CV, Coding, Gym, or your own. Each has a colour, Pomodoro on or " +
            "off, and whether it counts as work. Every profile shares them.",
    ),
)

fun howToCard(key: ExplainerKey): HowToCard = HowToCards.first { it.key == key }

/**
 * The soft in-screen first-time hint (3.4.0): a card at the top of the screen, closed only by "Got it".
 * Screens put it inside an [androidx.compose.animation.AnimatedVisibility] so it collapses smoothly.
 */
@Composable
fun HintCard(key: ExplainerKey, onGotIt: () -> Unit, modifier: Modifier = Modifier) {
    val card = howToCard(key)
    val colors = AppTheme.colors
    AppCard(
        modifier = modifier.fillMaxWidth().testTag("hint_${key.name.lowercase()}"),
        borderColor = colors.outline,
        containerColor = colors.surfaceRaised,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(Metrics.iconLarge).background(colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(card.icon, null, tint = colors.primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(Spacing.m))
            Text(card.hintTitle, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        }
        Spacer(Modifier.height(Spacing.s))
        Text(card.text, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        TextButton(
            onClick = onGotIt,
            modifier = Modifier.align(Alignment.End).testTag("hint_ok"),
        ) {
            Text("Got it")
        }
    }
}
