package com.roni.blocks.core.time

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    @Singleton
    fun clock(): Clock = Clock.System

    @Provides
    @Singleton
    fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
}

@Singleton
class Ticker @javax.inject.Inject constructor(
    private val clock: Clock,
    private val zone: TimeZone,
) {
    val minutes: Flow<Int> = flow {
        while (currentCoroutineContext().isActive) {
            val now = clock.now()
            emit(now.minuteOfDay(zone))
            delay(millisUntilNextMinute(now))
        }
    }

    fun seconds(): Flow<Int> = flow {
        while (currentCoroutineContext().isActive) {
            val now = clock.now()
            emit(now.toLocalDateTime(zone).second)
            val next = now.plus(1.seconds)
            delay((next - now).inWholeMilliseconds)
        }
    }
}

fun Instant.minuteOfDay(zone: TimeZone): Int {
    val local = toLocalDateTime(zone)
    return local.hour * 60 + local.minute
}

fun millisUntilNextMinute(now: Instant): Long {
    val seconds = now.epochSeconds
    val next = (seconds / 60 + 1) * 60
    return (next - seconds).coerceAtLeast(1) * 1000
}

fun localInstant(date: LocalDate, hour: Int, minute: Int, zone: TimeZone): Instant =
    LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

fun LocalTime.at(date: LocalDate, zone: TimeZone): Instant =
    LocalDateTime(date, this).toInstant(zone)
