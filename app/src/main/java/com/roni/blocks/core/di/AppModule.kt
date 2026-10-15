package com.roni.blocks.core.di

import com.roni.blocks.BuildConfig
import com.roni.blocks.core.system.FailurePolicy
import com.roni.blocks.core.system.Failures
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
    fun failurePolicy(): FailurePolicy = FailurePolicy(loud = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun exceptionHandler(failures: Failures): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, error -> failures.report("app scope", error) }
}
