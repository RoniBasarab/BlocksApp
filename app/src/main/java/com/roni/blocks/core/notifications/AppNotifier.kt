package com.roni.blocks.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.roni.blocks.MainActivity
import com.roni.blocks.core.alarms.NotificationActionReceiver
import com.roni.blocks.core.daystate.DayState
import com.roni.blocks.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class NoticeChannel { STATUS, DAY_ALERTS, EVENING }

fun postsAllowed(state: DayState, channel: NoticeChannel): Boolean = when (state) {
    DayState.Ended, DayState.Off, DayState.NotStarted -> false
    DayState.KidTime, is DayState.Evening -> channel != NoticeChannel.DAY_ALERTS
    else -> true
}

@Singleton
class AppNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        manager.createNotificationChannel(channel(STATUS, "Status", NotificationManager.IMPORTANCE_LOW, sound = false))
        manager.createNotificationChannel(channel(DAY_ALERTS, "Day alerts", NotificationManager.IMPORTANCE_DEFAULT, sound = true))
        manager.createNotificationChannel(channel(EVENING, "Evening", NotificationManager.IMPORTANCE_LOW, sound = false))
    }

    fun postStatus(state: DayState, text: String) {
        if (!postsAllowed(state, NoticeChannel.STATUS)) {
            manager.cancel(STATUS_ID)
            return
        }
        ensureChannels()
        manager.notify(STATUS_ID, base(STATUS, text).setOnlyAlertOnce(true).setOngoing(true).build())
        logger.info("status $text")
    }

    fun postAlert(state: DayState, text: String, actions: List<NoticeAction> = emptyList()) {
        val channel = if (state is DayState.KidTime || state is DayState.Evening) NoticeChannel.EVENING else NoticeChannel.DAY_ALERTS
        if (!postsAllowed(state, channel)) return
        ensureChannels()
        val id = if (channel == NoticeChannel.EVENING) EVENING else DAY_ALERTS
        val builder = base(id, text).setAutoCancel(true)
        actions.take(3).forEach { action ->
            builder.addAction(0, action.name, actionIntent(action))
        }
        manager.notify(ALERT_ID, builder.build())
        logger.info("alert $text")
    }

    fun cancelAll() {
        manager.cancel(STATUS_ID)
        manager.cancel(ALERT_ID)
    }

    private fun base(channelId: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Blocks")
            .setContentText(text)
            .setContentIntent(openApp())
            .setSilent(channelId != DAY_ALERTS)

    private fun channel(id: String, name: String, importance: Int, sound: Boolean): NotificationChannel =
        NotificationChannel(id, name, importance).apply {
            enableVibration(false)
            vibrationPattern = null
            setSound(if (sound) android.provider.Settings.System.DEFAULT_NOTIFICATION_URI else null, null)
        }

    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(action: NoticeAction): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).putExtra(EXTRA_ACTION, action.name)
        return PendingIntent.getBroadcast(context, action.ordinal + 10, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val STATUS = "status"
        const val DAY_ALERTS = "day_alerts"
        const val EVENING = "evening"
        const val STATUS_ID = 1
        const val ALERT_ID = 2
        const val EXTRA_ACTION = "action"
    }
}

enum class NoticeAction { NEXT, KID_ASLEEP, END_DAY }
