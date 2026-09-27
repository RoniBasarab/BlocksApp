package com.roni.blocks.feature.timedebt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.roni.blocks.designsystem.AppCard
import com.roni.blocks.designsystem.AppTopBar
import com.roni.blocks.designsystem.ScreenScaffold
import com.roni.blocks.designsystem.Spacing

@Composable
fun TimeDebtRoute(onBack: () -> Unit) {
    ScreenScaffold(
        topBar = {
            AppTopBar(
                title = { Text("Time Debt", style = MaterialTheme.typography.titleLarge) },
                showMenu = false,
                actions = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(top = Spacing.l)) {
            AppCard {
                Text("This week", style = MaterialTheme.typography.labelMedium)
                Text("0 min", style = MaterialTheme.typography.displaySmall.merge(com.roni.blocks.designsystem.AppType.numbers), color = com.roni.blocks.designsystem.Good)
                Text("No debt. Clean week.", style = MaterialTheme.typography.bodyMedium, color = com.roni.blocks.designsystem.Good)
            }
        }
    }
}
