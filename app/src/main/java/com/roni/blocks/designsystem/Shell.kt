package com.roni.blocks.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

enum class AppTab { HOME, WEEKLY, INSIGHTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    scrolled: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    onHistory: () -> Unit = {},
    onSettings: () -> Unit = {},
    showMenu: Boolean = true,
    onExplain: (() -> Unit)? = null,
) {
    TopAppBar(
        title = title,
        actions = {
            onExplain?.let { WhatTheseMeanButton(onClick = it) }
            actions()
            if (showMenu) {
                AppMenuButton { destination ->
                    when (destination) {
                        MenuDestination.History -> onHistory()
                        MenuDestination.Settings -> onSettings()
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        modifier = Modifier.topDivider(scrolled, MaterialTheme.colorScheme.outline),
    )
}

enum class MenuDestination(val label: String, val icon: ImageVector) {
    History("History", Icons.Outlined.History),
    Settings("Settings", Icons.Outlined.Settings),
}

@Composable
fun AppMenuButton(
    destinations: List<MenuDestination> = MenuDestination.entries,
    modifier: Modifier = Modifier,
    onSelect: (MenuDestination) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.testTag("menu_button")) {
        Icon(Icons.Outlined.Menu, "Menu")
    }
    DropdownMenu(
        expanded = open,
        onDismissRequest = { open = false },
        shape = AppShapes.card,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        destinations.forEach { destination ->
            DropdownMenuItem(
                text = { Text(destination.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) },
                leadingIcon = { Icon(destination.icon, null) },
                onClick = { open = false; onSelect(destination) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(
    title: String,
    onMenu: (MenuDestination) -> Unit,
    modifier: Modifier = Modifier,
    explainer: (@Composable () -> Unit)? = null,
) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        actions = {
            explainer?.invoke()
            AppMenuButton(onSelect = onMenu)
        },
        colors = screenTopBarColors(),
        modifier = modifier.testTag("top_bar"),
    )
}

private val ExplainerTerms = listOf(
    "Ran over" to "Time a block kept going after its planned end, until you tapped Next.",
    "Plan followed" to "Time you actually spent in CV, Coding and Gym, out of the time planned for them. Paused time doesn't count.",
    "Pause time" to "Short breaks you took inside a block with Pause.",
    "Evenings skipped" to "Days the evening never started because Kid asleep wasn't tapped before 22:30.",
    "Off days" to "Days you picked the Off template.",
    "Skipped blocks" to "Blocks that never happened because an earlier block ran past their end.",
)

@Composable
fun WhatTheseMeanDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What these mean") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ExplainerTerms.forEach { (term, meaning) ->
                    Text(term, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.m))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        modifier = Modifier.testTag("what_these_mean_dialog"),
    )
}

@Composable
fun WhatTheseMeanButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag("what_these_mean")) {
        Icon(Icons.Outlined.Info, contentDescription = "What these mean")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, onExplain: (() -> Unit)? = null) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
            }
        },
        actions = { onExplain?.let { WhatTheseMeanButton(onClick = it) } },
        colors = screenTopBarColors(),
        modifier = modifier.testTag("top_bar"),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun screenTopBarColors() = TopAppBarDefaults.centerAlignedTopAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
)

@Composable
fun AppBottomBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind {
            drawLine(colors.outline, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx())
        },
    ) {
        BarItem(AppTab.HOME, selected, Icons.Outlined.Today, "Home", onSelect)
        BarItem(AppTab.WEEKLY, selected, Icons.Outlined.BarChart, "Weekly", onSelect)
        BarItem(AppTab.INSIGHTS, selected, Icons.Outlined.Insights, "Insights", onSelect)
    }
}

@Composable
private fun Modifier.topDivider(scrolled: Boolean, color: Color): Modifier = drawBehind {
    if (!scrolled) return@drawBehind
    drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx())
}

@Composable
private fun RowScope.BarItem(tab: AppTab, selected: AppTab, icon: ImageVector, label: String, onSelect: (AppTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavigationBarItem(
        selected = tab == selected,
        onClick = { onSelect(tab) },
        icon = { Icon(icon, label) },
        label = { Text(label) },
        alwaysShowLabel = true,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = colors.primary,
            selectedTextColor = colors.primary,
            indicatorColor = colors.primary.copy(alpha = 0.16f),
            unselectedIconColor = colors.onSurfaceVariant,
            unselectedTextColor = colors.onSurfaceVariant,
        ),
    )
}
