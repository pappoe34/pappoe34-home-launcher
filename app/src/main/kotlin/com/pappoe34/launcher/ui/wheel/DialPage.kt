package com.pappoe34.launcher.ui.wheel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pappoe34.launcher.R
import com.pappoe34.launcher.model.AppInfo
import com.pappoe34.launcher.model.Ring
import com.pappoe34.launcher.model.SlotId
import com.pappoe34.launcher.ui.LauncherUiState
import com.pappoe34.launcher.ui.components.AppIcon
import kotlinx.coroutines.launch

/** The dial filling the available width, with one selection chip per ring group underneath. */
@Composable
fun DialPage(
    state: LauncherUiState,
    ringStates: Map<Ring, RingState>,
    palette: WheelPalette,
    onLaunch: (AppInfo) -> Unit,
    onSlotLongPress: (SlotId) -> Unit,
    onHubClick: () -> Unit,
    onDetent: (Ring) -> Unit,
    onSettled: (Ring, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    // Keep the rings in step with the persisted selection (changed by the widget or another screen).
    LaunchedEffect(state.isLoading, state.settings.selected) {
        if (state.isLoading) return@LaunchedEffect
        Ring.entries.forEach { ring ->
            val ringState = ringStates.getValue(ring)
            val wanted = state.settings.selectedSlot(ring)
            if (!ringState.angle.isRunning && ringState.selectedSlot() != wanted) ringState.snapToSlot(wanted)
        }
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Dial(
                rings = state.rings,
                states = ringStates,
                palette = palette,
                depth3d = state.settings.depth3d,
                onLaunch = onLaunch,
                onSlotLongPress = onSlotLongPress,
                onEmptySlotClick = onSlotLongPress,
                onHubClick = onHubClick,
                onRingTouched = {},
                onDetent = onDetent,
                onSettled = onSettled,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Ring.entries.forEach { ring ->
                val ringState = ringStates.getValue(ring)
                val selected by remember(ring, state) {
                    derivedStateOf { state.rings[ring]?.getOrNull(ringState.selectedSlot()) }
                }
                GroupChip(
                    name = state.settings.groupName(ring),
                    accent = palette.rings.getValue(ring).rim,
                    app = selected,
                    labelColor = palette.label,
                    onClick = {
                        val app = selected
                        if (app != null) onLaunch(app) else scope.launch { ringState.bringToWindow(0) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupChip(
    name: String,
    accent: Color,
    app: AppInfo?,
    labelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.35f),
        border = BorderStroke(1.5.dp, accent),
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app != null) {
                AppIcon(app = app, size = 30.dp)
            } else {
                Box(Modifier.size(30.dp).clip(CircleShape).background(accent.copy(alpha = 0.3f)))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app?.label ?: stringResource(R.string.empty_slot),
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
