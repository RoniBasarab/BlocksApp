package com.roni.blocks.expectations

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.MainActivity
import com.roni.blocks.core.notifications.NotificationRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The renderer is the only thing that touches [NotificationManager], so every rule about the
 * notification lives here: one at a time, two channels, silent refreshes, no vibration, and a body
 * tap that behaves like tapping the launcher icon.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val visibility = FakeAppVisibility(foreground = false)
    private val renderer = NotificationRenderer(context, visibility, InMemoryDismissalStore())

    @Before
    fun clearNotifications() {
        nm.cancelAll()
    }

    private fun channels() = nm.activeNotifications.map { it.notification.channelId }

    private fun posted(): Notification = nm.activeNotifications.first().notification

    private fun actionLabels(notification: Notification) = notification.actions.map { it.title }

    @Test
    fun exactlyTwoChannels_silentEveningAndVibrationlessDay() {
        renderer.ensureChannels()
        val day = nm.getNotificationChannel("day")
        val evening = nm.getNotificationChannel("evening")
        assertNotNull(day)
        assertNotNull(evening)
        assertEquals(NotificationManager.IMPORTANCE_LOW, evening!!.importance)
        assertTrue("the day channel must not vibrate", !day!!.shouldVibrate())
        assertNull(nm.getNotificationChannel("status"))
        assertNull(nm.getNotificationChannel("day_alerts"))
    }

    @Test
    fun aWholeDay_neverShowsMoreThanOne() {
        FakeNotificationModels.wholeDay.forEach { model ->
            renderer.render(model, transition = model != null)
            assertTrue("two notifications at once", nm.activeNotifications.size <= 1)
        }
        assertEquals(0, nm.activeNotifications.size)
    }

    @Test
    fun running_usesSystemCountdown_withPauseAndNext() {
        renderer.render(FakeNotificationModels.running, transition = true)
        val notification = posted()
        assertEquals("day", notification.channelId)
        assertTrue(notification.extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER, false))
        assertTrue(notification.extras.getBoolean("android.chronometerCountDown", false))
        assertEquals(listOf("Pause", "Next: Free"), actionLabels(notification))
        assertNotEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
    }

    @Test
    fun aTransitionAlerts_butARefreshIsSilent() {
        renderer.render(FakeNotificationModels.running, transition = true)
        assertEquals(0, posted().flags and Notification.FLAG_ONLY_ALERT_ONCE)
        renderer.render(FakeNotificationModels.running, transition = false)
        assertNotEquals(0, posted().flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }

    @Test
    fun silentWhileTheAppIsOnScreen() {
        visibility.foreground = true
        renderer.render(FakeNotificationModels.leakingCv, transition = true)
        assertNotEquals(0, posted().flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }

    @Test
    fun tappingTheBody_resumesLikeTheLauncher() {
        renderer.render(FakeNotificationModels.running, transition = true)
        val contentIntent = requireNotNull(posted().contentIntent)
        val saved = requireNotNull(shadowOf(contentIntent).savedIntent)
        assertEquals(Intent.ACTION_MAIN, saved.action)
        assertTrue(requireNotNull(saved.categories).contains(Intent.CATEGORY_LAUNCHER))
        assertEquals(ComponentName(context, MainActivity::class.java), saved.component)
        assertNotEquals(0, saved.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun swipedAway_staysGoneUntilTheNextTransition() {
        val running = FakeNotificationModels.running
        renderer.render(running, transition = true)
        renderer.onDismissed(running.key)
        nm.cancelAll()
        renderer.render(running, transition = false)
        assertEquals(0, nm.activeNotifications.size)
        renderer.render(FakeNotificationModels.onBreak, transition = true)
        assertEquals(1, nm.activeNotifications.size)
    }

    @Test
    fun dayToEvening_switchesChannel_andKeepsOneNotification() {
        renderer.render(FakeNotificationModels.leakingCv, transition = true)
        assertEquals(listOf("day"), channels())
        renderer.render(FakeNotificationModels.kidTimePrompt, transition = true)
        assertEquals(listOf("evening"), channels())
        assertEquals(listOf("Kid asleep", "End day"), actionLabels(posted()))
    }

    @Test
    fun theDayEnding_removesTheNotification() {
        renderer.render(FakeNotificationModels.running, transition = true)
        renderer.render(null, transition = true)
        assertEquals(0, nm.activeNotifications.size)
    }
}
