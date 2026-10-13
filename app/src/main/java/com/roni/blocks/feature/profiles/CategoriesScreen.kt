package com.roni.blocks.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.roni.blocks.designsystem.AppTheme
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing
import com.roni.blocks.feature.templates.CategorySection

/** The shared categories screen (3.2.0): reached from the Profiles list; the editor itself is unchanged. */
@Composable
fun CategoriesRoute(onBack: () -> Unit) {
    var howTo by remember { mutableStateOf(false) }
    ScreenScaffold(
        topBar = { ProfilesTopBar(title = "Categories", onBack = onBack, onHowTo = { howTo = true }) },
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.m),
            ) {
                CategorySection()
            }
            if (howTo) {
                Box(Modifier.fillMaxSize().background(AppTheme.colors.background).clickable { howTo = false }.testTag("how_to_placeholder"))
            }
        }
    }
}
