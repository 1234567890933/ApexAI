package com.apex.ai.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Frosted panel: translucent fill plus a 1dp light-source gradient border that
 * runs diagonally, which is what sells the glass illusion without a real blur
 * pass (RenderEffect blur needs API 31 and is expensive on mid-range GPUs).
 */
class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val rect = RectF()
    private val corner = 22f * density

    init {
        setWillNotDraw(false)
        fillPaint.color = 0xB3121828.toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        strokePaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0x66FFFFFF.toInt(), 0x14FFFFFF, 0x44FFFFFF.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(rect, corner, corner, fillPaint)
        canvas.drawRoundRect(rect, corner, corner, strokePaint)
        super.onDraw(canvas)
    }
}
