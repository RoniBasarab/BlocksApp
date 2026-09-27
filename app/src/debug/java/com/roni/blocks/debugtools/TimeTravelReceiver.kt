package com.roni.blocks.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.demo.DemoDataGenerator
import com.roni.blocks.core.di.ApplicationScope
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.time.DebugClock
import com.roni.blocks.core.time.at
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Debug-only clock control, driven by explicit broadcasts:
 *
 * ```
 * adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --es to 09:31
 * adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --ei plus 30
 * adb shell am broadcast -n com.roni.blocks.debug/com.roni.blocks.debugtools.TimeTravelReceiver --ez reset true
 * ```
 *
 * Moving the clock re-evaluates and reschedules; it is not an interruption, because nothing was
 * actually missed.
 */
@AndroidEntryPoint
class TimeTravelReceiver : BroadcastReceiver() {
    @Inject lateinit var clock: DebugClock
    @Inject lateinit var orchestrator: DayOrchestrator
    @Inject lateinit var logger: AppLogger
    @Inject lateinit var zone: TimeZone
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var demoData: DemoDataGenerator

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_SEED_DEMO, false)) {
            val pending = goAsync()
            appScope.launch {
                try {
                    withTimeout(9_000) { demoData.seed(weeks = 4) }
                } catch (t: Throwable) {
                    logger.error("TimeTravelReceiver", t)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.getBooleanExtra(EXTRA_CLEAR_DEMO, false)) {
            val pending = goAsync()
            appScope.launch {
                try {
                    withTimeout(9_000) { demoData.clear() }
                } catch (t: Throwable) {
                    logger.error("TimeTravelReceiver", t)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        val target = when {
            intent.getBooleanExtra(EXTRA_RESET, false) -> 0
            intent.hasExtra(EXTRA_TO) -> offsetForTimeOfDay(intent.getStringExtra(EXTRA_TO)) ?: return
            intent.hasExtra(EXTRA_PLUS) -> clock.offsetMinutes + intent.getIntExtra(EXTRA_PLUS, 0)
            else -> {
                logger.info("TimeTravelReceiver: nothing to do")
                return
            }
        }
        clock.setOffsetMinutes(target)
        logger.info("TimeTravelReceiver: offset ${clock.offsetMinutes} min")

        val pending = goAsync()
        appScope.launch {
            try {
                withTimeout(9_000) { orchestrator.onTimeChanged() }
            } catch (t: Throwable) {
                logger.error("TimeTravelReceiver", t)
            } finally {
                pending.finish()
            }
        }
    }

    /** Minutes to add to real time so the app clock reads [hhmm] on the app's current day. */
    private fun offsetForTimeOfDay(hhmm: String?): Int? {
        val time = hhmm?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        if (time == null) {
            logger.info("TimeTravelReceiver: bad --es to $hhmm")
            return null
        }
        val today = clock.now().toLocalDateTime(zone).date
        return (time.at(today, zone) - Clock.System.now()).inWholeMinutes.toInt()
    }

    private companion object {
        const val EXTRA_TO = "to"
        const val EXTRA_PLUS = "plus"
        const val EXTRA_RESET = "reset"
        const val EXTRA_SEED_DEMO = "seed_demo"
        const val EXTRA_CLEAR_DEMO = "clear_demo"
    }
}
