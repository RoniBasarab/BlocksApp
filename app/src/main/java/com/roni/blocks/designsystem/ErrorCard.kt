package com.roni.blocks.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.roni.blocks.core.system.ProblemAction

/**
 * In place of a part that failed to load or to save (3.5.0). The rest of the screen keeps working.
 * Same card everywhere; the screen decides where its part is.
 */
@Composable
fun ErrorCard(onAction: (ProblemAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    AppCard(
        modifier = modifier.fillMaxWidth().testTag("error_card"),
        borderColor = colors.leak,
        containerColor = colors.surfaceRaised,
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        Spacer(Modifier.height(Spacing.s))
        Text(
            "This part couldn't load. Restarting the app usually fixes it. If it keeps happening, send the log.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(Spacing.m))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onAction(ProblemAction.SendLog) }, modifier = Modifier.testTag("error_send_log")) {
                Text("Send log")
            }
            Button(
                onClick = { onAction(ProblemAction.Restart) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                modifier = Modifier.testTag("error_restart"),
            ) {
                Text("Restart app")
            }
        }
    }
}
