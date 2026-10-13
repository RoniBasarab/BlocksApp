package com.roni.blocks

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.steps.DriveDetector
import com.roni.blocks.core.storage.TemplateStore
import com.roni.blocks.designsystem.BlocksTheme
import com.roni.blocks.navigation.AppRoot
import com.roni.blocks.navigation.StartDestination
import com.roni.blocks.navigation.resolveStartDestination
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // The steps only start once Android has answered; ask the orchestrator to re-read now.
            lifecycleScope.launch {
                orchestrator.onAppForeground()
                driveDetector.register()
            }
        }

    @Inject lateinit var orchestrator: DayOrchestrator
    @Inject lateinit var driveDetector: DriveDetector
    @Inject lateinit var logger: AppLogger
    @Inject lateinit var templates: TemplateStore

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val shownAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            !orchestrator.isRestored.value && SystemClock.uptimeMillis() - shownAt < SPLASH_CAP_MS
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val toRequest = buildList {
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACTIVITY_RECOGNITION)) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        if (toRequest.isNotEmpty()) permissions.launch(toRequest.toTypedArray())
        setContent {
            BlocksTheme {
                val state by orchestrator.state.collectAsStateWithLifecycle()
                var start by remember { mutableStateOf<StartDestination?>(null) }
                LaunchedEffect(Unit) {
                    if (withTimeoutOrNull(SPLASH_CAP_MS) { orchestrator.isRestored.first { it } } == null) {
                        logger.error("Restore took over 3 s; opening with the current state")
                    }
                    start = resolveStartDestination(orchestrator.state.value, templates.profileChosen())
                }
                LaunchedEffect(state) {
                    if (start != StartDestination.HOME &&
                        resolveStartDestination(state, templates.profileChosen()) == StartDestination.HOME
                    ) {
                        start = StartDestination.HOME
                    }
                }
                start?.let { destination ->
                    key(destination) {
                        AppRoot(
                            start = destination,
                            modifier = Modifier.semantics { testTagsAsResourceId = true },
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val SPLASH_CAP_MS = 3_000L
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}