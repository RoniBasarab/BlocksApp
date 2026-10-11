package com.roni.blocks.core.widget

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WidgetLayoutTest {

    @Test
    fun widgetLayout_inflatesAsRemoteViews() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        RemoteViews(context.packageName, R.layout.widget_now).apply(context, FrameLayout(context))
    }
}