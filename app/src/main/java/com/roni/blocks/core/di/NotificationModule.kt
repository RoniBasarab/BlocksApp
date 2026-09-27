package com.roni.blocks.core.di

import android.content.Context
import com.roni.blocks.core.notifications.AppVisibility
import com.roni.blocks.core.notifications.DismissalStore
import com.roni.blocks.core.notifications.NotificationPort
import com.roni.blocks.core.notifications.NotificationRenderer
import com.roni.blocks.core.notifications.PrefsDismissalStore
import com.roni.blocks.core.notifications.ProcessAppVisibility
import com.roni.blocks.core.time.SystemTimeMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    @Provides
    @Singleton
    fun appVisibility(): AppVisibility = ProcessAppVisibility()

    @Provides
    @Singleton
    fun notificationPort(renderer: NotificationRenderer): NotificationPort = renderer

    @Provides
    @Singleton
    fun dismissalStore(@ApplicationContext context: Context): DismissalStore = PrefsDismissalStore(context)

    // Release maps identity. The debug build overrides this so a simulated clock still produces
    // real wall-clock `setWhen` values.
    @Provides
    @Singleton
    fun systemTimeMapper(): SystemTimeMapper = SystemTimeMapper.Identity
}
