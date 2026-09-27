package com.roni.blocks.core.system

import android.app.AlarmManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
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
}

@Singleton
class AndroidSystemStatuses @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemStatuses {
    override fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun canScheduleExactAlarms(): Boolean =
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SystemStatusesModule {
    @Binds
    abstract fun bindSystemStatuses(impl: AndroidSystemStatuses): SystemStatuses
}
