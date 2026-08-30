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
        listening: Boolean,
        backdropActive: Boolean,
        alpha: Int,
        colorFilter: ColorFilter?,
    ) {
        setUniform("origin", bounds.left, bounds.top)
        setUniform("resolution", bounds.width(), bounds.height())
        setUniform("cornerRadius", radius)
        setUniform("expansion", expansion)
        setUniform("listening", if (listening) 1f else 0f)
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
            shader.setFloatUniform(name, values)
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
            "listening",
            "backdropActive",
        )
        val SET_UNIFORMS = listOf(
            "origin",
            "resolution",
            "cornerRadius",
            "expansion",
            "listening",
            "backdropActive",
        )

        const val PROGRAM = """
            uniform float2 origin;
            uniform float2 resolution;
            uniform float cornerRadius;
            uniform float expansion;
            uniform float listening;
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

                // Per-pixel lens normal derived from the capsule SDF. Android's compositor
                // supplies the real blurred backdrop; this shader only supplies optical
                // thickness, Fresnel response and edge compression over those real pixels.
                float e = 1.25;
                float dx = sdRoundBox(centered + float2(e, 0.0), resolution * 0.5 - 1.0, cornerRadius)
                         - sdRoundBox(centered - float2(e, 0.0), resolution * 0.5 - 1.0, cornerRadius);
                float dy = sdRoundBox(centered + float2(0.0, e), resolution * 0.5 - 1.0, cornerRadius)
                         - sdRoundBox(centered - float2(0.0, e), resolution * 0.5 - 1.0, cornerRadius);
                float2 normal = normalize(float2(dx, dy) + float2(0.0001));
                float rim = 1.0 - smoothstep(0.0, mix(5.5, 7.5, expansion), -d);
                float innerRim = smoothstep(1.5, 5.5, -d) * (1.0 - smoothstep(5.5, 9.5, -d));
                float fresnel = pow(1.0 - abs(normal.y) * 0.34, 3.0) * rim;
                float keyLight = max(0.0, dot(normal, normalize(float2(-0.42, -0.91)))) * rim;
                float refractionCompression = rim * (0.018 + 0.012 * abs(normal.x));

                float centerAlpha = mix(0.205, 0.040, backdropActive);
                float opticalAlpha = 0.030 * fresnel + 0.052 * keyLight + 0.020 * innerRim;
                opticalAlpha += refractionCompression * mix(0.75, 1.0, backdropActive);
                opticalAlpha += listening * 0.012 * innerRim;
                float alpha = (centerAlpha + opticalAlpha) * coverage;
                alpha *= 1.0 - 0.05 * expansion;

                return half4(half3(1.0) * half(alpha), half(alpha));
            }
        """
    }
}
