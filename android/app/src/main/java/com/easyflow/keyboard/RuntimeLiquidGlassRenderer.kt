package com.easyflow.keyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * AGSL surface response for Android 13+.
 *
 * The live backdrop comes from Android's cross-window compositor blur. This
 * shader only models the capsule's neutral transmission and Fresnel edge; it
 * does not invent environmental colours or reflection streaks.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class RuntimeLiquidGlassRenderer {
    private val shader: RuntimeShader
    private val paint: Paint
    private var uniformSetLogged = false

    init {
        Log.i(TAG, "AGSL declared uniforms: ${DECLARED_UNIFORMS.joinToString()}")
        Log.i(TAG, "Kotlin-set uniforms: ${SET_UNIFORMS.joinToString()}")
        shader = try {
            RuntimeShader(PROGRAM)
        } catch (error: Throwable) {
            Log.e(TAG, "AGSL compile failed: ${error.message}", error)
            throw error
        }
        paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@RuntimeLiquidGlassRenderer.shader }
    }

    fun draw(
        canvas: Canvas,
        bounds: RectF,
        radius: Float,
        expansion: Float,
        backdropActive: Boolean,
        alpha: Int,
        colorFilter: ColorFilter?,
    ) {
        setUniform("origin", bounds.left, bounds.top)
        setUniform("resolution", bounds.width(), bounds.height())
        setUniform("cornerRadius", radius)
        setUniform("expansion", expansion)
        setUniform("backdropActive", if (backdropActive) 1f else 0f)
        if (!uniformSetLogged) {
            Log.i(
                TAG,
                "Uniform binding complete: declared=${DECLARED_UNIFORMS.joinToString()} " +
                    "set=${SET_UNIFORMS.joinToString()} backdropCompositorActive=$backdropActive",
            )
            uniformSetLogged = true
        }
        paint.alpha = alpha
        paint.colorFilter = colorFilter
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }

    private fun setUniform(name: String, vararg values: Float) {
        try {
            shader.setFloatUniform(name, *values)
        } catch (error: Throwable) {
            Log.e(TAG, "Uniform '$name' failed with ${values.contentToString()}: ${error.message}", error)
            throw error
        }
    }

    private companion object {
        const val TAG = "EasyFlowGlass"
        val DECLARED_UNIFORMS = listOf(
            "origin",
            "resolution",
            "cornerRadius",
            "expansion",
            "backdropActive",
        )
        val SET_UNIFORMS = listOf(
            "origin",
            "resolution",
            "cornerRadius",
            "expansion",
            "backdropActive",
        )

        const val PROGRAM = """
            uniform float2 origin;
            uniform float2 resolution;
            uniform float cornerRadius;
            uniform float expansion;
            uniform float backdropActive;

            float sdRoundBox(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + r;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
            }

            half4 main(float2 fragCoord) {
                float2 local = fragCoord - origin;
                float2 centered = local - resolution * 0.5;
                float d = sdRoundBox(centered, resolution * 0.5 - 1.0, cornerRadius);
                float coverage = 1.0 - smoothstep(-0.65, 0.85, d);

                // Real glass is mostly transmission. A restrained, symmetric
                // Fresnel response gives the capsule optical thickness without
                // painting directional highlights or a fictional environment.
                float edge = 1.0 - smoothstep(0.0, 4.0, -d);
                float centerAlpha = mix(0.20, 0.045, backdropActive);
                float fresnelAlpha = mix(0.030, 0.055, backdropActive);
                float alpha = (centerAlpha + fresnelAlpha * edge) * coverage;
                alpha *= 1.0 - 0.05 * expansion;

                return half4(half3(1.0) * half(alpha), half(alpha));
            }
        """
    }
}
