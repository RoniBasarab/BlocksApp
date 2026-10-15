package com.roni.blocks.testing

import android.content.Context
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.storage.PrefsStorage
import com.roni.blocks.core.system.AppRescue
import com.roni.blocks.core.system.FailurePolicy
import com.roni.blocks.core.system.Failures
import kotlinx.coroutines.Dispatchers

/**
 * A [Failures] for hand-built code in tests (3.5.0). Loud by default, so a failure a test triggers
 * still fails the test instead of only showing the card.
 */
fun testFailures(context: Context, loud: Boolean = true) = Failures(AppLogger(context), FailurePolicy(loud))

/**
 * An [AppRescue] for hand-built code in tests (3.5.0). Its actions start activities, which a test
 * never taps, so a quiet [Failures]-backed store is enough.
 */
fun testRescue(context: Context) =
    AppRescue(context, AppLogger(context), PrefsStorage(context, Dispatchers.Unconfined, testFailures(context, loud = false)))
