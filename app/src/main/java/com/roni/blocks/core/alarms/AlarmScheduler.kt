package com.roni.blocks.core.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.time.SystemTimeMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemTime: SystemTimeMapper,
    private val logger: AppLogger,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun schedule(at: Instant) {
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, systemTime.systemMillis(at), pending())
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
