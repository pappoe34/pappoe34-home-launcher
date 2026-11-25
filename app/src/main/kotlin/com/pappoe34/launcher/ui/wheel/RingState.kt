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

/** Rotation state of one ring, in degrees. Unbounded: 370 is one slot past 10. */
@Stable
class RingState(val ring: Ring) {

    val angle = Animatable(0f)
    val detent: Float get() = ring.detentDegrees

    /** Angle accumulated during the current drag; kept outside the animation so drags are not lossy. */
    private var dragAngle = 0f

    fun beginDrag() {
        dragAngle = angle.value
    }

    suspend fun dragBy(deltaDegrees: Float) {
        dragAngle += deltaDegrees
        angle.snapTo(dragAngle)
    }

    /** Fling with [velocity] in degrees/second, landing exactly on a detent. */
    suspend fun fling(velocity: Float, decay: DecayAnimationSpec<Float>) {
        val projected = decay.calculateTargetValue(angle.value, velocity)
        val target = (projected / detent).roundToInt() * detent
        angle.animateTo(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
            initialVelocity = velocity,
        )
    }

    /** Snap to the nearest detent after a drag released without momentum. */
    suspend fun settle() {
        val target = (angle.value / detent).roundToInt() * detent
        angle.animateTo(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }

    /** Index of the slot currently sitting at 12 o'clock. */
    fun focusedSlot(): Int {
        val steps = (-angle.value / detent).roundToInt()
        return Math.floorMod(steps, ring.slotCount)
    }
}

@Composable
fun rememberRingStates(): Map<Ring, RingState> = remember { Ring.entries.associateWith { RingState(it) } }
