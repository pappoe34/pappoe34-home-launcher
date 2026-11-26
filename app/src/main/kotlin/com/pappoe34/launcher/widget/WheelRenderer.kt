package com.pappoe34.launcher.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.os.Build
import com.pappoe34.launcher.model.LauncherSettings
import com.pappoe34.launcher.model.Ring
import com.pappoe34.launcher.model.SlotId
import com.pappoe34.launcher.model.WheelTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Draws the dial with plain android.graphics so it can be shown in a RemoteViews ImageView. */
class WheelRenderer(private val context: Context) {

    /** Pixel geometry of the dial centred in a widget of [width] x [height]. */
    class Layout(val width: Float, val height: Float) {
        val diameter = min(width, height)
        val cx = width / 2f
        val cy = height / 2f
        private val pad = diameter * 0.015f

        val iconPx: Map<Ring, Float> = mapOf(
            Ring.INNER to diameter * 0.105f,
            Ring.MIDDLE to diameter * 0.12f,
            Ring.OUTER to diameter * 0.13f,
        )
        val trackPx: Map<Ring, Float> = iconPx.mapValues { it.value * 1.3f }
        val radiusPx: Map<Ring, Float>
        val hubRadiusPx: Float
        val outerEdgePx: Float

        init {
            val outer = diameter / 2f - trackPx.getValue(Ring.OUTER) / 2f - pad
            radiusPx = mapOf(Ring.OUTER to outer, Ring.MIDDLE to outer * 0.68f, Ring.INNER to outer * 0.38f)
            hubRadiusPx = (radiusPx.getValue(Ring.INNER) - trackPx.getValue(Ring.INNER) / 2f - diameter * 0.015f)
                .coerceAtLeast(diameter * 0.06f)
            outerEdgePx = outer + trackPx.getValue(Ring.OUTER) / 2f + pad
        }

        /** Which ring contains the point; null for the hub or outside the dial. */
        fun ringAt(x: Float, y: Float): Ring? {
            val r = hypot(x - cx, y - cy)
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

        fun isHub(x: Float, y: Float): Boolean = hypot(x - cx, y - cy) < hubRadiusPx

        /** Rotation that puts [selected] in the ring's window. */
        fun angleFor(ring: Ring, selected: Int): Float = ring.windowAngle + 90f - selected * ring.detentDegrees

        /** Slot whose icon is nearest to the point on [ring]. */
        fun slotAt(ring: Ring, selected: Int, x: Float, y: Float): Int {
            val pointAngle = (atan2(y - cy, x - cx) * 180.0 / PI).toFloat()
            val angle = angleFor(ring, selected)
            val raw = ((pointAngle - (angle - 90f)) / ring.detentDegrees).roundToInt()
            return Math.floorMod(raw, ring.slotCount)
        }
    }

    private val pm: PackageManager = context.packageManager
    private val iconCache = HashMap<String, Drawable?>()

    fun render(widthPx: Int, heightPx: Int, settings: LauncherSettings): Bitmap {
        val layout = Layout(widthPx.toFloat(), heightPx.toFloat())
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = WidgetColors.forTheme(context, settings.theme)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Backdrop disc for legibility over any wallpaper.
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            layout.cx, layout.cy, layout.outerEdgePx,
            intArrayOf(colors.backdrop, colors.backdrop, Color.TRANSPARENT),
            floatArrayOf(0f, 0.92f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(layout.cx, layout.cy, layout.outerEdgePx, paint)
        paint.shader = null

        Ring.entries.forEach { ring -> drawRing(canvas, layout, ring, settings, colors, paint) }
        drawHub(canvas, layout, colors, paint)
        return bitmap
    }

    private fun drawRing(canvas: Canvas, l: Layout, ring: Ring, settings: LauncherSettings, colors: WidgetColors, paint: Paint) {
        val r = l.radiusPx.getValue(ring)
        val w = l.trackPx.getValue(ring)
        val accent = colors.accent(ring)
        val selected = settings.selectedSlot(ring)
        val angle = l.angleFor(ring, selected)
        val bounds = RectF(l.cx - r, l.cy - r, l.cx + r, l.cy + r)

        paint.style = Paint.Style.STROKE
        paint.shader = null

        // Glow.
        paint.strokeWidth = w + l.diameter * 0.05f
        paint.color = withAlpha(accent, if (settings.theme == WheelTheme.NEON) 0.35f else 0.12f)
        canvas.drawCircle(l.cx, l.cy, r, paint)

        // Track.
        paint.strokeWidth = w
        if (settings.theme == WheelTheme.METALLIC) {
            paint.shader = SweepGradient(
                l.cx, l.cy,
                intArrayOf(0xFF8E939A.toInt(), 0xFFD9DDE1.toInt(), 0xFF5F646A.toInt(), 0xFFC3C7CC.toInt(), 0xFF6E737A.toInt(), 0xFFE2E5E8.toInt(), 0xFF7B8087.toInt(), 0xFF8E939A.toInt()),
                null,
            )
            paint.color = Color.WHITE
        } else {
            paint.color = colors.track
        }
        canvas.drawCircle(l.cx, l.cy, r, paint)
        paint.shader = null

        if (settings.depth3d) {
            // Tube shading: dark edges, lit crown.
            val extent = r + w / 2f
            val inner = (r - w / 2f) / extent
            paint.shader = RadialGradient(
                l.cx, l.cy, extent,
                intArrayOf(withAlpha(Color.BLACK, 0.45f), withAlpha(Color.WHITE, 0.22f), Color.TRANSPARENT, withAlpha(Color.BLACK, 0.5f)),
                floatArrayOf(inner, inner + (1f - inner) * 0.42f, inner + (1f - inner) * 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
            paint.color = Color.WHITE
            canvas.drawCircle(l.cx, l.cy, r, paint)
            paint.shader = SweepGradient(
                l.cx, l.cy,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(Color.WHITE, 0.16f), Color.TRANSPARENT, Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 0.68f, 0.78f, 1f),
            )
            canvas.drawCircle(l.cx, l.cy, r, paint)
            paint.shader = null
            paint.strokeWidth = l.diameter * 0.004f
            paint.color = withAlpha(Color.WHITE, 0.35f)
            canvas.drawCircle(l.cx, l.cy, r + w / 2f, paint)
            paint.color = withAlpha(Color.BLACK, 0.55f)
            canvas.drawCircle(l.cx, l.cy, r - w / 2f, paint)
        }

        // Rims.
        paint.strokeWidth = l.diameter * 0.004f
        paint.color = colors.rim
        canvas.drawCircle(l.cx, l.cy, r + w / 2f, paint)
        canvas.drawCircle(l.cx, l.cy, r - w / 2f, paint)

        // Notches between slots.
        paint.strokeWidth = l.diameter * 0.005f
        paint.color = colors.notch
        val inset = w * 0.12f
        for (i in 0 until ring.slotCount) {
            val a = (angle + i * ring.detentDegrees + ring.detentDegrees / 2f - 90f) * DEG
            canvas.drawLine(
                l.cx + cos(a) * (r - w / 2f + inset), l.cy + sin(a) * (r - w / 2f + inset),
                l.cx + cos(a) * (r + w / 2f - inset), l.cy + sin(a) * (r + w / 2f - inset),
                paint,
            )
        }

        // Selection window.
        val sweep = ring.detentDegrees * 0.92f
        val start = ring.windowAngle - sweep / 2f
        paint.strokeWidth = w
        paint.color = withAlpha(accent, 0.28f)
        canvas.drawArc(bounds, start, sweep, false, paint)
        paint.strokeWidth = l.diameter * 0.008f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = accent
        val outerBounds = RectF(l.cx - r - w / 2f, l.cy - r - w / 2f, l.cx + r + w / 2f, l.cy + r + w / 2f)
        val innerBounds = RectF(l.cx - r + w / 2f, l.cy - r + w / 2f, l.cx + r - w / 2f, l.cy + r - w / 2f)
        canvas.drawArc(outerBounds, start, sweep, false, paint)
        canvas.drawArc(innerBounds, start, sweep, false, paint)
        paint.strokeCap = Paint.Cap.BUTT

        // Pointer nub.
        val ta = ring.windowAngle * DEG
        val tipR = r + w / 2f + l.diameter * 0.008f
        val tip = floatArrayOf(l.cx + cos(ta) * tipR, l.cy + sin(ta) * tipR)
        val back = l.diameter * 0.022f
        val half = l.diameter * 0.014f
        val sx = -sin(ta)
        val sy = cos(ta)
        val path = Path().apply {
            moveTo(tip[0], tip[1])
            lineTo(tip[0] + cos(ta) * back + sx * half, tip[1] + sin(ta) * back + sy * half)
            lineTo(tip[0] + cos(ta) * back - sx * half, tip[1] + sin(ta) * back - sy * half)
            close()
        }
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)

        // Icons.
        val iconSize = l.iconPx.getValue(ring)
        for (i in 0 until ring.slotCount) {
            val a = (angle + i * ring.detentDegrees - 90f) * DEG
            val x = l.cx + cos(a) * r
            val y = l.cy + sin(a) * r
            val scale = if (i == selected) 1.25f else 1f
            val half2 = iconSize * scale / 2f
            val drawable = settings.appKeyAt(SlotId(ring, i))?.let { iconFor(it) }
            if (drawable != null) {
                if (settings.depth3d) {
                    paint.style = Paint.Style.FILL
                    paint.color = withAlpha(Color.BLACK, 0.35f)
                    canvas.drawCircle(x, y + half2 * 0.12f, half2 * 0.98f, paint)
                }
                drawable.setBounds((x - half2).roundToInt(), (y - half2).roundToInt(), (x + half2).roundToInt(), (y + half2).roundToInt())
                drawable.draw(canvas)
            } else {
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(Color.BLACK, 0.25f)
                canvas.drawCircle(x, y, half2, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = l.diameter * 0.004f
                paint.color = withAlpha(colors.label, 0.6f)
                val plus = half2 * 0.45f
                canvas.drawLine(x - plus, y, x + plus, y, paint)
                canvas.drawLine(x, y - plus, x, y + plus, paint)
            }
        }
    }

    private fun drawHub(canvas: Canvas, l: Layout, colors: WidgetColors, paint: Paint) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            l.cx - l.hubRadiusPx * 0.3f, l.cy - l.hubRadiusPx * 0.3f, l.hubRadiusPx * 1.6f,
            intArrayOf(lighten(colors.hub, 0.18f), colors.hub, darken(colors.hub, 0.25f)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(l.cx, l.cy, l.hubRadiusPx, paint)
        paint.shader = null
        // A small "expand" glyph: four corner ticks.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = l.diameter * 0.006f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = colors.hubContent
        val s = l.hubRadiusPx * 0.45f
        val t = s * 0.5f
        for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
            val x = l.cx + dx * s
            val y = l.cy + dy * s
            canvas.drawLine(x, y, x - dx * t, y, paint)
            canvas.drawLine(x, y, x, y - dy * t, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun iconFor(key: String): Drawable? = iconCache.getOrPut(key) {
        val slash = key.indexOf('/')
        if (slash <= 0) return@getOrPut null
        val component = ComponentName(key.substring(0, slash), key.substring(slash + 1))
        runCatching { pm.getActivityIcon(component) }
            .recoverCatching { pm.getApplicationIcon(component.packageName) }
            .getOrNull()
    }

    private companion object {
        const val DEG = (PI / 180.0).toFloat()

        fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

        fun lighten(color: Int, amount: Float): Int = Color.rgb(
            (Color.red(color) + (255 - Color.red(color)) * amount).roundToInt(),
            (Color.green(color) + (255 - Color.green(color)) * amount).roundToInt(),
            (Color.blue(color) + (255 - Color.blue(color)) * amount).roundToInt(),
        )

        fun darken(color: Int, amount: Float): Int = Color.rgb(
            (Color.red(color) * (1f - amount)).roundToInt(),
            (Color.green(color) * (1f - amount)).roundToInt(),
            (Color.blue(color) * (1f - amount)).roundToInt(),
        )
    }
}

/** Widget colours per theme, mirroring the Compose palettes. */
class WidgetColors(
    val backdrop: Int,
    val track: Int,
    val rim: Int,
    val notch: Int,
    val hub: Int,
    val hubContent: Int,
    val label: Int,
    private val accents: Map<Ring, Int>,
) {
    fun accent(ring: Ring): Int = accents.getValue(ring)

    companion object {
        fun forTheme(context: Context, theme: WheelTheme): WidgetColors = when (theme) {
            WheelTheme.METALLIC -> WidgetColors(
                backdrop = 0x66000000, track = 0xFF9EA3A8.toInt(), rim = 0xFF23272B.toInt(), notch = 0xB3303439.toInt(),
                hub = 0xFF2B2F34.toInt(), hubContent = 0xFFE6E8EB.toInt(), label = Color.WHITE,
                accents = Ring.entries.associateWith { 0xFFE6E8EB.toInt() },
            )
            WheelTheme.NEON -> WidgetColors(
                backdrop = 0xB3000000.toInt(), track = 0xCC0B0F14.toInt(), rim = 0x660B0F14, notch = 0x66FFFFFF,
                hub = 0xFF0B0F14.toInt(), hubContent = 0xFF00E5FF.toInt(), label = 0xFFE0FBFF.toInt(),
                accents = mapOf(Ring.INNER to 0xFFB6FF3B.toInt(), Ring.MIDDLE to 0xFFFF2BD6.toInt(), Ring.OUTER to 0xFF00E5FF.toInt()),
            )
            WheelTheme.GLASS -> WidgetColors(
                backdrop = 0x40000000, track = 0x1FFFFFFF, rim = 0x66FFFFFF, notch = 0x4DFFFFFF,
                hub = 0x55FFFFFF, hubContent = Color.WHITE, label = Color.WHITE,
                accents = Ring.entries.associateWith { Color.WHITE },
            )
            WheelTheme.DYNAMIC -> {
                val (a1, a2, a3) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Triple(
                        context.getColor(android.R.color.system_accent3_200),
                        context.getColor(android.R.color.system_accent2_200),
                        context.getColor(android.R.color.system_accent1_200),
                    )
                } else {
                    Triple(0xFF81C995.toInt(), 0xFFF28B82.toInt(), 0xFF8AB4F8.toInt())
                }
                WidgetColors(
                    backdrop = 0x73000000, track = 0xCC1E293B.toInt(), rim = 0x80FFFFFF.toInt(), notch = 0x59FFFFFF,
                    hub = a3, hubContent = 0xFF0F172A.toInt(), label = Color.WHITE,
                    accents = mapOf(Ring.INNER to a1, Ring.MIDDLE to a2, Ring.OUTER to a3),
                )
            }
        }
    }
}
