package com.roni.blocks.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.roni.blocks.core.storage.ExplainerKey

/** One explainer/tour card (3.3.0); the tour and the first-visit cards share this text. */
data class HowToCard(val key: ExplainerKey, val icon: ImageVector, val title: String, val text: String)

/** The five cards, in tour order; the first-visit cards look the same up by [ExplainerKey]. */
val HowToCards: List<HowToCard> = listOf(
    HowToCard(
        ExplainerKey.PROFILES,
        Icons.Outlined.People,
        "Profiles",
        "A profile is how your weeks look right now, like Unemployed or Employed. It holds the templates " +
            "you use and which one each day suggests. Switch it at the top of the morning picker.",
    ),
    HowToCard(
        ExplainerKey.TEMPLATES,
        Icons.Outlined.ViewAgenda,
        "Templates",
        "A template is one kind of day: a gym day, an office day. All templates live in one library, and " +
            "each profile picks the ones it uses. Change a template once and every profile using it follows.",
    ),
    HowToCard(
        ExplainerKey.BLOCKS,
        Icons.Outlined.Schedule,
        "Blocks",
        "Open a template and tap + Add block. Choose a category, a start and a length. Blocks after it move " +
            "later to make room, and kid time at the end gets shorter. Built-in templates can't be changed: " +
            "start your own from blank.",
    ),
    HowToCard(
        ExplainerKey.DAYS,
        Icons.Outlined.CalendarMonth,
        "Days",
        "On a profile's page, tap a weekday to choose its template. That morning the picker marks it Suggested; " +
            "you can always pick another.",
    ),
    HowToCard(
        ExplainerKey.CATEGORIES,
        Icons.Outlined.Palette,
        "Categories",
        "Categories are the kinds of blocks: CV, Coding, Gym, or your own. Each has a colour, Pomodoro on or " +
            "off, and whether it counts as work. Every profile shares them.",
    ),
)

fun howToCard(key: ExplainerKey): HowToCard = HowToCards.first { it.key == key }

/** The first-visit card (3.3.0): only "Got it" closes it. */
@Composable
fun ExplainerDialog(key: ExplainerKey, onGotIt: () -> Unit) {
    val card = howToCard(key)
    val colors = AppTheme.colors
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Box(
                    Modifier.size(96.dp).background(colors.surfaceRaised, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(card.icon, null, tint = colors.textPrimary, modifier = Modifier.size(56.dp))
                }
                Text(card.title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Text(
                    card.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onGotIt,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("explainer_ok"),
                ) {
                    Text("Got it")
                }
            }
        }
    }
}
