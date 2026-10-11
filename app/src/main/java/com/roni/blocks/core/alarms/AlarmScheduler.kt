package com.roni.blocks.core.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.notifications.openAppIntent
import com.roni.blocks.core.time.SystemTimeMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

@Singleton
/**
 * Owns the single exact alarm that wakes the app when the current block ends. Rules: it must be
 * exact and allow-while-idle, or Doze swallows the block transition; the [PendingIntent] is reused
 * so scheduling replaces the previous alarm instead of stacking a second one; app instants are
 * converted to system time through [SystemTimeMapper], never by hand.
 *
 * Talks to: [AlarmManager], [SystemTimeMapper], [AlarmReceiver].
 */
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemTime: SystemTimeMapper,
    private val logger: AppLogger,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun schedule(at: Instant, clock: Boolean = false) {
        val millis = systemTime.systemMillis(at)
        if (clock) {
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(millis, openAppIntent(context)), pending())
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending())
        }
        logger.info("alarm scheduled $at")
    }

    fun cancel() {
        alarms.cancel(pending())
        logger.info("alarm cancelled")
    }

    private fun pending(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST = 42
    }
}
