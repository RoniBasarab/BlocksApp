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
                NowButton(NowButtonId.PAUSE, "Pause", NowButtonStyle.ICON),
                NowButton(NowButtonId.INTERRUPT, "Emergency interrupt", NowButtonStyle.ICON),
                NowButton(NowButtonId.END_EARLY, "End early", NowButtonStyle.TEXT),
                NowButton(NowButtonId.NEXT, "Next", NowButtonStyle.PRIMARY),
            ),
        ),
    )

    fun leakingCv(): HomeState = base(
        currentBlockId = "b0",
        leakFromMin = 570,
        nowCard = NowCardUi(
            title = "CV",
            type = BlockType.Cv,
            chip = "Focus",
            chipTone = ChipTone.LEAK,
            subtitle = "until 09:30",
            isLeaking = true,
            showProgress = false,
            buttons = persistentListOf(
                NowButton(NowButtonId.PAUSE, "Pause", NowButtonStyle.ICON),
                NowButton(NowButtonId.INTERRUPT, "Emergency interrupt", NowButtonStyle.ICON),
                NowButton(NowButtonId.END_EARLY, "End early", NowButtonStyle.TEXT),
                NowButton(NowButtonId.NEXT, "Next: Coding", NowButtonStyle.PRIMARY),
            ),
        ),
    )

    private fun base(currentBlockId: String, leakFromMin: Int?, nowCard: NowCardUi): HomeState = HomeState(
        weekdayShort = "SUN",
        dayOfMonth = "27",
        title = "Sunday / Tuesday / Wednesday",
        status = "4 blocks left",
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
