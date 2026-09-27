package com.roni.blocks.feature.home

import com.roni.blocks.core.model.BlockType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

object FakeHomeStates {
    fun runningCoding(): HomeState = base(
        currentBlockId = "b1",
        leakFromMin = null,
        nowCard = NowCardUi(
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
        ),
    )

    fun leakingCv(): HomeState = base(
        currentBlockId = "b0",
        leakFromMin = 570,
        nowCard = NowCardUi(
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
        ),
    )

    fun freeBreak(): HomeState = base(
        currentBlockId = "b2",
        leakFromMin = null,
        status = "3 blocks left",
        nowCard = NowCardUi(
            title = "Free",
            type = BlockType.Free,
            chip = null,
            chipTone = ChipTone.NEUTRAL,
            subtitle = "until 14:00",
            isLeaking = false,
            showProgress = false,
            buttons = persistentListOf(
                NowButton(NowButtonId.PAUSE, "Pause", NowButtonStyle.SECONDARY),
                NowButton(NowButtonId.INTERRUPT, "Emergency", NowButtonStyle.SECONDARY),
                NowButton(NowButtonId.END_EARLY, "Finish early", NowButtonStyle.SECONDARY),
            ),
        ),
    )

    fun kidTime(): HomeState = base(
        currentBlockId = null,
        leakFromMin = null,
        status = "6 blocks left",
        nowCard = NowCardUi(
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
        ),
    )

    fun ended(): HomeState = base(
        currentBlockId = null,
        leakFromMin = null,
        status = "6 blocks left",
        nowCard = NowCardUi(
            title = "Day done",
            type = null,
            chip = null,
            chipTone = ChipTone.NEUTRAL,
            subtitle = "Leak 0 min · 6 blocks",
            isLeaking = false,
            showProgress = false,
            buttons = persistentListOf(),
        ),
    )

    private fun base(
        currentBlockId: String?,
        leakFromMin: Int?,
        nowCard: NowCardUi,
        status: String = "4 blocks left",
    ) = HomeState(
        weekdayShort = "SUN",
        dayOfMonth = "27",
        title = "Sunday / Tuesday / Wednesday",
        status = status,
        hourLabels = (DAY_START_MIN..DAY_END_MIN step 60).map { hhmm(it) }.toPersistentList(),
        blocks = sunTueWedBlocks(),
        currentBlockId = currentBlockId,
        leakFromMin = leakFromMin,
        nowCard = nowCard,
        eveningChoicePending = false,
    )

    private fun sunTueWedBlocks(): ImmutableList<CalendarBlockUi> = listOf(
        block("b0", BlockType.Cv, "CV", 540, 570, null),
        block("b1", BlockType.Coding, "Coding", 570, 690, "Pomodoro 50/10"),
        block("b2", BlockType.Free, "Free", 690, 840, null),
        block("b3", BlockType.Coding, "Coding", 840, 930, "Pomodoro 50/10"),
        block("b4", BlockType.Free, "Free", 930, 980, null),
        block("b5", BlockType.KidTime, "Kid time", 980, 1350, null),
    ).toPersistentList()

    private fun block(
        id: String,
        type: BlockType,
        title: String,
        startMin: Int,
        endMin: Int,
        subtitle: String?,
    ) = CalendarBlockUi(
        id = id,
        type = type,
        title = title,
        startMin = startMin,
        endMin = endMin,
        timeLabel = "${hhmm(startMin)}–${hhmm(endMin)}",
        subtitle = subtitle,
    )
}
