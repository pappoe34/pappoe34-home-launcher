package com.pappoe34.launcher.ui.sheets

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.pappoe34.launcher.LauncherApplication
import com.pappoe34.launcher.R
import com.pappoe34.launcher.ui.SearchField
import com.pappoe34.launcher.ui.sheetBottomPadding

/** Lists every home-screen widget provider on the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(onPick: (AppWidgetProviderInfo) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val host = remember(context) { (context.applicationContext as LauncherApplication).widgetHost }
    val pm = context.packageManager
    val density = LocalDensity.current
    val providers = remember { host.installedProviders() }
    var query by remember { mutableStateOf("") }
    val filtered = remember(providers, query) {
        val q = query.trim()
        if (q.isEmpty()) providers
        else providers.filter { host.appLabel(it).contains(q, true) || it.loadLabel(pm).contains(q, true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxHeight(0.85f).padding(horizontal = 16.dp).sheetBottomPadding()) {
            Text(stringResource(R.string.pick_widget), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            SearchField(query = query, onQueryChange = { query = it }, onClear = { query = "" }, onSubmit = {})
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(filtered, key = { it.provider.flattenToString() }) { info ->
                    val icon = remember(info.provider) {
                        runCatching { info.loadIcon(context, context.resources.displayMetrics.densityDpi).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(info) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info.loadLabel(pm), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = host.appLabel(info) + " · " + widgetSizeText(info, density.density),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun widgetSizeText(info: AppWidgetProviderInfo, density: Float): String {
    val w = (info.minWidth / density).toInt()
    val h = (info.minHeight / density).toInt()
    return "${w}×${h} dp"
}
