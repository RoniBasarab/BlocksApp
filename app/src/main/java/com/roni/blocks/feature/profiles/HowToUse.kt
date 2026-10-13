package com.roni.blocks.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roni.blocks.designsystem.AppShapes
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.Spacing

/** The 5-card tour (3.2.0): the caller owns [page]; this just renders it and reports page/close. */
sealed interface HowToAction {
    data class Page(val page: Int) : HowToAction
    data object Close : HowToAction
}

private data class HowToCard(val icon: ImageVector, val title: String, val text: String)

private val Cards = listOf(
    HowToCard(
        Icons.Outlined.People,
        "Profiles",
        "A profile is how your weeks look right now, like Unemployed or Employed. It holds the templates " +
            "you use and which one each day suggests. Switch it at the top of the morning picker.",
    ),
    HowToCard(
        Icons.Outlined.ViewAgenda,
        "Templates",
        "A template is one kind of day: a gym day, an office day. All templates live in one library, and " +
            "each profile picks the ones it uses. Change a template once and every profile using it follows.",
    ),
    HowToCard(
        Icons.Outlined.Schedule,
        "Blocks",
        "Open a template and tap + Add block. Choose a category, a start and a length. Blocks after it move " +
            "later to make room, and kid time at the end gets shorter. Built-in templates can't be changed: " +
            "start your own from blank.",
    ),
    HowToCard(
        Icons.Outlined.CalendarMonth,
        "Days",
        "On a profile's page, tap a weekday to choose its template. That morning the picker marks it Suggested; " +
            "you can always pick another.",
    ),
    HowToCard(
        Icons.Outlined.Palette,
        "Categories",
        "Categories are the kinds of blocks: CV, Coding, Gym, or your own. Each has a colour, Pomodoro on or " +
            "off, and whether it counts as work. Every profile shares them.",
    ),
)

/** The count of tour cards; useful for tests. */
val HOW_TO_PAGES: Int get() = Cards.size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToUse(page: Int, onAction: (HowToAction) -> Unit) {
    val colors = AppTheme.colors
    val pager = rememberPagerState(initialPage = page) { Cards.size }
    LaunchedEffect(page) { if (pager.currentPage != page) pager.animateScrollToPage(page) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { current -> if (current != page) onAction(HowToAction.Page(current)) }
    }
    ModalBottomSheet(
        onDismissRequest = { onAction(HowToAction.Close) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.sheet,
        containerColor = colors.surface,
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().testTag("how_to_pager")) { index ->
                val card = Cards[index]
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
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Cards.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (index == page) colors.primary else colors.outline, CircleShape),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = { onAction(HowToAction.Page((page - 1).coerceAtLeast(0))) },
                    enabled = page > 0,
                ) {
                    Text("Back")
                }
                TextButton(
                    onClick = { if (page == Cards.lastIndex) onAction(HowToAction.Close) else onAction(HowToAction.Page(page + 1)) },
                    modifier = Modifier.testTag("how_to_next"),
                ) {
                    Text(if (page == Cards.lastIndex) "Done" else "Next")
                }
            }
        }
    }
}
