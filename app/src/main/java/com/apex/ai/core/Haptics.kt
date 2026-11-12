package com.apex.ai.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Contextual tactile feedback. Every pulse has a meaning: send, receive, error,
 * long-press. No decorative buzzing.
 */
class Haptics(private val context: Context) {

    var enabled: Boolean = true

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    fun tap(view: View?) {
        if (!enabled) return
        runCatching { view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    }

    fun confirm() = buzz(longArrayOf(0, 14), -1)
    fun receive() = buzz(longArrayOf(0, 8, 22, 10), -1)
    fun warn() = buzz(longArrayOf(0, 26, 40, 26), -1)
    fun success() = buzz(longArrayOf(0, 12, 30, 12, 30, 18), -1)

    private fun buzz(pattern: LongArray, repeatIndex: Int) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, repeatIndex))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        }
    }
}
