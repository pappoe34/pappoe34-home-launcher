package com.pappoe34.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.pappoe34.launcher.model.AppInfo

/** Renders an app's launcher icon at [size]; the bitmap is cached per app and size. */
@Composable
fun AppIcon(app: AppInfo, size: Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val px = remember(density, size) { with(density) { size.roundToPx() }.coerceAtLeast(1) }
    val bitmap = remember(app.key, px) { app.icon.toBitmap(px, px).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = app.label,
        modifier = modifier.size(size),
    )
}
