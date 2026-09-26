package com.roni.blocks.core.di

import com.roni.blocks.core.logging.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun exceptionHandler(logger: AppLogger): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, error -> logger.error("coroutine", error) }

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(handler: CoroutineExceptionHandler): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
}
