package com.roni.blocks.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.roni.blocks.MainActivity

/**
 * The single launcher-style intent: notification content taps and the widget's root both open the
 * main activity this way, so both surfaces come back to the same screen.
 */
internal fun openAppIntent(context: Context): PendingIntent {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setClass(context, MainActivity::class.java)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}