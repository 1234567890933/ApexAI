package com.apex.ai.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.apex.ai.core.DeviceProfile
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ambient depth layer: three slow-moving colour fields behind the content.
 *
 * Ticked at ~16 fps on purpose. A full-screen shader redraw at 60 fps costs more
 * battery than the visual gain is worth, and the motion is deliberately slow
 * enough that nobody can tell the difference.
 */
class AuroraBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f

    private val ticker = object : Runnable {
        override fun run() {
            phase = (phase + 0.0045f) % 1f
            invalidate()
            postDelayed(this, 60L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(ticker)
        // On a low-end phone this layer is static: three full-screen radial
        // shaders per frame is exactly the kind of thing that drops a 6000-series
        // chip to single-digit frame rates while scrolling.
        if (DeviceProfile.auroraEnabled) postDelayed(ticker, 60L)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFF06080F.toInt())
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val angle = phase * 6.2831855f

        blob(canvas, w * (0.20f + 0.07f * sin(angle)), h * 0.16f, w * 0.70f, 0x557C5CFF)
        blob(canvas, w * 0.94f, h * (0.40f + 0.06f * cos(angle)), w * 0.62f, 0x3322D3EE)
        blob(canvas, w * 0.08f, h * 0.94f, w * 0.55f, 0x22F472B6)
    }

    private fun blob(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(color, 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }
}
