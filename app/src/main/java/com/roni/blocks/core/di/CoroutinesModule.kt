package com.roni.blocks.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Both dispatchers the app uses are bindings, not literals. Production code asks for
 * [IoDispatcher] or [ApplicationScope] through its constructor, and a test hands in the dispatcher
 * and scope it owns instead - which is what stops a coroutine from outliving the test that started
 * it. This is the pattern every class in `src/main` that touches a dispatcher follows.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(
        @IoDispatcher io: CoroutineDispatcher,
        handler: CoroutineExceptionHandler,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + io + handler)
}
