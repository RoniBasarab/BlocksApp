package com.roni.blocks.feature.weekly

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.roni.blocks.navigation.HomeKey
import com.roni.blocks.navigation.InsightsKey
import com.roni.blocks.navigation.WeeklyKey

@Immutable
data class WeeklyState(val label: String = "This week")

@Composable
fun WeeklyRoute(onTab: (NavKey) -> Unit) {
    Scaffold(bottomBar = { Tabs(WeeklyKey, onTab) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(WeeklyState().label)
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                drawRect(Color(0xFF7A9CC6).copy(alpha = 0.35f), Offset(24f, 40f), Size(40f, 80f))
                drawRect(Color(0xFF7A9CC6), Offset(72f, 20f), Size(40f, 100f))
            }
        }
    }
}

@Composable
internal fun Tabs(selected: NavKey, onTab: (NavKey) -> Unit) {
    NavigationBar {
        NavigationBarItem(selected = selected == HomeKey, onClick = { onTab(HomeKey) }, icon = { Text("H") }, label = { Text("Home") })
        NavigationBarItem(selected = selected == WeeklyKey, onClick = { onTab(WeeklyKey) }, icon = { Text("W") }, label = { Text("Weekly") })
        NavigationBarItem(selected = selected == InsightsKey, onClick = { onTab(InsightsKey) }, icon = { Text("I") }, label = { Text("Insights") })
    }
}
