package com.pappoe34.launcher.widget

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import com.pappoe34.launcher.LauncherApplication
import com.pappoe34.launcher.model.Ring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Invisible trampoline for widget taps: records which slot was tapped as the ring's
 * selection, opens the app, and finishes. One tap, both effects.
 */
class WidgetLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra(EXTRA_KEY)
        val ring = intent.getStringExtra(EXTRA_RING)?.let { name -> Ring.entries.firstOrNull { it.name == name } }
        val slot = intent.getIntExtra(EXTRA_SLOT, -1)

        if (key != null) {
            val slash = key.indexOf('/')
            if (slash > 0) {
                val launch = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(key.substring(0, slash), key.substring(slash + 1))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                runCatching { startActivity(launch) }
            }
        }

        if (ring != null && slot >= 0) {
            val app = application as LauncherApplication
            scope.launch {
                app.preferences.setSelected(ring, slot)
                WheelWidgetProvider.updateAll(app)
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_RING = "ring"
        const val EXTRA_SLOT = "slot"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
