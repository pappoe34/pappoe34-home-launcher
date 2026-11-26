package com.pappoe34.launcher.ui

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pappoe34.launcher.LauncherApplication
import com.pappoe34.launcher.R
import com.pappoe34.launcher.model.AppInfo
import com.pappoe34.launcher.model.SlotId
import com.pappoe34.launcher.ui.sheets.AppPickerSheet
import com.pappoe34.launcher.ui.sheets.SettingsSheet
import com.pappoe34.launcher.ui.wheel.DialPage
import com.pappoe34.launcher.ui.wheel.rememberRingStates
import com.pappoe34.launcher.ui.wheel.rememberWheelPalette

/** The dial alone over a dimmed scrim. Tapping outside the rings, or launching an app, dismisses it. */
@Composable
fun OverlayWheelScreen(onDismiss: () -> Unit, viewModel: LauncherViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val palette = rememberWheelPalette(state.settings.theme)
    val ringStates = rememberRingStates()
    val tickPlayer = remember(context) { (context.applicationContext as LauncherApplication).tickPlayer }

    var pickerSlot by remember { mutableStateOf<SlotId?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    val launchApp: (AppInfo) -> Unit = { app ->
        if (viewModel.launch(app)) {
            onDismiss()
        } else {
            Toast.makeText(context, context.getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.75f))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Text(
                text = stringResource(R.string.overlay_hint),
                style = MaterialTheme.typography.labelMedium,
                color = palette.label.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            DialPage(
                state = state,
                ringStates = ringStates,
                palette = palette,
                onLaunch = launchApp,
                onSlotLongPress = { pickerSlot = it },
                onHubClick = { settingsOpen = true },
                onDetent = {
                    val settings = viewModel.state.value.settings
                    if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (settings.sound) tickPlayer.play()
                },
                onSettled = viewModel::setSelected,
                modifier = Modifier.weight(1f).padding(bottom = 16.dp),
            )
        }
    }

    pickerSlot?.let { slot ->
        AppPickerSheet(
            slot = slot,
            groupName = state.settings.groupName(slot.ring),
            current = state.appAt(slot),
            apps = state.apps,
            onPick = { app ->
                viewModel.assign(slot, app)
                pickerSlot = null
            },
            onDismiss = { pickerSlot = null },
        )
    }

    if (settingsOpen) {
        SettingsSheet(
            settings = state.settings,
            isDefaultLauncher = true,
            onTheme = viewModel::setTheme,
            onDepth3d = viewModel::setDepth3d,
            onHaptics = viewModel::setHaptics,
            onSound = viewModel::setSound,
            onBlur = viewModel::setBlur,
            onGroupName = viewModel::setGroupName,
            onSetDefault = {},
            onAutoFill = viewModel::autoFillWheels,
            onClearWheels = viewModel::clearWheels,
            onDismiss = { settingsOpen = false },
        )
    }
}
