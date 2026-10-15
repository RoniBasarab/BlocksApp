package com.roni.blocks.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A template's key (3.0.0). Saved as plain text, exactly like the 2.x enum, so old data decodes unchanged. */
@Serializable
@JvmInline
value class TemplateId(val key: String) {
    companion object {
        val MON_THU = TemplateId("MON_THU")
        val SUN_TUE_WED = TemplateId("SUN_TUE_WED")
        val FRI_SAT = TemplateId("FRI_SAT")
        val OFF = TemplateId("OFF")
        /** The four templates 2.x shipped with, in picker order. */
        val builtIns = listOf(MON_THU, SUN_TUE_WED, FRI_SAT, OFF)
    }
}

@Serializable
enum class Choice {
    GAME,
    PROJECT,
    READ,
    CHILL,
}

@Serializable
enum class SegmentKind {
    PLANNED,
    SYNTHETIC_FREE,
    PAUSE,
    INTERRUPTION,
    CONSUMED,
}

@Serializable
enum class DayFlag {
    OFF_DAY,
    EVENING_SKIPPED,
    GYM_MISSED,
    AUTO_ENDED,
    EMERGENCY_STOP,
    TEMPLATE_SWAPPED,
    PLAN_EDITED,
    CORRECTED,
}

@Serializable
enum class CheckIn {
    GOOD,
    OK,
    ROUGH,
    SKIPPED,
}

@Serializable
enum class MissReason {
    OVERRUN,
    LATE_START,
}

@Serializable
sealed interface BlockType {
    @Serializable
    data object Cv : BlockType

    @Serializable
    data object Coding : BlockType

    @Serializable
    data object Gym : BlockType

    @Serializable
    data object Free : BlockType

    @Serializable
    data object KidTime : BlockType

    @Serializable
    data object Dishes : BlockType

    @Serializable
    data object Walk : BlockType

    @Serializable
    data class EveningChoice(val choice: Choice) : BlockType

    /** One of your own block kinds (3.0.0). The block carries a copy, so history keeps what it was. */
    @Serializable
    data class Custom(val id: String, val name: String, val colorArgb: Long, val work: Boolean, val pomodoro: Boolean) : BlockType
}

/** CV, Coding, Gym and your own work categories: the blocks that count as worked time and may be paused. */
val BlockType.isWork: Boolean
    get() = when (this) {
        BlockType.Cv, BlockType.Coding, BlockType.Gym -> true
        is BlockType.Custom -> work
        else -> false
    }

@Serializable
data class Pomodoro(
    val focusMinutes: Int = 50,
    val breakMinutes: Int = 10,
)

/** Ids for the two evening blocks the app builds at runtime rather than loading from the asset. */
const val EVENING_DISHES_ID = "evening.dishes"
const val EVENING_CHOICE_ID = "evening.choice"

@Serializable
data class BlockSpec(
    val id: String,
    val type: BlockType,
    @Serializable(with = LocalTimeSerializer::class) val start: LocalTime,
    @Serializable(with = LocalTimeSerializer::class) val end: LocalTime,
    val pomodoro: Pomodoro? = null,
    val quiet: Boolean = false,
)

@Serializable
data class Template(
    val id: TemplateId,
    val name: String,
    val blocks: List<BlockSpec>,
)

/** Today's blocks, copied from a template then changed by a swap or an edit. [blocks] is sorted by start and ids stay unique. */
@Serializable
data class TodayPlan(
    val templateId: TemplateId,
    val blocks: List<BlockSpec>,
    val edited: Boolean = false,
    /** The profile the day started under (3.2.0), so History can label it. */
    val profileName: String? = null,
)

@Serializable
data class Segment(
    val type: BlockType,
    val kind: SegmentKind,
    @Serializable(with = InstantSerializer::class) val plannedStart: Instant? = null,
    @Serializable(with = InstantSerializer::class) val plannedEnd: Instant? = null,
    @Serializable(with = InstantSerializer::class) val actualStart: Instant,
    @Serializable(with = InstantSerializer::class) val actualEnd: Instant? = null,
    val blockId: String? = null,
    val missReason: MissReason? = null,
)

@Serializable
data class DayRecord(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val template: TemplateId?,
    val segments: List<Segment>,
    val flags: Set<DayFlag> = emptySet(),
    val checkIn: CheckIn? = null,
    val plannedBlocks: List<BlockSpec>? = null,
    val demo: Boolean = false,
    /** The profile the day started under (3.2.0). */
    val profileName: String? = null,
)

/**
 * A standalone walk (2.4.0): a timer that runs at any time and does not touch the day's state,
 * plan or segments. [date] is the day it started, so one that crosses midnight is logged on the
 * date it began.
 */
@Serializable
data class WalkSession(
    @Serializable(with = InstantSerializer::class) val start: Instant,
    @Serializable(with = InstantSerializer::class) val end: Instant,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    /** The step-counter reading at the walk's start (2.5.0); null when steps aren't available. */
    val startCounter: Long? = null,
)

/** A finished walk, stored per date in history (2.4.0). */
@Serializable
data class WalkLog(
    @Serializable(with = InstantSerializer::class) val start: Instant,
    @Serializable(with = InstantSerializer::class) val end: Instant,
    /** Steps during the walk (2.5.0); null when the phone restarted or has no sensor. */
    val steps: Long? = null,
)

object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
