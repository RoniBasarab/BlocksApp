package com.roni.blocks.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.roni.blocks.core.demo.DemoDataGenerator
import com.roni.blocks.core.di.ApplicationScope
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.time.DebugTimeController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Debug-only clock control, driven by explicit broadcasts. The clock itself lives in
 * [DebugTimeController], which the Settings screen also uses; this is just the adb front door.
 *
 * ```
 * adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --es to 09:31
 * adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --ei plus 30
 * adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --ez reset true
 * ```
 */
@AndroidEntryPoint
class TimeTravelReceiver : BroadcastReceiver() {
    @Inject lateinit var logger: AppLogger
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var demoData: DemoDataGenerator
    @Inject lateinit var debugTime: DebugTimeController

    override fun onReceive(context: Context, intent: Intent) {
        when {
            intent.getBooleanExtra(EXTRA_SEED_DEMO, false) -> runAsync { demoData.seed(weeks = 4) }
            intent.getBooleanExtra(EXTRA_CLEAR_DEMO, false) -> runAsync { demoData.clear() }
            intent.getBooleanExtra(EXTRA_RESET, false) -> runAsync { debugTime.useRealTime() }
            intent.hasExtra(EXTRA_TO) -> runAsync { debugTime.setTo(intent.getStringExtra(EXTRA_TO).orEmpty()) }
            intent.hasExtra(EXTRA_PLUS) -> runAsync { debugTime.shiftBy(intent.getIntExtra(EXTRA_PLUS, 0)) }
            else -> logger.info("TimeTravelReceiver: nothing to do")
        }
    }

    /** [goAsync] has to be claimed before returning, so the body is handed a scope to finish in. */
    private fun runAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        appScope.launch {
            try {
                withTimeout(9_000) { block() }
            } catch (t: Throwable) {
                logger.error("TimeTravelReceiver", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val EXTRA_TO = "to"
        const val EXTRA_PLUS = "plus"
        const val EXTRA_RESET = "reset"
        const val EXTRA_SEED_DEMO = "seed_demo"
        const val EXTRA_CLEAR_DEMO = "clear_demo"
    }
}
