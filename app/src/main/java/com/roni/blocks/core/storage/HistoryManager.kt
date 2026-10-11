package com.roni.blocks.core.storage

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.CheckIn
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.LocalDateSerializer
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.TemplateId
import com.roni.blocks.core.model.WalkLog
import com.roni.blocks.core.model.isWork
import com.roni.blocks.core.schedule.RecordEdit
import com.roni.blocks.core.schedule.RecordEditor
import com.roni.blocks.core.schedule.toRecordRows
import com.roni.blocks.core.schedule.withRows
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class WeekAggregate(
    @Serializable(with = LocalDateSerializer::class) val weekStart: LocalDate,
    val minutesByType: Map<String, Int> = emptyMap(),
    val leakMinutes: Int = 0,
    val underMinutes: Int = 0,
    val pauseMinutes: Int = 0,
    val interruptionMinutes: Int = 0,
    val flags: Set<DayFlag> = emptySet(),
)

@Singleton
/**
 * The only reader and writer of stored days and week aggregates. Rules: writing a day
 * prunes old ones, so pruning must stay inside the write path; weeks start on Sunday and every
 * weekly figure goes through [aggregateWeek], so the day-end flush and demo seeding cannot drift
 * apart; clearing history keeps today.
 *
 * Talks to: [PrefsStorage], [DayRecord].
 */
class HistoryManager @Inject constructor(
    private val prefs: PrefsStorage,
) {
    /** Bumped after every committed write so [observeDay] re-reads the day. */
    private val version = MutableStateFlow(0L)

    suspend fun writeDay(record: DayRecord) {
        prefs.write(PrefsFile.HISTORY, dayKey(record.date), record, DayRecord.serializer())
        prune(record.date)
        version.value++
    }

    suspend fun readDay(date: LocalDate): DayRecord? =
        prefs.read(PrefsFile.HISTORY, dayKey(date), DayRecord.serializer())

    /** Re-reads [date] after every write; emits the same record again only when it changed. */
    fun observeDay(date: LocalDate): Flow<DayRecord?> =
        version.map { readDay(date) }.distinctUntilChanged()

    /** Appends a finished walk to [date]'s own list, under its own key so day records stay untouched. */
    suspend fun addWalk(date: LocalDate, log: WalkLog) {
        prefs.write(PrefsFile.HISTORY, walkKey(date), walks(date) + log, ListSerializer(WalkLog.serializer()))
        version.value++
    }

    /** The standalone walks logged on [date], oldest first. */
    suspend fun walks(date: LocalDate): List<WalkLog> =
        prefs.read(PrefsFile.HISTORY, walkKey(date), ListSerializer(WalkLog.serializer())) ?: emptyList()

    /** Re-reads [date]'s walks after every write, like [observeDay]. */
    fun observeWalks(date: LocalDate): Flow<List<WalkLog>> =
        version.map { walks(date) }.distinctUntilChanged()

    /**
     * Corrects the stored record of a day that already happened (2.1.0) and rebuilds that day's week.
     * False when [date] has no record, or the edit isn't allowed or changes nothing.
     */
    suspend fun correctDay(date: LocalDate, edit: RecordEdit, zone: TimeZone): Boolean {
        val record = readDay(date) ?: return false
        val rows = RecordEditor.apply(record.toRecordRows(zone), edit) ?: return false
        writeDay(record.withRows(rows, zone))
        val week = weekStart(date)
        writeWeek(aggregateWeek(week, days().filter { weekStart(it.date) == week }))
        return true
    }

    suspend fun setCheckIn(date: LocalDate, checkIn: CheckIn) {
        val record = readDay(date) ?: return
        writeDay(record.copy(checkIn = checkIn))
    }

    suspend fun writeWeek(aggregate: WeekAggregate) {
        prefs.write(PrefsFile.HISTORY, weekKey(aggregate.weekStart), aggregate, WeekAggregate.serializer())
    }

    suspend fun readWeek(weekStart: LocalDate): WeekAggregate? =
        prefs.read(PrefsFile.HISTORY, weekKey(weekStart), WeekAggregate.serializer())

    suspend fun days(): List<DayRecord> = prefs.keys(PrefsFile.HISTORY)
        .filter { it.startsWith("record_") }
        .mapNotNull { prefs.read(PrefsFile.HISTORY, it, DayRecord.serializer()) }
        .sortedByDescending { it.date }

    suspend fun clear(keepToday: LocalDate? = null) {
        val kept = setOfNotNull(keepToday?.let { dayKey(it) }, keepToday?.let { weekKey(it) })
        prefs.keys(PrefsFile.HISTORY).filter { it !in kept }.forEach { prefs.remove(PrefsFile.HISTORY, it) }
    }

    fun weekStart(date: LocalDate): LocalDate {
        var cursor = date
        while (cursor.dayOfWeek != DayOfWeek.SUNDAY) cursor = cursor.minus(1, DateTimeUnit.DAY)
        return cursor
    }

    /** The one weekly builder: the day-end flush and demo seeding both go through this. */
    fun aggregateWeek(
        weekStart: LocalDate,
        days: List<DayRecord>,
    ): WeekAggregate {
        val minutesByType = mutableMapOf<String, Int>()
        var leakMinutes = 0
        var underMinutes = 0
        var pauseMinutes = 0
        var interruptionMinutes = 0
        val flags = mutableSetOf<DayFlag>()
        days.forEach { day ->
            flags += day.flags
            leakMinutes += debtMinutes(day.segments)
            day.segments.forEach { segment ->
                when (segment.kind) {
                    SegmentKind.PLANNED -> segment.actualEnd?.let { actualEnd ->
                        val worked = ((actualEnd - segment.actualStart).inWholeMinutes.coerceAtLeast(0L)).toInt()
                        minutesByType.merge(segment.type.toString(), worked, Int::plus)
                        val planned = segment.plannedStart?.let { plannedStart ->
                            segment.plannedEnd?.let { plannedEnd -> ((plannedEnd - plannedStart).inWholeMinutes).toInt() }
                        } ?: 0
                        if (segment.type.isWork) underMinutes += maxOf(0, planned - worked)
                    }
                    SegmentKind.PAUSE -> pauseMinutes += ((segment.actualEnd ?: segment.actualStart) - segment.actualStart).inWholeMinutes.toInt()
                    SegmentKind.INTERRUPTION -> interruptionMinutes += ((segment.actualEnd ?: segment.actualStart) - segment.actualStart).inWholeMinutes.toInt()
                    else -> Unit
                }
            }
        }
        return WeekAggregate(
            weekStart = weekStart,
            minutesByType = minutesByType,
            leakMinutes = leakMinutes,
            underMinutes = underMinutes,
            pauseMinutes = pauseMinutes,
            interruptionMinutes = interruptionMinutes,
            flags = flags,
        )
    }

    suspend fun clearDemoData(): Set<LocalDate> {
        val removedDates = mutableSetOf<LocalDate>()
        prefs.keys(PrefsFile.HISTORY).filter { it.startsWith("record_") }.forEach { key ->
            prefs.read(PrefsFile.HISTORY, key, DayRecord.serializer())?.takeIf { it.demo }?.let { record ->
                prefs.remove(PrefsFile.HISTORY, key)
                removedDates += record.date
            }
        }
        return removedDates
    }

    suspend fun removeWeek(weekStart: LocalDate) {
        prefs.remove(PrefsFile.HISTORY, weekKey(weekStart))
    }

    private suspend fun prune(today: LocalDate) {
        val cutoff = today.minus(1, DateTimeUnit.YEAR)
        prefs.keys(PrefsFile.HISTORY).forEach { key ->
            val date = key.substringAfterLast('_').let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@forEach
            if (date < cutoff) prefs.remove(PrefsFile.HISTORY, key)
        }
    }

    private fun dayKey(date: LocalDate) = "record_$date"
    private fun weekKey(date: LocalDate) = "agg_week_$date"
    private fun walkKey(date: LocalDate) = "walks_$date"
}

fun missedGym(template: TemplateId?, segments: List<Segment>): Boolean {
    if (template != TemplateId.MON_THU) return false
    return segments.none { it.type == BlockType.Gym && it.kind == SegmentKind.PLANNED && it.actualEnd != null }
}

/** Debt is time a block ran past its planned end. Pause time is logged, but it is not debt. */
fun debtMinutes(segments: List<Segment>): Int =
    segments.filter { it.kind == SegmentKind.PLANNED && it.actualEnd != null && it.plannedEnd != null }
        .sumOf { segment ->
            val over = (segment.actualEnd!! - segment.plannedEnd!!).inWholeMinutes
            over.coerceAtLeast(0)
        }
        .toInt()
