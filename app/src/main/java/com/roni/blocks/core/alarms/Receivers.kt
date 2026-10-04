package com.roni.blocks.core.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.di.ApplicationScope
import com.roni.blocks.core.logging.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * The alarm that ends the current block. Rules: claim `goAsync` before returning and bound the work
 * with a timeout, or Android kills the process mid-transition; call [DayOrchestrator.onAlarm], not
 * `onTimeChanged`, so a late alarm still applies the transition it was scheduled for.
 *
 * Talks to: [DayOrchestrator], [AppLogger], the application scope.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var orchestrator: DayOrchestrator
    @Inject lateinit var logger: AppLogger
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        appScope.launch {
            try {
                withTimeout(9_000) {
                    orchestrator.ensureRestored()
                    orchestrator.onAlarm()
                }
            } catch (t: Throwable) {
                logger.error("AlarmReceiver", t)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Re-evaluates the day after the system changes something underneath us: boot, app replaced, clock
 * or time zone moved. Rules: same shape as [AlarmReceiver] — claim `goAsync`, bound the work with a
 * timeout — and it must never write state itself, only ask [DayOrchestrator] to re-evaluate.
 *
 * Talks to: [DayOrchestrator], [AppLogger], the application scope.
 */
@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {
    @Inject lateinit var orchestrator: DayOrchestrator
    @Inject lateinit var logger: AppLogger
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        appScope.launch {
            try {
                withTimeout(9_000) {
                    orchestrator.ensureRestored()
                    orchestrator.onAlarm()
                }
            } catch (t: Throwable) {
                logger.error("SystemEventsReceiver", t)
            } finally {
                pending.finish()
            }
        }
    }
}
