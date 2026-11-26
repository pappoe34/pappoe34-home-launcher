package com.pappoe34.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.runtime.staticCompositionLocalOf

/** Lets Compose ask the hosting activity to allocate, bind and configure a widget. */
interface WidgetBinder {
    /** On success [onBound] receives the new appWidgetId; on cancel or failure it is never called. */
    fun addWidget(provider: AppWidgetProviderInfo, onBound: (Int) -> Unit)
}

val LocalWidgetBinder = staticCompositionLocalOf<WidgetBinder> {
    error("No WidgetBinder provided")
}
