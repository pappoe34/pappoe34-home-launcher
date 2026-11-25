package com.pappoe34.launcher

import android.app.Application
import com.pappoe34.launcher.data.AppRepository
import com.pappoe34.launcher.data.LauncherPreferences
import com.pappoe34.launcher.data.LauncherWidgetHost
import com.pappoe34.launcher.data.TickPlayer

class LauncherApplication : Application() {
    val appRepository: AppRepository by lazy { AppRepository(this) }
    val preferences: LauncherPreferences by lazy { LauncherPreferences(this) }
    val tickPlayer: TickPlayer by lazy { TickPlayer(this) }
    val widgetHost: LauncherWidgetHost by lazy { LauncherWidgetHost(this) }
}
