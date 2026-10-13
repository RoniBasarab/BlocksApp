package com.roni.blocks.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.roni.blocks.R
import com.roni.blocks.core.time.SystemTimeMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the app needs from "the thing that shows notifications". Exists so the orchestrator
 * can be driven by a recording fake in tests without an Android context.
 */
interface NotificationPort {
    fun ensureChannels()

    /** Null [model] removes the notification. [transition] means the day genuinely moved on; [alert] means an alert is due (always sound). */
    fun render(model: NotificationModel?, transition: Boolean, alert: Boolean = false)

    fun onDismissed(key: String)

    /** Settings row: the one allowed second notification, ID 99, self-expiring after 10s. */
    fun sendTestNotification()
}

/**
 * Shows exactly one notification at a time. The old design used two (a silent status and a
 * separate alert), which meant two sounds, two dismissals and two ways to be wrong.
 */
@Singleton
class NotificationRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val visibility: AppVisibility,
    private val dismissals: DismissalStore,
    private val systemTime: SystemTimeMapper = SystemTimeMapper.Identity,
) : NotificationPort {

    private val manager = context.getSystemService(NotificationManager::class.java)
    private var lastChannel: String? = null

    override fun ensureChannels() {
        // The two-channel design is gone; drop the old channels so they stop lingering in settings.
        manager.deleteNotificationChannel(LEGACY_STATUS)
        manager.deleteNotificationChannel(LEGACY_ALERTS)
        manager.createNotificationChannel(
            channel(DAY, R.string.channel_day, NotificationManager.IMPORTANCE_DEFAULT, sound = true),
        )
        manager.createNotificationChannel(
            channel(EVENING, R.string.channel_evening, NotificationManager.IMPORTANCE_LOW, sound = false),
        )
        manager.createNotificationChannel(dishesChannel())
    }

    override fun render(model: NotificationModel?, transition: Boolean, alert: Boolean) {
        if (model == null) {
            if (lastChannel != null) manager.cancel(ID)
            lastChannel = null
            return
        }
        ensureChannels()
        // Dishes reminders vibrate only, and only while an alert is due (3.3.0); every other render
        // (and every transition) stays on the day/evening channel it always used.
        val channelId = when {
            alert && model.isDishesSlot() -> DISHES
            model.channel == NotificationChannelKind.EVENING -> EVENING
            else -> DAY
        }
        // Changing channel on a live notification is ignored by the platform, so replace it.
        if (lastChannel != null && lastChannel != channelId) manager.cancel(ID)
        if (transition || alert) {
            dismissals.forget(model.key)
        } else if (dismissals.isDismissed(model.key)) {
            return
        }
        manager.notify(ID, build(model, transition, alert, channelId))
        lastChannel = channelId
    }

    override fun onDismissed(key: String) {
        dismissals.remember(key)
        if (lastChannel != null) manager.cancel(ID)
        lastChannel = null
    }

    override fun sendTestNotification() {
        ensureChannels()
        manager.cancel(TEST_ID)
        manager.notify(
            TEST_ID,
            NotificationCompat.Builder(context, DAY)
                .setSmallIcon(R.drawable.ic_stat_blocks)
                .setContentTitle("Test notification")
                .setContentText("If you heard a sound, alerts work.")
                .setTimeoutAfter(10_000)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun build(model: NotificationModel, transition: Boolean, alert: Boolean, channelId: String): Notification {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_blocks)
            .setContentTitle(model.title)
            .setContentText(model.text)
            .setContentIntent(openApp())
            .setColor(model.accentArgb)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDeleteIntent(dismissedIntent(model.key))
            // An alert always makes a sound, even with the app open; a real transition makes a
  // sound only when the app isn't on screen; a refresh stays quiet (2.2.0).
            .setOnlyAlertOnce(!(alert || (transition && !visibility.isForeground)))
        val countdownTo = model.countdownTo
        if (countdownTo != null) {
            builder.setWhen(systemTime.systemMillis(countdownTo))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        model.actions.forEachIndexed { index, action ->
            builder.addAction(0, action.label, actionIntent(action, index))
        }
        return builder.build()
    }

    private fun channel(id: String, nameRes: Int, importance: Int, sound: Boolean): NotificationChannel =
        NotificationChannel(id, context.getString(nameRes), importance).apply {
            // Roni's rule: no vibration anywhere, sound only during the daytime segment.
            enableVibration(false)
            vibrationPattern = null
            setSound(if (sound) android.provider.Settings.System.DEFAULT_NOTIFICATION_URI else null, null)
        }

    /** Dishes reminders (3.3.0): no sound, a double vibration; the only other vibration besides walks. */
    private fun dishesChannel(): NotificationChannel =
        NotificationChannel(DISHES, "Dishes reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
        }

    private fun openApp(): PendingIntent = openAppIntent(context)

    private fun actionIntent(action: NotificationActionModel, index: Int): PendingIntent {        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_EVENT)
            .putExtra(NotificationActionReceiver.EXTRA_EVENT, action.event.name)
            .putExtra(NotificationActionReceiver.EXTRA_FOR_BLOCK_ID, action.forBlockId)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_ACTION + action.event.ordinal * 2 + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissedIntent(key: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_DISMISSED)
            .putExtra(NotificationActionReceiver.EXTRA_KEY, key)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_DISMISSED,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ID = 1
        const val TEST_ID = 99
        const val DAY = "day"
        const val EVENING = "evening"
        const val DISHES = "dishes"
        private const val LEGACY_STATUS = "status"
        private const val LEGACY_ALERTS = "day_alerts"
        private const val REQUEST_ACTION = 20
        private const val REQUEST_DISMISSED = 60
    }
}

/** The Dishes slot's model (3.3.0): its alerts vibrate on the DISHES channel. */
private fun NotificationModel.isDishesSlot(): Boolean = key == "dishes" || key == "dishes:done"
