package com.roni.blocks

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.designsystem.BlocksTheme
import com.roni.blocks.navigation.AppRoot
import com.roni.blocks.navigation.resolveStartDestination
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @Inject lateinit var orchestrator: DayOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !orchestrator.isRestored.value }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            BlocksTheme {
                val restored by orchestrator.isRestored.collectAsStateWithLifecycle()
                if (restored) {
                    val start = remember { resolveStartDestination(orchestrator.state.value) }
                    AppRoot(
                        start = start,
                        modifier = Modifier.semantics { testTagsAsResourceId = true },
                    )
                }
            }
        }
    }
}
