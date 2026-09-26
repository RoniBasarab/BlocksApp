package com.roni.blocks.feature.timedebt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class DebtState(val week: String = "0 min", val today: String = "0 min")

@Composable
fun TimeDebtRoute() {
    val state = DebtState()
    Column(Modifier.padding(16.dp)) {
        Text("This week ${state.week}")
        Text("Today ${state.today}")
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            drawRect(Color(0xFFD97070), Offset(8f, 20f), Size(12f, 40f))
        }
    }
}
