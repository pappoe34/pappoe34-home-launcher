package com.pappoe34.launcher.ui.wheel

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pappoe34.launcher.model.AppInfo
import com.pappoe34.launcher.model.Ring
import com.pappoe34.launcher.model.SlotId
import com.pappoe34.launcher.ui.components.AppIcon
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pixel geometry of the dial for a given square size. */
class DialGeometry(density: Density, diameterPx: Float) {
    val center = Offset(diameterPx / 2f, diameterPx / 2f)
    private val pad = with(density) { 10.dp.toPx() }

    val iconDp: Map<Ring, Dp> = mapOf(Ring.INNER to 32.dp, Ring.MIDDLE to 38.dp, Ring.OUTER to 42.dp)
    val iconPx: Map<Ring, Float> = iconDp.mapValues { with(density) { it.value.toPx() } }
    val trackWidthPx: Map<Ring, Float> = iconPx.mapValues { it.value + with(density) { 14.dp.toPx() } }

    val radiusPx: Map<Ring, Float>
    val hubRadiusPx: Float
    val hubDp: Dp

    init {
        val outer = diameterPx / 2f - iconPx.getValue(Ring.OUTER) / 2f - pad
        radiusPx = mapOf(Ring.OUTER to outer, Ring.MIDDLE to outer * 0.68f, Ring.INNER to outer * 0.38f)
        hubRadiusPx = (radiusPx.getValue(Ring.INNER) - trackWidthPx.getValue(Ring.INNER) / 2f - with(density) { 6.dp.toPx() })
            .coerceAtLeast(with(density) { 20.dp.toPx() })
        hubDp = with(density) { (hubRadiusPx * 2).toDp() }
    }

    /** Which ring a touch at [offsetFromCenter] grabs; null inside the hub. */
    fun ringAt(offsetFromCenter: Offset): Ring? {
        val r = offsetFromCenter.getDistance()
        if (r < hubRadiusPx) return null
        val inner = radiusPx.getValue(Ring.INNER)
        val middle = radiusPx.getValue(Ring.MIDDLE)
        val outer = radiusPx.getValue(Ring.OUTER)
        return when {
            r < (inner + middle) / 2f -> Ring.INNER
            r < (middle + outer) / 2f -> Ring.MIDDLE
            else -> Ring.OUTER
        }
    }
}

/** Degrees clockwise from 3 o'clock, as Android canvas angles go. */
private fun angleOf(v: Offset): Float = (atan2(v.y, v.x) * 180.0 / PI).toFloat()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Dial(
    rings: Map<Ring, List<AppInfo?>>,
    states: Map<Ring, RingState>,
    palette: WheelPalette,
    onLaunch: (AppInfo) -> Unit,
    onSlotLongPress: (SlotId) -> Unit,
    onEmptySlotClick: (SlotId) -> Unit,
    onHubClick: () -> Unit,
    onRingTouched: (Ring) -> Unit,
    onDetent: (Ring) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = 1.6f) }

    // Haptic / sound tick each time a ring crosses a detent.
    val currentOnDetent by rememberUpdatedState(onDetent)
    states.forEach { (ring, state) ->
        LaunchedEffect(state) {
            snapshotFlow { floor(state.angle.value / state.detent).toInt() }
                .drop(1)
                .collect { currentOnDetent(ring) }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(states) {
                val geometry = DialGeometry(density, min(size.width, size.height).toFloat())
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val ring = geometry.ringAt(down.position - geometry.center) ?: return@awaitEachGesture
                    val state = states.getValue(ring)
                    val radius = (down.position - geometry.center).getDistance().coerceAtLeast(1f)
                    val tracker = VelocityTracker1D(isDataDifferential = false)
                    var previous = angleOf(down.position - geometry.center)
                    var accumulated = 0f
                    var applied = 0f
                    var dragging = false
                    tracker.addDataPoint(down.uptimeMillis, 0f)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val current = angleOf(change.position - geometry.center)
                        var delta = current - previous
                        if (delta > 180f) delta -= 360f else if (delta < -180f) delta += 360f
                        previous = current
                        accumulated += delta
                        tracker.addDataPoint(change.uptimeMillis, accumulated)

                        if (!dragging) {
                            val arcPx = abs(accumulated) * (PI.toFloat() / 180f) * radius
                            if (arcPx > viewConfiguration.touchSlop) {
                                dragging = true
                                state.beginDrag()
                                onRingTouched(ring)
                            }
                        }
                        if (dragging) {
                            val step = accumulated - applied
                            applied = accumulated
                            change.consume()
                            scope.launch { state.dragBy(step) }
                        }
                    }
                    if (dragging) {
                        val velocity = tracker.calculateVelocity()
                        scope.launch {
                            if (abs(velocity) > 40f) state.fling(velocity, decay) else state.settle()
                        }
                    }
                }
            },
    ) {
        DialContent(
            rings = rings,
            states = states,
            palette = palette,
            onLaunch = onLaunch,
            onSlotLongPress = onSlotLongPress,
            onEmptySlotClick = onEmptySlotClick,
            onHubClick = onHubClick,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialContent(
    rings: Map<Ring, List<AppInfo?>>,
    states: Map<Ring, RingState>,
    palette: WheelPalette,
    onLaunch: (AppInfo) -> Unit,
    onSlotLongPress: (SlotId) -> Unit,
    onEmptySlotClick: (SlotId) -> Unit,
    onHubClick: () -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val diameterPx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val geometry = remember(density, diameterPx) { DialGeometry(density, diameterPx) }

        // Ring tracks, rims, notches and the top marker.
        Canvas(Modifier.fillMaxSize()) {
            val center = geometry.center
            val rimStroke = Stroke(width = 1.5.dp.toPx())
            Ring.entries.forEach { ring ->
                val style = palette.rings.getValue(ring)
                val r = geometry.radiusPx.getValue(ring)
                val w = geometry.trackWidthPx.getValue(ring)
                drawCircle(color = style.glow, radius = r, center = center, style = Stroke(width = w + 22.dp.toPx()))
                drawCircle(brush = style.track(center), radius = r, center = center, style = Stroke(width = w))
                drawCircle(color = style.rim, radius = r + w / 2f, center = center, style = rimStroke)
                drawCircle(color = style.rim, radius = r - w / 2f, center = center, style = rimStroke)

                val state = states.getValue(ring)
                rotate(degrees = state.angle.value, pivot = center) {
                    val inset = 5.dp.toPx()
                    for (i in 0 until ring.slotCount) {
                        val a = (i * state.detent + state.detent / 2f - 90f) * (PI.toFloat() / 180f)
                        val dir = Offset(cos(a), sin(a))
                        drawLine(
                            color = style.notch,
                            start = center + dir * (r - w / 2f + inset),
                            end = center + dir * (r + w / 2f - inset),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }
            }
            // 12 o'clock marker above the outer ring.
            val outerEdge = geometry.radiusPx.getValue(Ring.OUTER) + geometry.trackWidthPx.getValue(Ring.OUTER) / 2f
            val tip = Offset(center.x, center.y - outerEdge + 2.dp.toPx())
            val marker = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - 6.dp.toPx(), tip.y - 9.dp.toPx())
                lineTo(tip.x + 6.dp.toPx(), tip.y - 9.dp.toPx())
                close()
            }
            drawPath(marker, color = palette.label)
        }

        // App icons, kept upright and positioned by polar coordinates read at layout time.
        Ring.entries.forEach { ring ->
            val state = states.getValue(ring)
            val radius = geometry.radiusPx.getValue(ring)
            val iconDp = geometry.iconDp.getValue(ring)
            val apps = rings[ring].orEmpty()
            for (index in 0 until ring.slotCount) {
                val slot = SlotId(ring, index)
                val app = apps.getOrNull(index)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset {
                            val a = (state.angle.value + index * state.detent - 90f) * (PI.toFloat() / 180f)
                            IntOffset((radius * cos(a)).roundToInt(), (radius * sin(a)).roundToInt())
                        }
                        .graphicsLayer {
                            // Scale up smoothly as the slot approaches 12 o'clock.
                            val a = Math.floorMod((state.angle.value + index * state.detent).roundToInt(), 360)
                            val dist = min(a, 360 - a).toFloat()
                            val t = (1f - (dist / state.detent).coerceIn(0f, 1f))
                            val s = 1f + 0.22f * t
                            scaleX = s
                            scaleY = s
                        }
                        .size(iconDp)
                        .clip(CircleShape)
                        .then(
                            if (app != null) {
                                Modifier.combinedClickable(
                                    onClick = { onLaunch(app) },
                                    onLongClick = { onSlotLongPress(slot) },
                                )
                            } else {
                                Modifier
                                    .background(palette.emptySlot)
                                    .combinedClickable(
                                        onClick = { onEmptySlotClick(slot) },
                                        onLongClick = { onSlotLongPress(slot) },
                                    )
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (app != null) {
                        AppIcon(app = app, size = iconDp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = palette.label.copy(alpha = 0.6f),
                            modifier = Modifier.size(iconDp / 2),
                        )
                    }
                }
            }
        }

        // Hub: opens settings.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(geometry.hubDp)
                .clip(CircleShape)
                .background(palette.hub)
                .drawBehind {
                    drawCircle(color = palette.hubContent.copy(alpha = 0.25f), style = Stroke(1.dp.toPx()))
                }
                .clickable(onClick = onHubClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = palette.hubContent,
                modifier = Modifier.size(geometry.hubDp * 0.5f),
            )
        }
    }
}
