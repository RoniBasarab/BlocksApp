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
enum class ResumeMode {
    RESUME_WHERE_I_WAS,
    JUMP_TO_NOW,
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

@Serializable
data class Pomodoro(
    val focusMinutes: Int = 50,
    val breakMinutes: Int = 10,
)

@Serializable
data class BlockSpec(
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
)

@Serializable
data class DayRecord(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val template: TemplateId?,
    val segments: List<Segment>,
    val flags: Set<DayFlag> = emptySet(),
)

@Serializable
data class DebtRecord(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val leakMinutes: Int,
    val pauseMinutes: Int,
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
