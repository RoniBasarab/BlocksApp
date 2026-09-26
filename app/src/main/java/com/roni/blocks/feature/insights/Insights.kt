package com.roni.blocks.feature.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.roni.blocks.feature.weekly.Tabs
import com.roni.blocks.navigation.InsightsKey
import androidx.navigation3.runtime.NavKey

@Immutable
data class InsightsState(val rows: String = "Leak, gym, evenings — open after a day is flushed")

@Composable
fun InsightsRoute(onTab: (NavKey) -> Unit) {
    Scaffold(bottomBar = { Tabs(InsightsKey, onTab) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(InsightsState().rows)
        }
    }
}
