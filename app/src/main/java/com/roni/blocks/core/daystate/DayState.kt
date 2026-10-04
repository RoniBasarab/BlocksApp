package com.roni.blocks.core.daystate

import com.roni.blocks.core.model.Choice
import com.roni.blocks.core.model.InstantSerializer
import com.roni.blocks.core.model.TemplateId
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder

@Serializable
sealed interface DayState {
    @Serializable
    data object NotStarted : DayState

    @Serializable
    data class Running(
        val template: TemplateId,
        val blockIndex: Int,
    ) : DayState

    @Serializable
    data class Paused(
        val running: Running,
        @Serializable(with = InstantSerializer::class) val since: Instant,
    ) : DayState

    @Serializable
    data object KidTime : DayState

    @Serializable
    data class Evening(
        val blockIndex: Int,
    ) : DayState

    @Serializable
    data object Ended : DayState

    @Serializable
    data object Off : DayState
}

/**
 * Why the day stopped. [EndReason.EMERGENCY] is a stop rather than a pause: the day is over and
 * stays over, so the flag exists to explain it in history instead of to resume it.
 */
@Serializable
enum class EndReason { NORMAL, EMERGENCY }

@Serializable
sealed interface DayEvent {
    @Serializable
    data class StartDay(val template: TemplateId) : DayEvent

    @Serializable
    data object TakeOffDay : DayEvent

    /**
     * Re-plans the rest of today around [template] from now. What the day already recorded stays as
     * history; only the part of today that had not happened yet is replaced.
     */
    @Serializable
    data class SwapTemplate(val template: TemplateId) : DayEvent

    /**
     * Advances to the next block. [expectedBlockId] is the block the tap was aimed at, so a double
     * tap, or a notification that outlived its block, cannot skip a block nobody ever saw.
     */
    @Serializable
    data class Next(val expectedBlockId: String) : DayEvent

    @Serializable
    data object Pause : DayEvent

    @Serializable
    data object Resume : DayEvent

    @Serializable
    data object KidTimeStarted : DayEvent

    @Serializable
    data object KidAsleep : DayEvent

    @Serializable
    data class EveningChoice(val choice: Choice) : DayEvent

    @Serializable
    data class EndDay(val reason: EndReason) : DayEvent
}

/**
 * The frozen-schedule state older versions could persist. Kept only so its stored form can still be
 * read: the app can no longer enter that state and offered no honest way out of it, so a device
 * that was interrupted is simply a day that was [DayState.Running] on the same block.
 */
@Serializable
private data class LegacyInterrupted(
    val running: DayState.Running,
    @Serializable(with = InstantSerializer::class) val since: Instant,
)

/**
 * Reads persisted day state, accepting the removed frozen-interrupt form as a plain
 * [DayState.Running]. Encoding is unchanged, so new writes still look exactly like old reads.
 */
object DayStateSerializer : KSerializer<DayState> {
    private val delegate: KSerializer<DayState> = DayState.serializer()

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: DayState): Unit = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): DayState {
        // Only a JSON decoder can be re-read; anything else has no legacy form to fall back to.
        val input = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val element = input.decodeJsonElement()
        return runCatching { input.json.decodeFromJsonElement(delegate, element) }
            .recoverCatching { input.json.decodeFromJsonElement(LegacyInterrupted.serializer(), element).running }
            .getOrElse { cause -> throw SerializationException("unreadable day state", cause) }
    }
}

fun DayState.isActive(): Boolean = when (this) {
    is DayState.Running,
    is DayState.Paused,
    DayState.KidTime,
    is DayState.Evening,
    -> true
    DayState.NotStarted, DayState.Ended, DayState.Off -> false
}

val DayState.isDishesSlot: Boolean
    get() = this is DayState.Evening && blockIndex == 0

fun DayState.templateId(): TemplateId? = when (this) {
    is DayState.Running -> template
    is DayState.Paused -> running.template
    DayState.Off -> TemplateId.OFF
    else -> null
}

fun rollover(state: DayState, storedDate: LocalDate, today: LocalDate): DayState =
    if (storedDate != today) DayState.NotStarted else state
