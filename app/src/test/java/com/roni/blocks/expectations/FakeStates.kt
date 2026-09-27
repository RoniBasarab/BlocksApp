package com.roni.blocks.expectations

import com.roni.blocks.core.model.BlockType
import com.roni.blocks.feature.home.ChipTone
import com.roni.blocks.feature.home.NowButton
import com.roni.blocks.feature.home.NowButtonId
import com.roni.blocks.feature.home.NowButtonStyle
import com.roni.blocks.feature.home.NowCardUi
import kotlinx.collections.immutable.persistentListOf

/** The NowCard shapes from Task-4 §2.2, one per state row. */
object FakeStates {
    fun nowRunningCoding(): NowCardUi = NowCardUi(
        title = "Coding",
        type = BlockType.Coding,
        chip = "Focus",
        chipTone = ChipTone.NEUTRAL,
        subtitle = "until 11:30",
        isLeaking = false,
        showProgress = true,
        buttons = persistentListOf(
            NowButton(NowButtonId.PAUSE, "Pause", NowButtonStyle.SECONDARY),
            NowButton(NowButtonId.INTERRUPT, "Emergency", NowButtonStyle.SECONDARY),
            NowButton(NowButtonId.END_EARLY, "Finish early", NowButtonStyle.SECONDARY),
        ),
    )

    fun nowLeakingCv(): NowCardUi = NowCardUi(
        title = "CV",
        type = BlockType.Cv,
        chip = "Ran over",
        chipTone = ChipTone.LEAK,
        subtitle = "CV ended at 09:30",
        isLeaking = true,
        showProgress = false,
        buttons = persistentListOf(
            NowButton(NowButtonId.NEXT, "Start Coding", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.INTERRUPT, "Emergency", NowButtonStyle.SECONDARY),
        ),
    )

    fun nowPaused(): NowCardUi = NowCardUi(
        title = "Paused",
        type = BlockType.Coding,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = "until 11:30",
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(
            NowButton(NowButtonId.RESUME, "Resume", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.INTERRUPT, "Emergency", NowButtonStyle.SECONDARY),
        ),
    )

    fun nowInterrupted(): NowCardUi = NowCardUi(
        title = "Frozen",
        type = BlockType.Coding,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = null,
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(NowButton(NowButtonId.RESUME, "I'm back", NowButtonStyle.PRIMARY)),
    )

    fun nowKidTime(): NowCardUi = NowCardUi(
        title = "Kid time",
        type = BlockType.KidTime,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = "Evening starts when you tap Kid asleep",
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(
            NowButton(NowButtonId.KID_ASLEEP, "Kid asleep", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY),
        ),
    )

    fun nowDishes(): NowCardUi = NowCardUi(
        title = "Dishes",
        type = BlockType.Dishes,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = "Pick what tonight is about",
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(
            NowButton(NowButtonId.CHOOSE_EVENING, "Choose evening", NowButtonStyle.PRIMARY),
            NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY),
        ),
    )

    fun nowEvening(): NowCardUi = NowCardUi(
        title = "Evening - Game",
        type = BlockType.Free,
        chip = null,
        chipTone = ChipTone.NEUTRAL,
        subtitle = "until 22:30",
        isLeaking = false,
        showProgress = false,
        buttons = persistentListOf(NowButton(NowButtonId.END_DAY, "End day", NowButtonStyle.SECONDARY)),
    )
}
