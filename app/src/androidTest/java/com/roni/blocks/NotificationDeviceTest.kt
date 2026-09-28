package com.roni.blocks

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task-4 §4.6 device tests D1-D8. Roni runs these on a connected phone:
 *
 * ```
 * ./gradlew connectedDebugAndroidTest
 * ```
 *
 * These drive the real notification shade, so the behaviour under test (ongoing flags, shade
 * dismissal, background action handling) cannot be faked in a JVM test.
 *
 * Selectors are text and content descriptions that exist in the app: Compose `testTag`s are not
 * published to the accessibility tree, so UiAutomator cannot see them.
 *
 * Times come from `assets/templates.json` for SUN_TUE_WED (cv 09:00-09:30, coding 09:30-11:30) and
 * the titles come from `NotificationModelMapper`. Because CV is a 30-minute block with no pomodoro,
 * any time after 09:30 is the *leak* state, whose title is "CV ran over" and whose only action is
 * "Next: Coding". Selectors therefore match the exact title, never `contains("Coding")`, which would
 * also match the action button.
 *
 * Note: this source set compiles against the platform SDK, where `Notification.getTitle()` was
 * removed, so titles are read out of `extras` via `EXTRA_TITLE`.
 */
@RunWith(AndroidJUnit4::class)
class NotificationDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val notifications: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    @Before
    fun resetWorld() {
        travelReset()
        listOf("day", "settings", "history").forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        notifications.cancelAll()
        device.pressHome()
    }

    @After
    fun leaveClean() {
        device.pressHome()
    }

    // ---- time travel (explicit broadcast to the debug-only receiver) --------------------

    private fun travelReset() = broadcast { putExtra("reset", true) }

    private fun travelTo(hhmm: String) = broadcast { putExtra("to", hhmm) }

    private fun broadcast(extras: Intent.() -> Unit) {
        context.sendBroadcast(
            Intent("$PKG/com.roni.blocks.debugtools.TimeTravelReceiver").setPackage(PKG).apply(extras),
        )
    }

    // ---- app driving -------------------------------------------------------------------

    private fun launchApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(PKG)!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(launch)
        assertTrue("app never reached Home", device.wait(Until.hasObject(By.text(HOME_TAB)), 10_000))
    }

    /**
     * Travel first, then start the day, so the day lands on the block that contains that time.
     * Not started means the picker is up; picking the focus template starts a real day.
     */
    private fun startDayAt(hhmm: String) {
        travelTo(hhmm)
        launchApp()
        val card = device.wait(Until.findObject(By.text(FOCUS_TEMPLATE)), 10_000)
        assertNotNull("template picker did not offer $FOCUS_TEMPLATE at $hhmm", card)
        requireNotNull(card).click()
        assertTrue(
            "Home never appeared after starting the day at $hhmm",
            device.wait(Until.hasObject(By.text(HOME_TAB)), 10_000),
        )
    }

    private fun openSettings() {
        val menu = device.wait(Until.findObject(By.desc(MENU)), 5_000)
        assertNotNull("no menu button on the Home top bar", menu)
        requireNotNull(menu).click()
        val settings = device.wait(Until.findObject(By.text(SETTINGS_MENU_ITEM)), 5_000)
        assertNotNull("menu did not offer Settings", settings)
        requireNotNull(settings).click()
        assertTrue("Settings never opened", device.wait(Until.hasObject(By.text(ABOUT_SECTION)), 5_000))
    }

    private fun appInForeground(): Boolean = device.currentPackageName == PKG

    // ---- notification state ------------------------------------------------------------
    //
    // A posted notification is not a screen node once the shade closes, so its title is read from
    // NotificationManager and polled rather than looked up in the view hierarchy.

    private fun activeTitles(): List<String> =
        notifications.activeNotifications
            .mapNotNull { it.notification }
            .map { posted ->
                posted.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            }

    private fun activeActions(): List<String> =
        notifications.activeNotifications
            .mapNotNull { it.notification }
            .flatMap { posted -> posted.actions?.map { it.title.toString() }.orEmpty() }

    private fun awaitTitle(fragment: String, timeoutMs: Long = 5_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (activeTitles().any { it.contains(fragment) }) return true
            SystemClock.sleep(100)
        }
        return activeTitles().any { it.contains(fragment) }
    }

    private fun awaitNoNotifications(timeoutMs: Long = 5_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (notifications.activeNotifications.isEmpty()) return true
            SystemClock.sleep(100)
        }
        return notifications.activeNotifications.isEmpty()
    }

    private fun awaitInShade(selector: BySelector, what: String): UiObject2 {
        assertTrue("could not open the notification shade", device.openNotification())
        val node = device.wait(Until.findObject(selector), 5_000)
        assertNotNull("shade did not offer $what", node)
        return requireNotNull(node)
    }

    private fun taskCount(): Int =
        context.getSystemService(ActivityManager::class.java)
            .appTasks.count { it?.taskInfo?.baseIntent?.component?.packageName == PKG }

    private companion object {
        const val PKG = "com.roni.blocks.debug"
        const val HOME_TAB = "Home"
        const val MENU = "Menu"
        const val SETTINGS_MENU_ITEM = "Settings"
        const val ABOUT_SECTION = "About"
        const val FOCUS_TEMPLATE = "Sunday / Tuesday / Wednesday"

        // Titles from NotificationModelMapper for SUN_TUE_WED.
        const val CV = "CV"
        const val CV_RAN_OVER = "CV ran over"
        const val CODING = "Coding"
        const val CODING_FOCUS = "Focus 1/2"
        const val START_CODING = "Next: Coding"
        const val KID_TIME = "Kid time"
        const val KID_ASLEEP = "Kid asleep"
    }

    // ---- D1: tap notification while app is open ----------------------------------------

    @Test
    fun d1_tapNotificationWhileOpen_closesShadeAndStaysOnSettings() {
        startDayAt("09:40")
        openSettings()

        awaitInShade(By.text(CV_RAN_OVER), CV_RAN_OVER).click()

        assertTrue(
            "shade closed and Settings is interactive again",
            device.wait(Until.hasObject(By.text(ABOUT_SECTION)), 5_000),
        )
        assertTrue("app stayed in the foreground", appInForeground())

        device.pressBack()
        assertTrue(
            "Back returns to Home, never the picker",
            device.wait(Until.hasObject(By.text(HOME_TAB)), 5_000),
        )
    }

    // ---- D2: tap notification from background ------------------------------------------

    @Test
    fun d2_tapNotificationFromBackground_returnsToSameState() {
        startDayAt("09:40")
        device.pressHome()

        awaitInShade(By.text(CV_RAN_OVER), CV_RAN_OVER).click()

        assertTrue("app came back to the foreground", device.wait(Until.hasObject(By.text(HOME_TAB)), 10_000))
        assertTrue("app really is in front", appInForeground())
        assertEquals("no second instance", 1, taskCount())
    }

    // ---- D3: action with app in background ----------------------------------------------

    @Test
    fun d3_actionFromBackground_updatesNotificationWithoutOpeningTheApp() {
        startDayAt("09:31")
        device.pressHome()

        assertTrue("a next-block action is offered", activeActions().contains(START_CODING))
        awaitInShade(By.text(START_CODING), START_CODING).click()

        assertTrue("notification advanced to the coding focus block", awaitTitle(CODING_FOCUS))
        assertFalse("the app must NOT come to the foreground", appInForeground())
    }

    // ---- D4: action with app open ------------------------------------------------------

    @Test
    fun d4_actionWithAppOpen_switchesNowCardWithoutNavigating() {
        startDayAt("09:31")

        awaitInShade(By.text(START_CODING), START_CODING).click()

        assertTrue(
            "NowCard switched to Coding on Home",
            device.wait(Until.hasObject(By.textContains(CODING)), 5_000),
        )
        assertTrue("still on Home, no navigation", device.findObject(By.text(HOME_TAB)) != null)
        assertTrue("notification agrees", awaitTitle(CODING_FOCUS))
    }

    // ---- D5: double tap -----------------------------------------------------------------
    //
    // Tapping a notification action dismisses the shade, so a genuine second physical tap cannot
    // land on the same node. The tap is attempted defensively and the assertion is on the outcome
    // the spec cares about: exactly one transition, so Coding and never Free. DayOrchestrator's
    // `doubleTap_advancesOnce` covers two real events at the unit level.

    @Test
    fun d5_doubleTap_advancesOnlyOnce() {
        startDayAt("09:31")
        device.pressHome()

        val action = awaitInShade(By.text(START_CODING), START_CODING)
        action.click()
        runCatching { action.click() }

        assertTrue(
            "landed on Coding, not Free (exactly one transition)",
            awaitTitle(CODING_FOCUS) && activeTitles().none { it.contains("Free") },
        )
    }

    // ---- D6: swipe away -----------------------------------------------------------------
    //
    // NOTE: NotificationRenderer sets FLAG_ONGOING_EVENT, which stock Android treats as
    // non-dismissible. Task-4.md:88 mandates that flag while Task-4.md:647 asks for a swipe. If this
    // test fails on a real phone, that contradiction is the cause, not the dismissal logic.

    @Test
    fun d6_swipeAway_staysGoneUntilTheNextTransition() {
        startDayAt("09:40")

        awaitInShade(By.text(CV_RAN_OVER), CV_RAN_OVER).swipe(Direction.RIGHT, 0.8f)
        device.pressBack()

        launchApp()
        device.pressHome()
        assertTrue("stays gone after reopening the app", awaitNoNotifications())

        // 11:30 is the next real block end after 09:31, so the dismissal has to be cleared.
        travelTo("11:30")
        assertTrue("the next transition brings it back", awaitTitle(CV_RAN_OVER, 10_000))
    }

    // ---- D7: process killed -------------------------------------------------------------

    @Test
    fun d7_processKilled_stillRefreshesTheNotification() {
        startDayAt("09:10")
        assertTrue("CV is running and has not leaked yet", awaitTitle(CV))

        device.pressHome()
        device.executeShellCommand("am kill $PKG")

        travelTo("09:31")
        assertTrue("notification rebuilt itself into the leak state", awaitTitle(CV_RAN_OVER, 15_000))
    }

    // ---- D8: evening from Settings ------------------------------------------------------

    @Test
    fun d8_eveningChoiceSheet_onlyAppearsOnHome() {
        startDayAt("20:05")
        openSettings()

        assertTrue("kid time prompt is posted", awaitTitle(KID_TIME))
        awaitInShade(By.text(KID_TIME), KID_TIME).click()

        assertTrue("Settings stays put", device.wait(Until.hasObject(By.text(ABOUT_SECTION)), 5_000))

        device.pressBack()
        assertTrue(
            "the choice sheet appears once Home is open",
            device.wait(Until.hasObject(By.text(KID_ASLEEP)), 10_000),
        )
    }

    // ---- D13: cold start from a notification tap must restore Home ----------------------

    @Test
    fun d13_tapNotificationAfterProcessKill_restoresHome() {
        startDayAt("09:10")
        device.pressHome()
        device.executeShellCommand("am kill $PKG")

        awaitInShade(By.text(CV), CV).click()

        assertTrue(
            "cold start via the notification lands on Home",
            device.wait(Until.hasObject(By.text(HOME_TAB)), 10_000),
        )
        assertTrue("never the template picker", device.findObject(By.text(FOCUS_TEMPLATE)) == null)
    }

    // ---- D14: relaunch from the launcher after a process kill ---------------------------

    @Test
    fun d14_relaunchFromLauncher_restoresHome() {
        startDayAt("09:10")
        device.pressHome()
        device.executeShellCommand("am kill $PKG")

        launchApp()

        assertTrue(
            "launcher relaunch lands on Home",
            device.wait(Until.hasObject(By.text(HOME_TAB)), 10_000),
        )
        assertTrue("never the template picker", device.findObject(By.text(FOCUS_TEMPLATE)) == null)
    }
}
