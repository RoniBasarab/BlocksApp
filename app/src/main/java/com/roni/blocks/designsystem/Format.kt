package com.roni.blocks.designsystem

/** "5,240": a count with thousands separators, for step totals and per-walk lines (2.5.0). */
internal fun formatSteps(value: Long): String = "%,d".format(value)

/** Steps as the estimate they are (2.7.0): rounded to tens, e.g. "2,050". */
internal fun formatStepsApprox(steps: Long): String = formatSteps(((steps + 5) / 10) * 10)
