package com.easyflow.keyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/** GPU material used only where AGSL RuntimeShader is available. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class RuntimeLiquidGlassRenderer {
    private val shader = RuntimeShader(PROGRAM)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@RuntimeLiquidGlassRenderer.shader }

    fun draw(
        canvas: Canvas,
        bounds: RectF,
        radius: Float,
        phase: Float,
        expansion: Float,
        alpha: Int,
        colorFilter: ColorFilter?,
    ) {
        shader.setFloatUniform("origin", bounds.left, bounds.top)
        shader.setFloatUniform("resolution", bounds.width(), bounds.height())
        shader.setFloatUniform("cornerRadius", radius)
        shader.setFloatUniform("phase", phase)
        shader.setFloatUniform("expansion", expansion)
        paint.alpha = alpha
        paint.colorFilter = colorFilter
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }

    private companion object {
        // This is a procedural material rather than a screenshot blur. IME windows
        // cannot sample the pixels owned by the app underneath them.
        const val PROGRAM = """
            uniform float2 origin;
            uniform float2 resolution;
            uniform float cornerRadius;
            uniform float phase;
            uniform float expansion;

            float sdRoundBox(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + r;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
            }

            half3 lightField(float2 uv) {
                float rose = exp(-13.0 * dot(uv - float2(0.12, 0.78), uv - float2(0.12, 0.78)));
                float blue = exp(-11.0 * dot(uv - float2(0.90, 0.22), uv - float2(0.90, 0.22)));
                half3 neutral = half3(0.94, 0.95, 0.98);
                return neutral + half3(0.10, 0.025, 0.015) * rose + half3(0.01, 0.045, 0.12) * blue;
            }

            half4 main(float2 fragCoord) {
                float2 local = fragCoord - origin;
                float2 uv = local / resolution;
                float2 centered = local - resolution * 0.5;
                float d = sdRoundBox(centered, resolution * 0.5 - 1.0, cornerRadius);
                float inside = 1.0 - smoothstep(-0.6, 1.2, d);
                float rim = 1.0 - smoothstep(0.2, 5.0, abs(d));
                float innerRim = 1.0 - smoothstep(1.2, 7.0, abs(d + 3.0));

                float2 normal = normalize(centered / max(resolution * 0.5, float2(1.0)) + float2(0.0001));
                float lens = rim * (0.012 + 0.008 * (1.0 - expansion));
                float dispersion = rim * 0.0045;
                float2 shifted = uv + normal * lens;
                half3 color = half3(0.0);
                color.r = lightField(shifted + normal * dispersion).r;
                color.g = lightField(shifted).g;
                color.b = lightField(shifted - normal * dispersion).b;

                float topLight = pow(max(0.0, 1.0 - uv.y), 9.0) * (0.25 + 0.75 * rim);
                float sideLight = pow(max(0.0, abs(normal.x)), 7.0) * rim;
                float moving = exp(-pow((uv.x - phase) / 0.105, 2.0)) * exp(-pow(uv.y / 0.075, 2.0));
                float lowerCaustic = exp(-pow((uv.y - 0.93) / 0.05, 2.0)) * innerRim;
                float specular = 0.20 * topLight + 0.34 * sideLight + 0.42 * moving + 0.16 * lowerCaustic;
                color += half3(specular);

                float opacity = (0.16 + 0.20 * rim + 0.07 * innerRim + 0.09 * topLight) * inside;
                opacity *= 1.0 - 0.10 * expansion;
                return half4(color * opacity, opacity);
            }
        """
    }
}
