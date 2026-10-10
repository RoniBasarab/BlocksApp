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

@Serializable
enum class TemplateId {
    MON_THU,
    SUN_TUE_WED,
    FRI_SAT,
    OFF,
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
    data class EveningChoice(val choice: Choice) : BlockType
}

/** CV, Coding and Gym: the blocks that count as worked time and may be paused. */
val BlockType.isWork: Boolean
    get() = this == BlockType.Cv || this == BlockType.Coding || this == BlockType.Gym

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
    val plannedBlocks: List<BlockSpec>? = null,
    val demo: Boolean = false,
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
