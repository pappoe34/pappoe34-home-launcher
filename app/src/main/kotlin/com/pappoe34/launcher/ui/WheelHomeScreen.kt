package com.pappoe34.launcher.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pappoe34.launcher.LauncherApplication
import com.pappoe34.launcher.R
import com.pappoe34.launcher.model.AppInfo
import com.pappoe34.launcher.model.Ring
import com.pappoe34.launcher.model.SlotId
import com.pappoe34.launcher.ui.sheets.AppPickerSheet
import com.pappoe34.launcher.ui.sheets.SettingsSheet
import com.pappoe34.launcher.ui.wheel.Dial
import com.pappoe34.launcher.ui.wheel.rememberRingStates
import com.pappoe34.launcher.ui.wheel.rememberWheelPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelHomeScreen(viewModel: LauncherViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val palette = rememberWheelPalette(state.settings.theme)
    val ringStates = rememberRingStates()
    val tickPlayer = remember(context) { (context.applicationContext as LauncherApplication).tickPlayer }

    var pickerSlot by remember { mutableStateOf<SlotId?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var activeRing by remember { mutableStateOf(Ring.OUTER) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDefaultStatus() }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshDefaultStatus()
    }
    val requestDefault = {
        runCatching { roleLauncher.launch(defaultLauncherIntent(context)) }
    }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    // Back collapses the drawer; otherwise it is swallowed, as a launcher should.
    BackHandler(enabled = true) {
        if (sheetState.currentValue == SheetValue.Expanded) scope.launch { sheetState.partialExpand() }
    }

    val launchApp: (AppInfo) -> Unit = { app ->
        if (!viewModel.launch(app)) {
            Toast.makeText(context, context.getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 64.dp,
        containerColor = Color.Transparent,
        sheetContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        sheetDragHandle = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle()
                if (sheetState.currentValue != SheetValue.Expanded) {
                    Text(
                        text = stringResource(R.string.drawer_handle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        sheetContent = {
            AppDrawer(
                apps = state.filteredApps,
                isLoading = state.isLoading,
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onClearQuery = viewModel::clearQuery,
                onLaunch = { app ->
                    launchApp(app)
                    scope.launch { sheetState.partialExpand() }
                },
                onAddToWheel = { app ->
                    val message = when (val result = viewModel.addToWheel(app)) {
                        is AddToWheelResult.Added -> context.getString(
                            R.string.added_to_wheel,
                            result.slot.ring.name.lowercase(),
                            result.slot.index + 1,
                        )
                        AddToWheelResult.AlreadyOnWheel -> context.getString(R.string.already_on_wheel, app.label)
                        AddToWheelResult.WheelsFull -> context.getString(R.string.wheels_full)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
                onAppInfo = viewModel::openAppInfo,
                onUninstall = viewModel::uninstall,
                modifier = Modifier.fillMaxHeight(0.92f).sheetBottomPadding(),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(palette.scrimTop, palette.scrimBottom)))
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                ClockHeader(color = palette.label)

                if (state.showDefaultPrompt) {
                    Spacer(Modifier.height(12.dp))
                    DefaultLauncherPrompt(onSetDefault = { requestDefault() }, onDismiss = viewModel::dismissDefaultPrompt)
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Dial(
                        rings = state.rings,
                        states = ringStates,
                        palette = palette,
                        onLaunch = launchApp,
                        onSlotLongPress = { slot -> pickerSlot = slot },
                        onEmptySlotClick = { slot -> pickerSlot = slot },
                        onHubClick = { settingsOpen = true },
                        onRingTouched = { ring -> activeRing = ring },
                        onDetent = {
                            val settings = viewModel.state.value.settings
                            if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            if (settings.sound) tickPlayer.play()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Name of whatever sits at 12 o'clock on the last ring touched.
                val focusedApp by remember(activeRing, state) {
                    derivedStateOf {
                        val index = ringStates.getValue(activeRing).focusedSlot()
                        state.rings[activeRing]?.getOrNull(index)
                    }
                }
                Text(
                    text = focusedApp?.label ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    pickerSlot?.let { slot ->
        AppPickerSheet(
            slot = slot,
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
            isDefaultLauncher = state.isDefaultLauncher,
            onTheme = viewModel::setTheme,
            onHaptics = viewModel::setHaptics,
            onSound = viewModel::setSound,
            onBlur = viewModel::setBlur,
            onSetDefault = {
                settingsOpen = false
                requestDefault()
            },
            onAutoFill = viewModel::autoFillWheels,
            onClearWheels = viewModel::clearWheels,
            onDismiss = { settingsOpen = false },
        )
    }
}

private fun defaultLauncherIntent(context: Context): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
    }
    return Intent(Settings.ACTION_HOME_SETTINGS)
}

@Composable
private fun ClockHeader(color: Color) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) }
    Column {
        Text(
            text = now.format(timeFormatter),
            fontSize = 48.sp,
            fontWeight = FontWeight.Light,
            color = color,
            lineHeight = 52.sp,
        )
        Text(
            text = now.format(dateFormatter),
            style = MaterialTheme.typography.titleSmall,
            color = color.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun DefaultLauncherPrompt(onSetDefault: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.set_default_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.set_default_body), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                TextButton(onClick = onSetDefault) { Text(stringResource(R.string.set_default_action)) }
            }
        }
    }
}
