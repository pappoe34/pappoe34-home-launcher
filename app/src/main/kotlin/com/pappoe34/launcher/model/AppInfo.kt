package com.pappoe34.launcher.model

import android.content.ComponentName
import android.graphics.drawable.Drawable

/** One launchable activity as shown on the home screen. */
data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable,
) {
    val componentName: ComponentName get() = ComponentName(packageName, activityName)

    /** Stable identity for lists and keys; icon is deliberately excluded. */
    val key: String get() = "$packageName/$activityName"

    override fun equals(other: Any?): Boolean = other is AppInfo && other.key == key && other.label == label
    override fun hashCode(): Int = key.hashCode()
}
