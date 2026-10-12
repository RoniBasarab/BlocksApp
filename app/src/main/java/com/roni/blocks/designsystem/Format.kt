package com.roni.blocks.designsystem

/** "5,240": a count with thousands separators, for step totals and per-walk lines (2.5.0). */
internal fun formatSteps(value: Long): String = "%,d".format(value)
