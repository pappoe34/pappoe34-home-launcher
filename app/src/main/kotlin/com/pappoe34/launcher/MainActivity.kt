package com.pappoe34.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pappoe34.launcher.data.LauncherWidgetHost
import com.pappoe34.launcher.ui.WheelHomeScreen
import com.pappoe34.launcher.ui.theme.LauncherTheme
import com.pappoe34.launcher.ui.widgets.LocalWidgetBinder
import com.pappoe34.launcher.ui.widgets.WidgetBinder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The HOME activity. Runs as a singleTask; Back is handled inside Compose and never finishes it.
 * Also owns the app-widget host lifecycle and the bind / configure flow for new widgets.
 */
class MainActivity : ComponentActivity(), WidgetBinder {

    private val launcherApp: LauncherApplication get() = application as LauncherApplication
    private val widgetHost: LauncherWidgetHost get() = launcherApp.widgetHost

    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingCallback: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launcherApp.preferences.settings.map { it.blur }.distinctUntilChanged().collect { applyWallpaperBlur(it) }
            }
        }

        setContent {
            CompositionLocalProvider(LocalWidgetBinder provides this) {
                LauncherTheme {
                    WheelHomeScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { widgetHost.startListening() }
    }

    override fun onStop() {
        runCatching { widgetHost.stopListening() }
        super.onStop()
    }

    // ---- WidgetBinder -------------------------------------------------------

    @Suppress("DEPRECATION")
    override fun addWidget(provider: AppWidgetProviderInfo, onBound: (Int) -> Unit) {
        cancelPendingWidget()
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        pendingCallback = onBound

        val user = provider.profile ?: Process.myUserHandle()
        val bound = widgetHost.manager.bindAppWidgetIdIfAllowed(id, user, provider.provider, null)
        if (bound) {
            configureOrFinish(id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                provider.profile?.let { putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, it) }
            }
            runCatching { startActivityForResult(intent, REQUEST_BIND) }
                .onFailure { cancelPendingWidget(); toast(R.string.widget_add_failed) }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureOrFinish(id: Int) {
        val info = widgetHost.providerFor(id)
        if (info?.configure != null) {
            val launched = runCatching {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE, null)
            }.isSuccess
            if (!launched) finishPendingWidget()
        } else {
            finishPendingWidget()
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND -> if (resultCode == RESULT_OK) configureOrFinish(pendingWidgetId) else cancelPendingWidget()
            REQUEST_CONFIGURE -> if (resultCode == RESULT_OK) finishPendingWidget() else cancelPendingWidget()
        }
    }

    private fun finishPendingWidget() {
        val id = pendingWidgetId
        val callback = pendingCallback
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingCallback = null
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) callback?.invoke(id)
    }

    private fun cancelPendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) widgetHost.deleteAppWidgetId(pendingWidgetId)
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingCallback = null
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    // ---- Wallpaper blur -----------------------------------------------------

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
        const val REQUEST_BIND = 0x5742
        const val REQUEST_CONFIGURE = 0x5743
    }
}
