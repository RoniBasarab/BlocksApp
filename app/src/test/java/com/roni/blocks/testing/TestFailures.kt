package com.roni.blocks.testing

import android.content.Context
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.system.FailurePolicy
import com.roni.blocks.core.system.Failures

/**
 * A [Failures] for hand-built code in tests (3.5.0). Loud by default, so a failure a test triggers
 * still fails the test instead of only showing the card.
 */
fun testFailures(context: Context, loud: Boolean = true) = Failures(AppLogger(context), FailurePolicy(loud))
