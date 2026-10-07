package com.roni.blocks.core.di

import com.roni.blocks.core.logging.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun exceptionHandler(logger: AppLogger): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, error -> logger.error("coroutine", error) }
}
