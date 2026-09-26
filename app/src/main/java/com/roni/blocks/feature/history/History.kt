package com.roni.blocks.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Immutable
data class HistoryState(val empty: Boolean = true)

@Composable
fun HistoryRoute(date: String? = null, onDay: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (date == null) {
            Text("History")
            Text("No days yet")
        } else {
            Text(date)
        }
    }
}
