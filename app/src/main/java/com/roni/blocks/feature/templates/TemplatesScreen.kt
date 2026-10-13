package com.roni.blocks.feature.templates

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.designsystem.AppBottomBar
import com.roni.blocks.designsystem.AppTab
import com.roni.blocks.designsystem.AppTopBar
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.feature.weekly.tabKey
import com.roni.blocks.navigation.HistoryKey
import com.roni.blocks.navigation.SettingsKey

/**
 * The new 3.1.0 Templates tab (Base, slice 2): the scaffold with the top bar (History / Settings)
 * and bottom bar, and a [LazyColumn] of the two read-only sections. Nobody edits this file later:
 * Track A owns [CategorySection], Track B owns [TemplateSection] and its editor.
 */
@Composable
fun TemplatesRoute(
    onTab: (NavKey) -> Unit,
    onMenu: (NavKey) -> Unit,
    onOpenTemplate: (TemplateId) -> Unit,
    onNewTemplate: () -> Unit,
) {
    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = { Text("Templates", style = MaterialTheme.typography.titleLarge) },
                onHistory = { onMenu(HistoryKey) },
                onSettings = { onMenu(SettingsKey) },
            )
        },
        bottomBar = { AppBottomBar(AppTab.TEMPLATES) { onTab(tabKey(it)) } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.m),
        ) {
            item(key = "templates") { TemplateSection(onOpenTemplate = onOpenTemplate, onNewTemplate = onNewTemplate) }
            item(key = "categories") {
                Spacer(Modifier.height(Spacing.l))
                CategorySection()
            }
        }
    }
}