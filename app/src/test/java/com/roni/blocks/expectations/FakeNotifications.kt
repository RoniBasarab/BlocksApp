package com.roni.blocks.expectations

import com.roni.blocks.core.notifications.ActionEvent
import com.roni.blocks.core.notifications.AppVisibility
import com.roni.blocks.core.notifications.DismissalStore
import com.roni.blocks.core.notifications.NotificationActionModel
import com.roni.blocks.core.notifications.NotificationChannelKind
import com.roni.blocks.core.notifications.NotificationModel
import kotlin.time.Instant

class FakeAppVisibility(var foreground: Boolean = false) : AppVisibility {
    override val isForeground: Boolean get() = foreground
}

class InMemoryDismissalStore : DismissalStore {
    private val keys = mutableSetOf<String>()

    override fun isDismissed(key: String): Boolean = key in keys

    override fun remember(key: String) {
        keys += key
    }

    override fun forget(key: String) {
        keys -= key
    }
}

/** Notification models for renderer tests, independent of the mapper that produces them in prod. */
internal object FakeNotificationModels {
    private val at: Instant = Instant.parse("2026-09-27T12:00:00Z")

    val running = model(
        key = "b1:running",
        channel = NotificationChannelKind.DAY,
        title = "Coding",
        text = "until 12:00",
        countdownTo = at,
        actions = listOf(
            NotificationActionModel("Pause", ActionEvent.PAUSE, "b1"),
            NotificationActionModel("Next: Free", ActionEvent.START_NEXT, "b1"),
        ),
    )

    val onBreak = model(
        key = "b1:break",
        channel = NotificationChannelKind.DAY,
        title = "Coding · Break",
        text = "focus resumes at 12:05",
        countdownTo = at,
        actions = listOf(
            NotificationActionModel("Pause", ActionEvent.PAUSE, "b1"),
            NotificationActionModel("Next: Free", ActionEvent.START_NEXT, "b1"),
        ),
    )

    val paused = model(
        key = "b1:paused",
        channel = NotificationChannelKind.DAY,
        title = "Paused · Coding",
        text = "Resume when you're back",
        countdownTo = null,
        actions = listOf(NotificationActionModel("Resume", ActionEvent.RESUME, "b1")),
    )

    val leakingCv = model(
        key = "b0:leak",
        channel = NotificationChannelKind.DAY,
        title = "CV ran over",
        text = "Next: Coding when you're ready",
        countdownTo = at,
        actions = listOf(NotificationActionModel("Next: Coding", ActionEvent.START_NEXT, "b0")),
    )

    val kidTimePrompt = model(
        key = "kid",
        channel = NotificationChannelKind.EVENING,
        title = "Kid time",
        text = "Tap Kid asleep to start your evening",
        countdownTo = null,
        actions = listOf(
            NotificationActionModel("Kid asleep", ActionEvent.KID_ASLEEP, null),
            NotificationActionModel("End day", ActionEvent.END_DAY, null),
        ),
    )

    /** The waiting day (3.4.2): the widget shows it, the shade must not. */
    val waiting = model(
        key = "waiting",
        channel = NotificationChannelKind.DAY,
        title = "Day starts at 09:00",
        text = "Work",
        countdownTo = at,
        actions = emptyList(),
        inShade = false,
    )

    /** The 09:00-10:00 prompt that IS shown, used to check the waiting render cancels it. */
    val notStarted = model(
        key = "not_started",
        channel = NotificationChannelKind.DAY,
        title = "Start your day",
        text = "Pick today's template",
        countdownTo = null,
        actions = emptyList(),
    )

    /** A plausible run of a whole day, in order. */
    val wholeDay: List<NotificationModel?> = listOf(
        leakingCv, paused, running, onBreak, running, kidTimePrompt, null,
    )

    private fun model(
        key: String,
        channel: NotificationChannelKind,
        title: String,
        text: String,
        countdownTo: Instant?,
        actions: List<NotificationActionModel>,
        inShade: Boolean = true,
    ) = NotificationModel(
        key = key,
        channel = channel,
        title = title,
        text = text,
        countdownTo = countdownTo,
        actions = actions,
        accentArgb = 0xFF7A9CC6.toInt(),
        inShade = inShade,
    )
}
