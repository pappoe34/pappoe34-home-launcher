package com.pappoe34.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/** Single AppWidgetHost for the launcher; the activity drives start/stopListening. */
class LauncherWidgetHost(context: Context) : AppWidgetHost(context.applicationContext, HOST_ID) {

    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)

    fun providerFor(appWidgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(appWidgetId)

    fun createHostView(appWidgetId: Int): AppWidgetHostView? {
        val info = providerFor(appWidgetId) ?: return null
        return createView(appContext, appWidgetId, info)
    }

    /** Every widget provider the user could add, sorted by app then widget label. */
    fun installedProviders(): List<AppWidgetProviderInfo> {
        val pm = appContext.packageManager
        return manager.installedProviders
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .sortedWith(
                compareBy<AppWidgetProviderInfo>({ appLabel(it).lowercase() }, { it.loadLabel(pm).lowercase() }),
            )
    }

    fun appLabel(info: AppWidgetProviderInfo): String {
        val pm = appContext.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
            .getOrDefault(info.provider.packageName)
    }

    private companion object {
        const val HOST_ID = 0x4C4E4348 // "LNCH"
    }
}
