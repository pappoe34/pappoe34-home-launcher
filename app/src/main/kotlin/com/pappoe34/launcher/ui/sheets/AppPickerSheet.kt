package com.pappoe34.launcher.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pappoe34.launcher.R
import com.pappoe34.launcher.model.AppInfo
import com.pappoe34.launcher.model.SlotId
import com.pappoe34.launcher.ui.SearchField
import com.pappoe34.launcher.ui.components.AppIcon
import com.pappoe34.launcher.ui.sheetBottomPadding

/** Pick an app for [slot]; [current] is whatever already sits there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    slot: SlotId,
    current: AppInfo?,
    apps: List<AppInfo>,
    onPick: (AppInfo?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxHeight(0.85f).padding(horizontal = 16.dp).sheetBottomPadding()) {
            Text(
                text = stringResource(R.string.pick_app_for_slot, slot.ring.name.lowercase().replaceFirstChar { it.titlecase() }, slot.index + 1),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            SearchField(query = query, onQueryChange = { query = it }, onClear = { query = "" }, onSubmit = {
                filtered.firstOrNull()?.let(onPick)
            })
            if (current != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.clear_slot)) }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            LazyColumn {
                items(filtered, key = { it.key }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app = app, size = 40.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (app.key == current?.key) androidx.compose.ui.text.font.FontWeight.Bold else null,
                        )
                    }
                }
            }
        }
    }
}
