package com.roni.blocks.core.notifications

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
 * Lives in `src/main`, not `src/debug`: a notification button must work in a release build too.
 * The time-travel receiver is the debug-only one.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var orchestrator: DayOrchestrator
    @Inject lateinit var renderer: NotificationRenderer
    @Inject lateinit var logger: AppLogger
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISMISSED) {
            intent.getStringExtra(EXTRA_KEY)?.let(renderer::onDismissed)
            return
        }
        val event = intent.getStringExtra(EXTRA_EVENT)
            ?.let { name -> ActionEvent.entries.firstOrNull { it.name == name } }
        if (event == null) {
            logger.info("NotificationActionReceiver: unknown action ${intent.getStringExtra(EXTRA_EVENT)}")
            return
        }
        // The block the button was built for. A tap whose block is no longer current is stale.
        val forBlockId = intent.getStringExtra(EXTRA_FOR_BLOCK_ID)
        val pending = goAsync()
        appScope.launch {
            try {
                withTimeout(9_000) { orchestrator.handleNotificationAction(event, forBlockId) }
            } catch (t: Throwable) {
                logger.error("NotificationActionReceiver", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_EVENT = "com.roni.blocks.action.NOTIFICATION_EVENT"
        const val ACTION_DISMISSED = "com.roni.blocks.action.NOTIFICATION_DISMISSED"
        const val EXTRA_EVENT = "event"
        const val EXTRA_FOR_BLOCK_ID = "for_block_id"
        const val EXTRA_KEY = "key"
    }
}
