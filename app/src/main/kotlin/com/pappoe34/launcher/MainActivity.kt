package com.pappoe34.launcher

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pappoe34.launcher.ui.WheelHomeScreen
import com.pappoe34.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The HOME activity. Runs as a singleTask; Back is handled inside Compose and never finishes it. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preferences = (application as LauncherApplication).preferences
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferences.settings.map { it.blur }.distinctUntilChanged().collect { applyWallpaperBlur(it) }
            }
        }

        setContent {
            LauncherTheme {
                WheelHomeScreen()
            }
        }
    }

    /** Blurs whatever is behind this window (the wallpaper) on Android 12+, when the device supports it. */
    private fun applyWallpaperBlur(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val supported = windowManager.isCrossWindowBlurEnabled
        val radius = if (enabled && supported) BLUR_RADIUS_PX else 0
        if (radius > 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        window.attributes = window.attributes.also { it.blurBehindRadius = radius }
    }

    private companion object {
        const val BLUR_RADIUS_PX = 48
    }
}
