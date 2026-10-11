package com.roni.blocks.core.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.roni.blocks.R
import com.roni.blocks.core.notifications.NotificationModel
import com.roni.blocks.core.notifications.openAppIntent
import com.roni.blocks.core.time.SystemTimeMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Paints the single home-screen widget from the same [NotificationModel] the notification uses.
 * A pure reflection of whatever model it's given; the orchestrator decides what that model is.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemTime: SystemTimeMapper = SystemTimeMapper.Identity,
) : WidgetPort {
    override fun update(model: NotificationModel?) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, BlocksWidget::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_now)
        views.setTextViewText(R.id.widget_title, model?.title ?: "Blocks")
        views.setTextViewText(R.id.widget_text, model?.text ?: "No day running")
        views.setInt(R.id.widget_accent, "setBackgroundColor", model?.accentArgb ?: 0xFF46433D.toInt())
        val end = model?.countdownTo
        if (end == null) {
            views.setViewVisibility(R.id.widget_timer, View.GONE)
        } else {
            val base = SystemClock.elapsedRealtime() + (systemTime.systemMillis(end) - System.currentTimeMillis())
            views.setChronometer(R.id.widget_timer, base, null, true)
            views.setChronometerCountDown(R.id.widget_timer, true)
            views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
        }
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        manager.updateAppWidget(ids, views)
    }
}