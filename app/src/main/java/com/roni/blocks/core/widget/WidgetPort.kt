package com.roni.blocks.core.widget

import com.roni.blocks.core.notifications.NotificationModel

/**
 * Everything the app needs from "the thing that shows the home-screen widget". Exists so the
 * orchestrator can be driven by hand-built tests without a widget on screen.
 */
interface WidgetPort {
    /** Null [model] hides the details and shows a static "No day running" state. */
    fun update(model: NotificationModel?)
}