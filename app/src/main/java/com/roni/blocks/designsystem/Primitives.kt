package com.roni.blocks.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Full-width, gutter-padded scrolling column every screen body is built from. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    menuScreen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bottomInset = if (menuScreen) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Spacing.l
    } else {
        Spacing.l
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.l, top = Spacing.s, end = Spacing.l, bottom = bottomInset),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
        content = content,
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    extra: @Composable (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    Box(modifier.fillMaxSize().padding(horizontal = Spacing.xxl), contentAlignment = Alignment.Center) {
        Column(Modifier.testTag("empty_state"), horizontalAlignment = Alignment.CenterHorizontally) {
            IconCircle(icon, size = Metrics.emptyIconCircle, iconSize = Metrics.emptyIcon, shape = CircleShape)
            Spacer(Modifier.height(Spacing.l))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.s))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
            extra?.let {
                Spacer(Modifier.height(Spacing.l))
                it()
            }
        }
    }
}

/** A card whose rows own their own padding, so [SettingsRow] spans it edge to edge. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AppCard(modifier.testTag("section_card").fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(content = content)
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = AppTheme.colors.textPrimary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .testTag("settings_row")
            .fillMaxWidth()
            .heightIn(min = Metrics.row)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
            }
        }
        trailing?.invoke()
    }
}

/** Indented to clear [IconCircle] inside a [SectionCard]. */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(start = Metrics.dividerStart), color = AppTheme.colors.outline)
}
