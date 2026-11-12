package com.apex.ai.ui.motion

import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlinx.coroutines.delay

/**
 * Motion vocabulary. Every curve here is tied to a state change — arrival,
 * press, completion — never decoration.
 */
object Motion {

    private const val ENTER_MS = 260L

    fun enter(view: View, index: Int = 0) {
        val density = view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = 14f * density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay((index.coerceAtMost(4) * 28).toLong())
            .setDuration(ENTER_MS)
            .setInterpolator(OvershootInterpolator(0.9f))
            .start()
    }

    /** Press feedback: scale down on touch, spring back on release. */
    fun attachPressScale(view: View, scale: Float = 0.96f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(scale).scaleY(scale).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160)
                        .setInterpolator(OvershootInterpolator(2f)).start()
            }
            false
        }
    }

    fun pulse(view: View) {
        view.animate().cancel()
        view.alpha = 0.45f
        view.animate().alpha(1f).setDuration(320).start()
    }

    /** Cursor shown at the tail of a streaming message. */
    const val CARET = "▍"

    suspend fun breathe(delayMs: Long = 320) = delay(delayMs)
}
