package com.pappoe34.launcher.ui.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.pappoe34.launcher.model.Ring
import kotlin.math.roundToInt

/**
 * Rotation state of one ring, in degrees (unbounded).
 *
 * Slot `i` sits at `angle + i * detent - 90` degrees (0 = 3 o'clock, clockwise). The ring
 * snaps so that one slot is centred in its selection window at [Ring.windowAngle].
 */
@Stable
class RingState(val ring: Ring) {

    val angle = Animatable(0f)
    val detent: Float get() = ring.detentDegrees

    /** Rotation offset at which slot 0 is centred in the window. */
    private val windowOffset: Float get() = ring.windowAngle + 90f

    /** Angle accumulated during the current drag; kept outside the animation so drags are not lossy. */
    private var dragAngle = 0f

    fun beginDrag() {
        dragAngle = angle.value
    }

    suspend fun dragBy(deltaDegrees: Float) {
        dragAngle += deltaDegrees
        angle.snapTo(dragAngle)
    }

    /** Fling with [velocity] in degrees/second, landing with a slot centred in the window. */
    suspend fun fling(velocity: Float, decay: DecayAnimationSpec<Float>) {
        val projected = decay.calculateTargetValue(angle.value, velocity)
        angle.animateTo(
            targetValue = nearestDetent(projected),
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
            initialVelocity = velocity,
        )
    }

    /** Snap to the nearest detent after a drag released without momentum. */
    suspend fun settle() {
        angle.animateTo(nearestDetent(angle.value), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }

    /** Rotate so that [slot] sits in the window, taking the short way round. */
    suspend fun bringToWindow(slot: Int) {
        val current = angle.value
        val base = windowOffset - slot * detent
        val turns = ((current - base) / 360f).roundToInt()
        angle.animateTo(base + turns * 360f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }

    private fun nearestDetent(value: Float): Float = ((value - windowOffset) / detent).roundToInt() * detent + windowOffset

    /** Index of the slot currently sitting in the selection window. */
    fun selectedSlot(): Int {
        val steps = ((windowOffset - angle.value) / detent).roundToInt()
        return Math.floorMod(steps, ring.slotCount)
    }

    /** Angular distance (0..180) between slot [index] and the window centre. */
    fun distanceToWindow(index: Int): Float {
        val a = angle.value + index * detent - windowOffset
        val m = ((a % 360f) + 360f) % 360f
        return if (m > 180f) 360f - m else m
    }
}

@Composable
fun rememberRingStates(): Map<Ring, RingState> = remember { Ring.entries.associateWith { RingState(it) } }
