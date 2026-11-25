package com.pappoe34.launcher

import android.app.Application
import com.pappoe34.launcher.data.AppRepository

class LauncherApplication : Application() {
    val appRepository: AppRepository by lazy { AppRepository(this) }
}
