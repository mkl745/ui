package com.orbit.launcher.feature.home

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

private const val GLOW_SHADER = """
uniform float2 center;
uniform float radius;
uniform half3 tint;
half4 main(float2 p) {
    float falloff = max(0.0, 1.0 - distance(p, center) / radius);
    half alpha = half(falloff * falloff * 0.18);
    return half4(tint * alpha, alpha);
}
"""

@RequiresApi(33)
private fun makeShader(): Shader? = try { RuntimeShader(GLOW_SHADER) } catch (_: RuntimeException) { null }

@RequiresApi(33)
private fun updateShader(shader: Shader, center: Offset, radius: Float, color: Color) {
    (shader as RuntimeShader).apply {
        setFloatUniform("center", center.x, center.y)
        setFloatUniform("radius", radius)
        setFloatUniform("tint", color.red, color.green, color.blue)
    }
}

/** Shader is allocated once, and only the draw phase observes the pulse animation. */
@Composable
fun OrbitGlow(modifier: Modifier, color: Color, reduceMotion: Boolean) {
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) makeShader() else null }
    val paint = remember(shader) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader } }
    val pulse = if (!reduceMotion) {
        val transition = rememberInfiniteTransition(label = "orbit glow")
        val value by transition.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "glow pulse")
        { value }
    } else {
        { 1f }
    }
    Canvas(modifier) {
        val radius = size.minDimension * .39f * pulse()
        if (shader != null && Build.VERSION.SDK_INT >= 33) {
            updateShader(shader, center, radius, color)
            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
        } else {
            drawCircle(brush = Brush.radialGradient(listOf(color.copy(alpha = .16f), Color.Transparent), center, radius), radius = radius, center = center)
        }
    }
}
