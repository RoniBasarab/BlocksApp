package com.roni.blocks.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.roni.blocks.R
import com.roni.blocks.core.schedule.AlertKind
import com.roni.blocks.core.time.TimeFormatter
import com.roni.blocks.core.time.minuteOfDay
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * The walk timer (2.3.0): it rings like an alarm clock at half time ("Turn back now") and when Roni
 * should be home. A small interface with a no-op default in the orchestrator, like [WidgetPort], so
 * hand-built tests can record the rings without an Android context.
 *
 * Talks to: [NotificationManagerCompat], [TimeFormatter].
 */
interface WalkRinger {
    /** [homeAt] is when the walk ends; [nextLabel]/[nextEnd] describe what runs after the walk (for WALK_DONE). */
    fun ring(kind: AlertKind, homeAt: Instant, nextLabel: String?, nextEnd: Instant?)

    /** Cancels the walk notification (id 3): the ring stops. */
    fun stop()
}

@Singleton
class AndroidWalkRinger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeFormatter: TimeFormatter,
    private val zone: TimeZone,
) : WalkRinger {
    private val manager = NotificationManagerCompat.from(context)

    override fun ring(kind: AlertKind, homeAt: Instant, nextLabel: String?, nextEnd: Instant?) {
        ensureChannel()
        val title: String
        val text: String
        when (kind) {
            AlertKind.TURN_BACK -> {
                title = "Turn back now"
                text = "Home by ${hhmm(homeAt)}"
            }
            AlertKind.WALK_DONE -> {
                title = "Walk done"
                text = if (nextLabel != null && nextEnd != null) {
                    "Welcome back. $nextLabel until ${hhmm(nextEnd)}"
                } else {
                    "Welcome back."
                }
            }
            else -> return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_blocks)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setColor(WALK_ARG)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .addAction(0, "Stop", stopIntent())
            .build()
        // INSISTENT repeats the alarm sound until Roni taps Stop or dismisses it.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        manager.notify(ID, notification)
    }

    override fun stop() {
        manager.cancel(ID)
    }

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.channel_walk),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // Walks are the one exception to the no-vibration rule, and the sound plays at alarm volume.
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800)
        }
        manager.createNotificationChannel(channel)
    }

    private fun stopIntent(): PendingIntent {
        val intent = Intent(context, WalkAlarmReceiver::class.java).setAction(WalkAlarmReceiver.ACTION_STOP)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_STOP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun hhmm(instant: Instant): String = timeFormatter.label(instant.minuteOfDay(zone))

    companion object {
        const val ID = 3
        const val CHANNEL = "walk_alarm"
        private const val REQUEST_STOP = 73
        private const val WALK_ARG = 0xFF8FC7C9.toInt()
    }
}

/** Cancels the walk alarm (notification id 3) when Roni taps Stop on it. */
class WalkAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) NotificationManagerCompat.from(context).cancel(AndroidWalkRinger.ID)
    }

    companion object {
        const val ACTION_STOP = "com.roni.blocks.action.WALK_STOP"
    }
}
