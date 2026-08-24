package com.easyflow.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.max

/**
 * Adaptive Liquid Glass for the IME surface.
 *
 * Android does not expose another application's pixels to an IME window, so the
 * material cannot refract the host app itself. On Android 13+ an AGSL runtime
 * shader provides procedural lensing, dispersion and specular response. Older
 * releases retain the same silhouette and optical cues through Canvas gradients.
 */
class LiquidGlassDrawable(context: Context, private val radiusDp: Float = 28f) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val reflection = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val streak = Path()
    private val runtimeRenderer: RuntimeLiquidGlassRenderer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeLiquidGlassRenderer() }.getOrNull()
        } else {
            null
        }
    private var drawableAlpha = 255
    private var drawableColorFilter: ColorFilter? = null

    var highlightPhase: Float = .18f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    var expansion: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val inset = density
        rect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
        val radius = radiusDp * density

        if (runtimeRenderer != null) {
            runtimeRenderer.draw(
                canvas = canvas,
                bounds = rect,
                radius = radius,
                phase = highlightPhase,
                expansion = expansion,
                alpha = drawableAlpha,
                colorFilter = drawableColorFilter,
            )
        } else {
            // The fallback stays translucent like the reference instead of using
            // the opaque white slab that made the previous version feel generic.
            fill.shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                intArrayOf(0x72ffffff, 0x3dffffff, 0x58ffffff),
                floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(rect, radius, radius, fill)
        }

        // The perimeter changes hue with its environment; it is intentionally not
        // a uniform outline. Coral is reflected on the left, cool sky on the right.
        rim.strokeWidth = 1.15f * density
        rim.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(0x99ff806f.toInt(), 0xd8ffffff.toInt(), 0x6f9cc8ff),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, radius, radius, rim)

        // Inner rear-surface reflection gives the edge optical thickness.
        val inner = RectF(rect).apply { inset(2.2f * density, 2.2f * density) }
        rim.strokeWidth = .8f * density
        rim.shader = LinearGradient(
            inner.left, inner.top, inner.right, inner.bottom,
            intArrayOf(0x78ffffff, 0x10ffffff, 0x65ffffff),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(inner, max(0f, radius - 2.2f * density), max(0f, radius - 2.2f * density), rim)

        // Bright reflected ribbons live primarily on the rounded side walls.
        reflection.strokeWidth = 2.25f * density
        reflection.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            intArrayOf(0xeaffffff.toInt(), 0x9dffb9aa.toInt(), 0x34ffffff),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawArc(rect, 118f, 126f, false, reflection)

        reflection.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            intArrayOf(0xcaffffff.toInt(), 0x729dccff, 0x2affffff),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawArc(rect, -62f, 124f, false, reflection)

        // A small specular streak moves while listening instead of glowing evenly.
        val streakCenter = rect.left + rect.width() * highlightPhase
        val streakHalf = 25f * density
        streak.reset()
        streak.moveTo((streakCenter - streakHalf).coerceAtLeast(rect.left + radius), rect.top + 1.5f * density)
        streak.lineTo((streakCenter + streakHalf).coerceAtMost(rect.right - radius), rect.top + 1.5f * density)
        reflection.strokeWidth = 1.8f * density
        reflection.shader = LinearGradient(
            streakCenter - streakHalf, 0f, streakCenter + streakHalf, 0f,
            intArrayOf(0x00ffffff, 0xf2ffffff.toInt(), 0x00ffffff),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawPath(streak, reflection)

        // Concentrated light along the lower inner rim reads as a subtle caustic.
        reflection.strokeWidth = .9f * density
        reflection.shader = LinearGradient(
            inner.left, 0f, inner.right, 0f,
            intArrayOf(0x22ff755f, 0x86ffffff.toInt(), 0x2b83b4ff),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawArc(inner, 22f, 136f, false, reflection)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        fill.alpha = alpha
        rim.alpha = alpha
        reflection.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawableColorFilter = colorFilter
        fill.colorFilter = colorFilter
        rim.colorFilter = colorFilter
        reflection.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
