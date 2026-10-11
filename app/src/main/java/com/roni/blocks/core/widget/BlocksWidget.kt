package com.roni.blocks.core.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
 * The home-screen widget. Rules: same shape as the alarm receivers — claim `goAsync`, bound the work
 * with a timeout — and never paint anything itself; restore the day, then let the orchestrator's
 * [DayOrchestrator.refreshWidget] draw the current model. If the orchestrator can't start, the
 * update just results in an empty widget.
 *
 * Talks to: [DayOrchestrator], [AppLogger], the application scope.
 */
@AndroidEntryPoint
class BlocksWidget : AppWidgetProvider() {
    @Inject lateinit var orchestrator: DayOrchestrator
    @Inject lateinit var logger: AppLogger
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        appScope.launch {
            try {
                withTimeout(9_000) {
                    orchestrator.ensureRestored()
                    orchestrator.refreshWidget()
                }
            } catch (t: Throwable) {
                logger.error("BlocksWidget", t)
            } finally {
                pending.finish()
            }
        }
    }
}