package com.roni.blocks.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.roni.blocks.benchmark"

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = { grantNotifications() },
    ) {
        pressHome()
        startActivityAndWait()
    }
}

@RunWith(AndroidJUnit4::class)
class HomeScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollCalendar() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            grantNotifications()
            pressHome()
            startActivityAndWait()
            device.findObject(By.res("template_card_SUN_TUE_WED"))?.click()
            device.wait(Until.hasObject(By.res("home_calendar")), 5_000)
        },
    ) {
        val calendar = device.findObject(By.res("home_calendar"))
        calendar.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            calendar.fling(Direction.DOWN)
            calendar.fling(Direction.UP)
        }
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.grantNotifications() {
    device.executeShellCommand("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
}
