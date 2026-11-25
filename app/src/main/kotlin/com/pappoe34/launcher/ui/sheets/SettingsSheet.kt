package com.pappoe34.launcher.ui.sheets

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pappoe34.launcher.R
import com.pappoe34.launcher.model.LauncherSettings
import com.pappoe34.launcher.model.Ring
import com.pappoe34.launcher.model.WheelTheme
import com.pappoe34.launcher.ui.sheetBottomPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: LauncherSettings,
    isDefaultLauncher: Boolean,
    onTheme: (WheelTheme) -> Unit,
    onDepth3d: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onBlur: (Boolean) -> Unit,
    onGroupName: (Ring, String) -> Unit,
    onSetDefault: () -> Unit,
    onAutoFill: () -> Unit,
    onClearWheels: () -> Unit,
    onDismiss: () -> Unit,
) {
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .sheetBottomPadding(),
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WheelTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { onTheme(theme) },
                        label = { Text(themeLabel(theme)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            ToggleRow(stringResource(R.string.depth_3d), stringResource(R.string.depth_3d_desc), settings.depth3d, onDepth3d)
            ToggleRow(stringResource(R.string.haptic_ticks), stringResource(R.string.haptic_ticks_desc), settings.haptics, onHaptics)
            ToggleRow(stringResource(R.string.spin_sound), stringResource(R.string.spin_sound_desc), settings.sound, onSound)
            ToggleRow(
                title = stringResource(R.string.blur_wallpaper),
                subtitle = if (blurSupported) stringResource(R.string.blur_wallpaper_desc) else stringResource(R.string.blur_unsupported),
                checked = settings.blur && blurSupported,
                enabled = blurSupported,
                onChange = onBlur,
            )
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.group_names), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Ring.entries.forEach { ring ->
                GroupNameField(
                    ring = ring,
                    value = settings.groupName(ring),
                    onChange = { onGroupName(ring, it) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAutoFill) { Text(stringResource(R.string.auto_fill_wheels)) }
                OutlinedButton(onClick = onClearWheels) { Text(stringResource(R.string.clear_wheels)) }
            }
            if (!isDefaultLauncher) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onSetDefault, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.set_default_action))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GroupNameField(ring: Ring, value: String, onChange: (String) -> Unit) {
    var text by remember(ring) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.take(16)
            onChange(text)
        },
        singleLine = true,
        label = { Text(ringLabel(ring)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ringLabel(ring: Ring): String = when (ring) {
    Ring.INNER -> stringResource(R.string.ring_inner)
    Ring.MIDDLE -> stringResource(R.string.ring_middle)
    Ring.OUTER -> stringResource(R.string.ring_outer)
}

@Composable
private fun themeLabel(theme: WheelTheme): String = when (theme) {
    WheelTheme.METALLIC -> stringResource(R.string.theme_metallic)
    WheelTheme.NEON -> stringResource(R.string.theme_neon)
    WheelTheme.GLASS -> stringResource(R.string.theme_glass)
    WheelTheme.DYNAMIC -> stringResource(R.string.theme_dynamic)
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
