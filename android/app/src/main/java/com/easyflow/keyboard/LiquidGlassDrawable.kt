package com.easyflow.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import java.lang.ref.WeakReference

/**
 * Neutral optical surface for the keyboard capsule.
 *
 * The actual pixels behind the IME are softened by Android's cross-window
 * compositor blur on supported Android 12+ devices. This drawable never paints
 * a fabricated scene: AGSL only calculates capsule coverage and a restrained
 * Fresnel edge. Older devices receive a plain translucent neutral surface.
 */
class LiquidGlassDrawable(context: Context, private val radiusDp: Float = 28f) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var hostView = WeakReference<View>(null)
    private var firstDrawLogged = false
    private var drawableAlpha = 255
    private var drawableColorFilter: ColorFilter? = null

    private val runtimeRenderer: RuntimeLiquidGlassRenderer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.i(TAG, "Tier decision: SDK=${Build.VERSION.SDK_INT}, entering Tier 1 AGSL")
            try {
                RuntimeLiquidGlassRenderer().also {
                    Log.i(TAG, "RuntimeShader constructed successfully")
                }
            } catch (error: Throwable) {
                Log.e(TAG, "RuntimeShader construction failed: ${error.message}", error)
                null
            }
        } else {
            Log.i(TAG, "Tier decision: SDK=${Build.VERSION.SDK_INT}, using neutral compatibility material")
            null
        }

    var backdropActive: Boolean = false
        set(value) {
            field = value
            invalidateSelf()
        }

    var expansion: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    var listening: Boolean = false
        set(value) {
            field = value
            invalidateSelf()
        }

    // Retained for binary/source compatibility; the redesigned material has no
    // travelling fake highlight.
    var highlightPhase: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    fun attachHost(view: View) {
        hostView = WeakReference(view)
        Log.i(
            TAG,
            "Host attached: view=$view attached=${view.isAttachedToWindow} " +
                "hardware=${view.isHardwareAccelerated} parent=${view.parent}",
        )
    }

    override fun draw(canvas: Canvas) {
        val inset = density
        rect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
        val radius = radiusDp * density

        if (!firstDrawLogged) {
            val view = hostView.get()
            Log.i(
                TAG,
                "Draw state: SDK=${Build.VERSION.SDK_INT} tier=${if (runtimeRenderer != null) 1 else 2} " +
                    "backdropBound=$backdropActive setRenderEffectCalled=false " +
                    "attachment=DrawablePaint view=$view attached=${view?.isAttachedToWindow} " +
                    "hardware=${view?.isHardwareAccelerated} parent=${view?.parent}",
            )
            firstDrawLogged = true
        }

        if (runtimeRenderer != null) {
            try {
                runtimeRenderer.draw(
                    canvas = canvas,
                    bounds = rect,
                    radius = radius,
                    expansion = expansion,
                    listening = listening,
                    backdropActive = backdropActive,
                    alpha = drawableAlpha,
                    colorFilter = drawableColorFilter,
                )
            } catch (error: Throwable) {
                Log.e(TAG, "RuntimeShader draw/uniform failure: ${error.message}", error)
                drawNeutralFallback(canvas, radius)
            }
        } else {
            drawNeutralFallback(canvas, radius)
        }
    }

    private fun drawNeutralFallback(canvas: Canvas, radius: Float) {
        // No fake refraction, coloured reflection, streak, caustic or outline.
        val baseAlpha = if (backdropActive) 0x18 else 0x52
        fill.color = Color.argb(baseAlpha * drawableAlpha / 255, 255, 255, 255)
        fill.shader = null
        fill.colorFilter = drawableColorFilter
        canvas.drawRoundRect(rect, radius, radius, fill)
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, radiusDp * density)
        outline.alpha = if (backdropActive) 0.12f else 0.32f
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawableColorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val TAG = "EasyFlowGlass"
    }
}
