package com.pappoe34.launcher.ui.pages

import android.appwidget.AppWidgetHostView
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pappoe34.launcher.LauncherApplication
import com.pappoe34.launcher.R
import com.pappoe34.launcher.model.PlacedWidget
import com.pappoe34.launcher.model.WidgetPage
import com.pappoe34.launcher.model.WidgetSize

/** One widget page: a vertical stack of hosted app widgets plus page actions. */
@Composable
fun WidgetPageScreen(
    page: WidgetPage,
    labelColor: Color,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onResizeWidget: (Int, WidgetSize) -> Unit,
    onRemovePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (page.widgets.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Widgets, contentDescription = null, tint = labelColor.copy(alpha = 0.6f))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.empty_page), color = labelColor.copy(alpha = 0.8f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(page.widgets, key = { it.appWidgetId }) { widget ->
                    HostedWidget(widget = widget, onRemove = { onRemoveWidget(widget.appWidgetId) }, onResize = { onResizeWidget(widget.appWidgetId, it) })
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onAddWidget) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.add_widget))
            }
            TextButton(onClick = onRemovePage) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.remove_page))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostedWidget(widget: PlacedWidget, onRemove: () -> Unit, onResize: (WidgetSize) -> Unit) {
    val context = LocalContext.current
    val host = remember(context) { (context.applicationContext as LauncherApplication).widgetHost }
    val widthDp = LocalConfiguration.current.screenWidthDp - 24
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
            modifier = Modifier
                .fillMaxWidth()
                .height(widget.size.heightDp.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            val hostView = remember(widget.appWidgetId) { host.createHostView(widget.appWidgetId) }
            if (hostView == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.widget_unavailable), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            (hostView.parent as? ViewGroup)?.removeView(hostView)
                            addView(hostView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        }
                    },
                    update = { hostView.reportSize(widthDp, widget.size.heightDp) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Long-press anywhere on the card edge area to manage; widgets eat most touches, so also
        // expose a tiny handle in the corner.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(onClick = { menuOpen = true }, onLongClick = { menuOpen = true })
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text("⋯", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            WidgetSize.entries.forEach { size ->
                DropdownMenuItem(
                    text = { Text(sizeLabel(size) + if (size == widget.size) "  ✓" else "") },
                    onClick = { menuOpen = false; onResize(size) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_widget)) },
                onClick = { menuOpen = false; onRemove() },
            )
        }
    }
}

@Composable
private fun sizeLabel(size: WidgetSize): String = when (size) {
    WidgetSize.SMALL -> stringResource(R.string.size_small)
    WidgetSize.MEDIUM -> stringResource(R.string.size_medium)
    WidgetSize.LARGE -> stringResource(R.string.size_large)
}

private fun AppWidgetHostView.reportSize(widthDp: Int, heightDp: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
    } else {
        @Suppress("DEPRECATION")
        updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
    }
}
