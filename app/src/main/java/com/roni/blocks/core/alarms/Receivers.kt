package com.roni.blocks.core.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.roni.blocks.core.daystate.DayEvent
import com.roni.blocks.core.daystate.DayOrchestrator
import com.roni.blocks.core.notifications.AppNotifier
import com.roni.blocks.core.notifications.NoticeAction
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var orchestrator: DayOrchestrator

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        orchestrator.onAlarm()
        pending.finish()
    }
}

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var orchestrator: DayOrchestrator

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        when (intent.getStringExtra(AppNotifier.EXTRA_ACTION)) {
            NoticeAction.NEXT.name -> orchestrator.send(DayEvent.Next)
            NoticeAction.KID_ASLEEP.name -> orchestrator.send(DayEvent.KidAsleep)
            NoticeAction.END_DAY.name -> orchestrator.send(DayEvent.EndDay)
        }
        pending.finish()
    }
}

@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {
    @Inject lateinit var orchestrator: DayOrchestrator

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        orchestrator.onAlarm()
        pending.finish()
    }
}
