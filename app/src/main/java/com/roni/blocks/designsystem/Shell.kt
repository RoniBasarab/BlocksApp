package com.roni.blocks.designsystem

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
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
import androidx.compose.ui.unit.dp

enum class AppTab { HOME, WEEKLY, INSIGHTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: @Composable () -> Unit,
    scrolled: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    onHistory: () -> Unit = {},
    onDebt: () -> Unit = {},
    onSettings: () -> Unit = {},
    showMenu: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        title = title,
        actions = {
            actions()
            if (showMenu) {
                IconButton(onClick = { open = true }) { Icon(Icons.Outlined.Menu, "Menu") }
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    shape = AppShapes.card,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    MenuRow(Icons.Outlined.History, "History") { open = false; onHistory() }
                    MenuRow(Icons.Outlined.HourglassBottom, "Time Debt") { open = false; onDebt() }
                    MenuRow(Icons.Outlined.Settings, "Settings") { open = false; onSettings() }
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

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick,
    )
}

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
