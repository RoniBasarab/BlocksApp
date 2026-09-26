package com.roni.blocks.core.storage

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.core.model.DayFlag
import com.roni.blocks.core.model.DayRecord
import com.roni.blocks.core.model.DebtRecord
import com.roni.blocks.core.model.LocalDateSerializer
import com.roni.blocks.core.model.Segment
import com.roni.blocks.core.model.SegmentKind
import com.roni.blocks.core.model.TemplateId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable

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
class HistoryManager @Inject constructor(
    private val prefs: PrefsStorage,
) {
    suspend fun writeDay(record: DayRecord) {
        prefs.write(PrefsFile.HISTORY, dayKey(record.date), record, DayRecord.serializer())
        prune(record.date)
    }

    suspend fun readDay(date: LocalDate): DayRecord? =
        prefs.read(PrefsFile.HISTORY, dayKey(date), DayRecord.serializer())

    suspend fun writeDebt(record: DebtRecord) {
        prefs.write(PrefsFile.HISTORY, debtKey(record.date), record, DebtRecord.serializer())
    }

    suspend fun readDebt(date: LocalDate): DebtRecord? =
        prefs.read(PrefsFile.HISTORY, debtKey(date), DebtRecord.serializer())

    suspend fun writeWeek(aggregate: WeekAggregate) {
        prefs.write(PrefsFile.HISTORY, weekKey(aggregate.weekStart), aggregate, WeekAggregate.serializer())
    }

    suspend fun readWeek(weekStart: LocalDate): WeekAggregate? =
        prefs.read(PrefsFile.HISTORY, weekKey(weekStart), WeekAggregate.serializer())

    suspend fun days(): List<DayRecord> = prefs.keys(PrefsFile.HISTORY)
        .filter { it.startsWith("record_") }
        .mapNotNull { prefs.read(PrefsFile.HISTORY, it, DayRecord.serializer()) }
        .sortedByDescending { it.date }

    suspend fun clear() {
        prefs.keys(PrefsFile.HISTORY).forEach { prefs.remove(PrefsFile.HISTORY, it) }
    }

    fun debtMinutes(segments: List<Segment>): Int {
        val leak = segments.filter { it.kind == SegmentKind.PLANNED && it.actualEnd != null && it.plannedEnd != null }
            .sumOf { segment ->
                val over = (segment.actualEnd!! - segment.plannedEnd!!).inWholeMinutes
                over.coerceAtLeast(0)
            }
        val pause = segments.filter { it.kind == SegmentKind.PAUSE && it.actualEnd != null }
            .sumOf { (it.actualEnd!! - it.actualStart).inWholeMinutes }
        return (leak + pause).toInt()
    }

    fun weekStart(date: LocalDate): LocalDate {
        var cursor = date
        while (cursor.dayOfWeek != DayOfWeek.SUNDAY) cursor = cursor.minus(1, DateTimeUnit.DAY)
        return cursor
    }

    private suspend fun prune(today: LocalDate) {
        val cutoff = today.minus(1, DateTimeUnit.YEAR)
        prefs.keys(PrefsFile.HISTORY).forEach { key ->
            val date = key.substringAfterLast('_').let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@forEach
            if (date < cutoff) prefs.remove(PrefsFile.HISTORY, key)
        }
    }

    private fun dayKey(date: LocalDate) = "record_$date"
    private fun debtKey(date: LocalDate) = "debt_$date"
    private fun weekKey(date: LocalDate) = "agg_week_$date"
}

fun missedGym(template: TemplateId?, segments: List<Segment>): Boolean {
    if (template != TemplateId.MON_THU) return false
    return segments.none { it.type == BlockType.Gym && it.kind == SegmentKind.PLANNED && it.actualEnd != null }
}
