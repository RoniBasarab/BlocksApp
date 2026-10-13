package com.roni.blocks.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class StepsFormatTest {
    @Test
    fun approx_roundsToTens() {
        assertEquals("2,050", formatStepsApprox(2047))
        assertEquals("0", formatStepsApprox(0))
    }
}
