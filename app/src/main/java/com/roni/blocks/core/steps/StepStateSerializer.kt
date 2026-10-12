package com.roni.blocks.core.steps

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** [StepState] as "day,baseline,today"; [StepBook] stays a pure, annotation-free type (2.5.0). */
object StepStateSerializer : KSerializer<StepState> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StepState", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StepState) {
        encoder.encodeString("${value.day},${value.baseline},${value.today}")
    }

    override fun deserialize(decoder: Decoder): StepState {
        val parts = decoder.decodeString().split(',')
        return StepState(
            day = parts.getOrNull(0)?.toLongOrNull() ?: -1,
            baseline = parts.getOrNull(1)?.toLongOrNull() ?: -1,
            today = parts.getOrNull(2)?.toLongOrNull() ?: 0,
        )
    }
}
