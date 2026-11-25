package com.pappoe34.launcher.model

/** The three concentric rings of the dial, innermost first. */
enum class Ring(val slotCount: Int) {
    INNER(6),
    MIDDLE(10),
    OUTER(14);

    val detentDegrees: Float get() = 360f / slotCount
}

/** One position on one ring. */
data class SlotId(val ring: Ring, val index: Int) {
    init {
        require(index in 0 until ring.slotCount) { "slot $index out of range for $ring" }
    }

    /** Serialised form used as a preferences key fragment, e.g. "OUTER:3". */
    fun encode(): String = "${ring.name}:$index"

    companion object {
        fun decode(text: String): SlotId? {
            val parts = text.split(':')
            if (parts.size != 2) return null
            val ring = Ring.entries.firstOrNull { it.name == parts[0] } ?: return null
            val index = parts[1].toIntOrNull() ?: return null
            if (index !in 0 until ring.slotCount) return null
            return SlotId(ring, index)
        }

        fun all(): List<SlotId> = Ring.entries.flatMap { ring -> (0 until ring.slotCount).map { SlotId(ring, it) } }
    }
}

enum class WheelTheme { METALLIC, NEON, GLASS, DYNAMIC }

data class LauncherSettings(
    /** Slot -> app key ("package/activity"). */
    val slots: Map<SlotId, String> = emptyMap(),
    val theme: WheelTheme = WheelTheme.METALLIC,
    val haptics: Boolean = true,
    val sound: Boolean = false,
    val blur: Boolean = true,
    /** True once the wheels were auto-filled on first run. */
    val seeded: Boolean = false,
)
