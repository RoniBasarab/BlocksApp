package com.roni.blocks.core.system

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Permission status as the system reports it; faked in SettingsViewModelTest. */
interface SystemStatuses {
    fun notificationsEnabled(): Boolean
    fun canScheduleExactAlarms(): Boolean

    /** True when the phone has a hardware step counter to read (2.5.0). */
    fun stepCounterPresent(): Boolean

    /** True when the "Physical activity" permission is granted (2.5.0). */
    fun activityRecognitionGranted(): Boolean
}

@Singleton
class AndroidSystemStatuses @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemStatuses {
    override fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun canScheduleExactAlarms(): Boolean =
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    override fun stepCounterPresent(): Boolean =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    override fun activityRecognitionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SystemStatusesModule {
    @Binds
    abstract fun bindSystemStatuses(impl: AndroidSystemStatuses): SystemStatuses
}
