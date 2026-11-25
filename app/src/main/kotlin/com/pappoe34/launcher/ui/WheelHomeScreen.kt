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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.pappoe34.launcher.ui.pages.WidgetPageScreen
import com.pappoe34.launcher.ui.sheets.AppPickerSheet
import com.pappoe34.launcher.ui.sheets.SettingsSheet
import com.pappoe34.launcher.ui.sheets.WidgetPickerSheet
import com.pappoe34.launcher.ui.wheel.DialPage
import com.pappoe34.launcher.ui.wheel.rememberRingStates
import com.pappoe34.launcher.ui.wheel.rememberWheelPalette
import com.pappoe34.launcher.ui.widgets.LocalWidgetBinder
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
    val widgetBinder = LocalWidgetBinder.current

    var pickerSlot by remember { mutableStateOf<SlotId?>(null) }
    var widgetPickerForPage by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDefaultStatus() }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshDefaultStatus()
    }
    val requestDefault = { runCatching { roleLauncher.launch(defaultLauncherIntent(context)) } }

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    // Page 0 is the dial, then one page per widget page, then the "add page" page.
    val pages = state.settings.pages
    val pageCount = pages.size + 2
    val pagerState = rememberPagerState { pageCount }

    // Back: collapse the drawer, else return to the dial, else swallow (launcher behaviour).
    BackHandler(enabled = true) {
        when {
            sheetState.currentValue == SheetValue.Expanded -> scope.launch { sheetState.partialExpand() }
            pagerState.currentPage != 0 -> scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    val launchApp: (AppInfo) -> Unit = { app ->
        if (!viewModel.launch(app)) {
            Toast.makeText(context, context.getString(R.string.launch_failed, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 56.dp,
        containerColor = Color.Transparent,
        sheetContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        sheetDragHandle = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle()
                if (sheetState.currentValue != SheetValue.Expanded) {
                    Text(
                        text = stringResource(R.string.drawer_handle),
                        style = MaterialTheme.typography.labelSmall,
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
                            state.settings.groupName(result.slot.ring),
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
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                ClockBar(color = palette.label, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))

                if (state.showDefaultPrompt) {
                    DefaultLauncherPrompt(
                        onSetDefault = { requestDefault() },
                        onDismiss = viewModel::dismissDefaultPrompt,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    when {
                        page == 0 -> DialPage(
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
                        )
                        page <= pages.size -> {
                            val widgetPage = pages[page - 1]
                            WidgetPageScreen(
                                page = widgetPage,
                                labelColor = palette.label,
                                onAddWidget = { widgetPickerForPage = widgetPage.id },
                                onRemoveWidget = viewModel::removeWidget,
                                onResizeWidget = viewModel::resizeWidget,
                                onRemovePage = {
                                    viewModel.removePage(widgetPage.id)
                                    scope.launch { pagerState.animateScrollToPage((page - 1).coerceAtLeast(0)) }
                                },
                            )
                        }
                        else -> AddPagePlaceholder(labelColor = palette.label, onAddPage = viewModel::addPage)
                    }
                }

                PageDots(count = pageCount, current = pagerState.currentPage, color = palette.label)
            }
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

    widgetPickerForPage?.let { pageId ->
        WidgetPickerSheet(
            onPick = { info ->
                widgetPickerForPage = null
                widgetBinder.addWidget(info) { appWidgetId -> viewModel.placeWidget(pageId, appWidgetId) }
            },
            onDismiss = { widgetPickerForPage = null },
        )
    }

    if (settingsOpen) {
        SettingsSheet(
            settings = state.settings,
            isDefaultLauncher = state.isDefaultLauncher,
            onTheme = viewModel::setTheme,
            onDepth3d = viewModel::setDepth3d,
            onHaptics = viewModel::setHaptics,
            onSound = viewModel::setSound,
            onBlur = viewModel::setBlur,
            onGroupName = viewModel::setGroupName,
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

@Composable
private fun AddPagePlaceholder(labelColor: Color, onAddPage: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledTonalButton(onClick = onAddPage) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_page))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.add_page_hint), color = labelColor.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val isCurrent = index == current
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (isCurrent) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (isCurrent) 0.95f else 0.4f)),
            )
        }
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
private fun ClockBar(color: Color, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text = now.format(timeFormatter),
            fontSize = 34.sp,
            fontWeight = FontWeight.Light,
            color = color,
            lineHeight = 36.sp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = now.format(dateFormatter),
            style = MaterialTheme.typography.titleSmall,
            color = color.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 5.dp),
        )
    }
}

@Composable
private fun DefaultLauncherPrompt(onSetDefault: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.set_default_title),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
            TextButton(onClick = onSetDefault) { Text(stringResource(R.string.set_default_action)) }
        }
    }
}
