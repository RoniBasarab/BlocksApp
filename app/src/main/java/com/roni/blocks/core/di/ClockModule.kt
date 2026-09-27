package com.roni.blocks.core.di

import com.roni.blocks.BuildConfig
import com.roni.blocks.core.time.DebugClock
import com.roni.blocks.core.time.SystemTimeMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * The app reads time through [Clock]; anything that hands a time to Android goes through
 * [SystemTimeMapper]. Debug runs on a jumpable clock, release on the system clock. One module for
 * every variant, because `matchingFallbacks` does not reliably carry `src/release` into the
 * benchmark variant.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    @Singleton
    fun clock(debugClock: DebugClock): Clock = if (BuildConfig.DEBUG) debugClock else Clock.System

    @Provides
    @Singleton
    fun systemTimeMapper(debugClock: DebugClock): SystemTimeMapper =
        if (BuildConfig.DEBUG) debugClock.asMapper() else SystemTimeMapper.Identity
}
