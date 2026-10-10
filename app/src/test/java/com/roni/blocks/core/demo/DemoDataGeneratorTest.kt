package com.roni.blocks.core.demo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.storage.HistoryManager
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.time.at
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Demo days must be indistinguishable from real ones in shape: 28 days before today, exactly one
 * Off day and one Emergency, weekly aggregates for every touched week, and identical output for
 * an identical seed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DemoDataGeneratorTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jerusalem")

    /** Sunday 2026-09-27. */
    private val today = LocalDate(2026, 9, 27)

    private lateinit var history: HistoryManager
    private lateinit var generator: DemoDataGenerator

    @Before
    fun setUp() = runBlocking {
        val prefs = PrefsStorage(context, UnconfinedTestDispatcher())
        history = HistoryManager(prefs)
        history.clear()
        generator = DemoDataGenerator(
            templates = com.roni.blocks.core.storage.TemplateManager(context),
            history = history,
            clock = object : Clock {
                override fun now(): Instant = LocalTime(10, 0).at(today, zone)
            },
            zone = zone,
        )
    }

    @Test
    fun seed_writes28DemoDaysNoneToday() = runBlocking {
        generator.seed()

        val days = history.days()
        assertEquals(28, days.size)
        assertTrue(days.all { it.date < today })
        assertTrue(days.all { it.demo })
    }

    @Test
    fun seed_exactlyOneOffDayAndOneEmergency() = runBlocking {
        generator.seed()

        val days = history.days()
        assertEquals(1, days.count { DayFlag.OFF_DAY in it.flags })
        assertEquals(1, days.count { DayFlag.EMERGENCY_STOP in it.flags })
    }

    @Test
    fun seed_weeklyAggregatesCoverEverySeededWeek() = runBlocking {
        generator.seed()

        val weeks = history.days().map { history.weekStart(it.date) }.distinct()
        assertTrue(weeks.size >= 4)
        weeks.forEach { week -> assertNotNull(history.readWeek(week)) }
    }

    @Test
    fun sameSeed_producesIdenticalOutput() = runBlocking {
        generator.seed(weeks = 2)
        val first = snapshot()

        generator.clear()
        history.clear()
        generator.seed(weeks = 2)

        assertEquals(first, snapshot())
    }

    @Test
    fun clear_keepsNonDemoData() = runBlocking {
        generator.seed()
        val guest = today.minus(10, kotlinx.datetime.DateTimeUnit.DAY)
        history.writeDay(com.roni.blocks.core.model.DayRecord(guest, template = null, segments = emptyList()))
        assertNotNull(history.readDay(guest))

        generator.clear()

        assertNotNull(history.readDay(guest))
        assertTrue(history.days().none { it.demo })
    }

    private suspend fun snapshot(): String {
        val days = history.days()
        val records = days.joinToString { formatRecord(it) }
        val weekStarts = days.map { history.weekStart(it.date) }.distinct()
        val weeks = buildList {
            for (week in weekStarts) {
                val aggregate = history.readWeek(week)
                add(aggregate?.let { "${it.leakMinutes}:${it.pauseMinutes}:${it.interruptionMinutes}" } ?: "none")
            }
        }.joinToString()
        return "$records|$weeks"
    }

    private fun formatRecord(record: com.roni.blocks.core.model.DayRecord): String =
        "${record.date}|${record.template}|${record.segments}|${record.flags}|${record.demo}"
}
