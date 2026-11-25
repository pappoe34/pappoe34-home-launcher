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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    val diameter = diameterPx
    val center = Offset(diameterPx / 2f, diameterPx / 2f)
    private val pad = with(density) { 6.dp.toPx() }

    val iconDp: Map<Ring, Dp> = mapOf(Ring.INNER to 36.dp, Ring.MIDDLE to 42.dp, Ring.OUTER to 46.dp)
    val iconPx: Map<Ring, Float> = iconDp.mapValues { with(density) { it.value.toPx() } }
    val trackWidthPx: Map<Ring, Float> = iconPx.mapValues { it.value + with(density) { 12.dp.toPx() } }

    val radiusPx: Map<Ring, Float>
    val hubRadiusPx: Float
    val hubDp: Dp
    /** Outer edge of the outer track; touches beyond it are not ours. */
    val outerEdgePx: Float

    init {
        val outer = diameterPx / 2f - trackWidthPx.getValue(Ring.OUTER) / 2f - pad
        radiusPx = mapOf(Ring.OUTER to outer, Ring.MIDDLE to outer * 0.68f, Ring.INNER to outer * 0.38f)
        hubRadiusPx = (radiusPx.getValue(Ring.INNER) - trackWidthPx.getValue(Ring.INNER) / 2f - with(density) { 6.dp.toPx() })
            .coerceAtLeast(with(density) { 22.dp.toPx() })
        hubDp = with(density) { (hubRadiusPx * 2).toDp() }
        outerEdgePx = outer + trackWidthPx.getValue(Ring.OUTER) / 2f + pad
    }

    /** Which ring a touch at [offsetFromCenter] grabs; null in the hub or outside the dial. */
    fun ringAt(offsetFromCenter: Offset): Ring? {
        val r = offsetFromCenter.getDistance()
        if (r < hubRadiusPx || r > outerEdgePx) return null
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

private const val DEG = (PI / 180.0).toFloat()

@Composable
fun Dial(
    rings: Map<Ring, List<AppInfo?>>,
    states: Map<Ring, RingState>,
    palette: WheelPalette,
    depth3d: Boolean,
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
            .graphicsLayer {
                if (depth3d) {
                    rotationX = 28f
                    cameraDistance = 16f * this.density
                    // Pull the tilted disc up slightly so it stays visually centred.
                    translationY = -size.height * 0.03f
                }
            }
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
                            val arcPx = abs(accumulated) * DEG * radius
                            if (arcPx > viewConfiguration.touchSlop) {
                                dragging = true
                                state.beginDrag()
                                onRingTouched(ring)
                            }
                        }
                        // Claim every move on a ring so the surrounding pager never steals a spin.
                        change.consume()
                        if (dragging) {
                            val step = accumulated - applied
                            applied = accumulated
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
            depth3d = depth3d,
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
    depth3d: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onSlotLongPress: (SlotId) -> Unit,
    onEmptySlotClick: (SlotId) -> Unit,
    onHubClick: () -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val diameterPx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val geometry = remember(density, diameterPx) { DialGeometry(density, diameterPx) }

        Canvas(Modifier.fillMaxSize()) {
            drawRings(geometry, states, palette, depth3d)
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
                            val a = (state.angle.value + index * state.detent - 90f) * DEG
                            IntOffset((radius * cos(a)).roundToInt(), (radius * sin(a)).roundToInt())
                        }
                        .graphicsLayer {
                            // Swell smoothly as the slot approaches its ring's selection window.
                            val t = 1f - (state.distanceToWindow(index) / state.detent).coerceIn(0f, 1f)
                            val s = 1f + 0.28f * t
                            scaleX = s
                            scaleY = s
                        }
                        .size(iconDp)
                        .then(if (depth3d) Modifier.shadow(6.dp, CircleShape, clip = false) else Modifier)
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
                .then(if (depth3d) Modifier.shadow(10.dp, CircleShape, clip = false) else Modifier)
                .clip(CircleShape)
                .background(
                    if (depth3d) {
                        Brush.radialGradient(
                            listOf(palette.hub.lighten(0.18f), palette.hub, palette.hub.darken(0.25f)),
                            center = Offset(geometry.hubRadiusPx * 0.7f, geometry.hubRadiusPx * 0.7f),
                            radius = geometry.hubRadiusPx * 1.6f,
                        )
                    } else {
                        Brush.linearGradient(listOf(palette.hub, palette.hub))
                    },
                )
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
                modifier = Modifier.size(geometry.hubDp * 0.45f),
            )
        }
    }
}

private fun DrawScope.drawRings(
    geometry: DialGeometry,
    states: Map<Ring, RingState>,
    palette: WheelPalette,
    depth3d: Boolean,
) {
    val center = geometry.center
    val rimStroke = Stroke(width = 1.5.dp.toPx())

    if (depth3d) {
        // Soft contact shadow under the whole disc.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                center = center + Offset(0f, 10.dp.toPx()),
                radius = geometry.outerEdgePx * 1.05f,
            ),
            radius = geometry.outerEdgePx * 1.05f,
            center = center + Offset(0f, 10.dp.toPx()),
        )
    }

    Ring.entries.forEach { ring ->
        val style = palette.rings.getValue(ring)
        val state = states.getValue(ring)
        val r = geometry.radiusPx.getValue(ring)
        val w = geometry.trackWidthPx.getValue(ring)

        drawCircle(color = style.glow, radius = r, center = center, style = Stroke(width = w + 22.dp.toPx()))
        drawCircle(brush = style.track(center), radius = r, center = center, style = Stroke(width = w))

        if (depth3d) {
            // Tube shading: dark at both edges, lit along the crown, so the track reads as a torus.
            val extent = r + w / 2f
            val inner = (r - w / 2f) / extent
            drawCircle(
                brush = Brush.radialGradient(
                    inner to Color.Black.copy(alpha = 0.45f),
                    (inner + (1f - inner) * 0.42f) to Color.White.copy(alpha = 0.22f),
                    (inner + (1f - inner) * 0.62f) to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.5f),
                    center = center,
                    radius = extent,
                ),
                radius = r,
                center = center,
                style = Stroke(width = w),
            )
            // Specular sweep from the upper left.
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.68f to Color.White.copy(alpha = 0.16f),
                    0.78f to Color.Transparent,
                    1f to Color.Transparent,
                    center = center,
                ),
                radius = r,
                center = center,
                style = Stroke(width = w),
            )
            // Bevelled rims: light top edge, dark bottom edge.
            drawCircle(color = Color.White.copy(alpha = 0.35f), radius = r + w / 2f, center = center, style = rimStroke)
            drawCircle(color = Color.Black.copy(alpha = 0.55f), radius = r - w / 2f, center = center, style = rimStroke)
        }
        drawCircle(color = style.rim, radius = r + w / 2f, center = center, style = rimStroke)
        drawCircle(color = style.rim, radius = r - w / 2f, center = center, style = rimStroke)

        // Notches between slots rotate with the ring.
        rotate(degrees = state.angle.value, pivot = center) {
            val inset = 4.dp.toPx()
            for (i in 0 until ring.slotCount) {
                val a = (i * state.detent + state.detent / 2f - 90f) * DEG
                val dir = Offset(cos(a), sin(a))
                drawLine(
                    color = style.notch,
                    start = center + dir * (r - w / 2f + inset),
                    end = center + dir * (r + w / 2f - inset),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        // Selection window: a lit sector on this ring, one slot wide, with bracket ticks.
        val sweep = state.detent * 0.92f
        val start = ring.windowAngle - sweep / 2f
        val windowRect = Rect(center - Offset(r, r), Size(r * 2, r * 2))
        drawArc(
            color = style.rim.copy(alpha = 0.28f),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = windowRect.topLeft,
            size = windowRect.size,
            style = Stroke(width = w),
        )
        drawArc(
            color = style.rim,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Rect(center - Offset(r + w / 2f, r + w / 2f), Size((r + w / 2f) * 2, (r + w / 2f) * 2)).topLeft,
            size = Size((r + w / 2f) * 2, (r + w / 2f) * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
            color = style.rim,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Rect(center - Offset(r - w / 2f, r - w / 2f), Size((r - w / 2f) * 2, (r - w / 2f) * 2)).topLeft,
            size = Size((r - w / 2f) * 2, (r - w / 2f) * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        // Pointer nub just outside the window.
        val tipDir = Offset(cos(ring.windowAngle * DEG), sin(ring.windowAngle * DEG))
        val tip = center + tipDir * (r + w / 2f + 3.dp.toPx())
        val side = Offset(-tipDir.y, tipDir.x)
        val marker = Path().apply {
            moveTo(tip.x, tip.y)
            val back = tip + tipDir * 8.dp.toPx()
            lineTo(back.x + side.x * 5.dp.toPx(), back.y + side.y * 5.dp.toPx())
            lineTo(back.x - side.x * 5.dp.toPx(), back.y - side.y * 5.dp.toPx())
            close()
        }
        drawPath(marker, color = style.rim)
    }
}

private fun Color.lighten(amount: Float): Color =
    Color(
        red = (red + (1f - red) * amount).coerceIn(0f, 1f),
        green = (green + (1f - green) * amount).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * amount).coerceIn(0f, 1f),
        alpha = alpha,
    )

private fun Color.darken(amount: Float): Color =
    Color(red = red * (1f - amount), green = green * (1f - amount), blue = blue * (1f - amount), alpha = alpha)
