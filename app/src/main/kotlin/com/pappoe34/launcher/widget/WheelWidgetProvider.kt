package com.pappoe34.launcher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import com.pappoe34.launcher.LauncherApplication
import com.pappoe34.launcher.R
import com.pappoe34.launcher.model.LauncherSettings
import com.pappoe34.launcher.model.Ring
import com.pappoe34.launcher.model.SlotId
import com.pappoe34.launcher.ui.WheelOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Home-screen widget showing the dial as an image over a 6x6 grid of tap zones.
 * Tapping an app opens it (and rotates it into its ring's window for next time);
 * tapping the hub, an empty slot or anywhere else opens the full wheel overlay.
 * ACTION_SELECT is kept for select-only taps if a future layout wants them.
 */
class WheelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateIds(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        updateIds(context, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SELECT) {
            val ring = intent.getStringExtra(EXTRA_RING)?.let { name -> Ring.entries.firstOrNull { it.name == name } }
            val slot = intent.getIntExtra(EXTRA_SLOT, -1)
            if (ring != null && slot >= 0) {
                val pending = goAsync()
                scope.launch {
                    try {
                        val app = context.applicationContext as LauncherApplication
                        app.preferences.setSelected(ring, slot)
                        if (app.preferences.settings.first().haptics) vibrate(context)
                        updateAll(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_SELECT = "com.pappoe34.launcher.widget.SELECT"
        private const val EXTRA_RING = "ring"
        private const val EXTRA_SLOT = "slot"
        private const val GRID = 6
        private const val MAX_PX = 1200

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Re-render every placed widget; safe to call from anywhere. */
        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, WheelWidgetProvider::class.java))
            if (ids.isEmpty()) return
            updateIds(appContext, ids)
        }

        private fun updateIds(context: Context, ids: IntArray) {
            val appContext = context.applicationContext
            scope.launch {
                val app = appContext as LauncherApplication
                val settings = app.preferences.settings.first()
                val manager = AppWidgetManager.getInstance(appContext)
                val renderer = WheelRenderer(appContext)
                ids.forEach { id ->
                    runCatching {
                        manager.updateAppWidget(id, buildViews(appContext, manager, renderer, id, settings))
                    }
                }
            }
        }

        private fun buildViews(
            context: Context,
            manager: AppWidgetManager,
            renderer: WheelRenderer,
            appWidgetId: Int,
            settings: LauncherSettings,
        ): RemoteViews {
            val density = context.resources.displayMetrics.density
            val options = manager.getAppWidgetOptions(appWidgetId)
            val portrait = context.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val widthDp = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250)
            val heightDp = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250)
            val widthPx = (widthDp.coerceAtLeast(120) * density).roundToInt().coerceAtMost(MAX_PX)
            val heightPx = (heightDp.coerceAtLeast(120) * density).roundToInt().coerceAtMost(MAX_PX)

            val views = RemoteViews(context.packageName, R.layout.widget_wheel)
            views.setImageViewBitmap(R.id.widget_dial, renderer.render(widthPx, heightPx, settings))

            val layout = WheelRenderer.Layout(widthPx.toFloat(), heightPx.toFloat())
            val overlay = overlayIntent(context)
            for (row in 0 until GRID) {
                for (col in 0 until GRID) {
                    val cellId = context.resources.getIdentifier("cell_${row}_$col", "id", context.packageName)
                    if (cellId == 0) continue
                    val x = (col + 0.5f) / GRID * widthPx
                    val y = (row + 0.5f) / GRID * heightPx
                    views.setOnClickPendingIntent(cellId, actionFor(context, layout, settings, x, y) ?: overlay)
                }
            }
            return views
        }

        /** What a tap at (x, y) does, or null for "open the overlay". */
        private fun actionFor(context: Context, layout: WheelRenderer.Layout, settings: LauncherSettings, x: Float, y: Float): PendingIntent? {
            val ring = layout.ringAt(x, y) ?: return null
            val selected = settings.selectedSlot(ring)
            val slot = layout.slotAt(ring, selected, x, y)
            val key = settings.appKeyAt(SlotId(ring, slot)) ?: return null
            // One tap opens the app and records it as the ring's selection.
            val intent = Intent(context, WidgetLaunchActivity::class.java).apply {
                data = Uri.parse("wheel://launch/${ring.name}/$slot")
                putExtra(WidgetLaunchActivity.EXTRA_KEY, key)
                putExtra(WidgetLaunchActivity.EXTRA_RING, ring.name)
                putExtra(WidgetLaunchActivity.EXTRA_SLOT, slot)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            return PendingIntent.getActivity(
                context,
                ring.ordinal * 100 + slot,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun overlayIntent(context: Context): PendingIntent {
            val intent = Intent(context, WheelOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun vibrate(context: Context) {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            runCatching {
                vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}
