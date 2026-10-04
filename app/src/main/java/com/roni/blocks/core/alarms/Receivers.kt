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
