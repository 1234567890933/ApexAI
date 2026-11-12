package com.apex.ai.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.apex.ai.R

/**
 * Shown instead of a silent death.
 *
 * If the previous launch never reached its first frame, the user gets an
 * explanation, the trace, and one button that repairs the app in place. That is
 * the difference between "your app is broken" and "your app is broken and here
 * is the fix".
 */
object StartupRecovery {

    fun show(activity: AppCompatActivity, crashText: String?, onReset: () -> Unit) {
        val trace = com.apex.ai.core.BootTrace.lastCompletedRun(activity)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(56), dp(22), dp(28))
            setBackgroundColor(ContextCompat.getColor(activity, R.color.apex_bg))
        }

        root.addView(TextView(activity).apply {
            text = "◈"
            textSize = 40f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(ContextCompat.getColor(activity, R.color.apex_violet))
        })

        root.addView(TextView(activity).apply {
            text = "Apex AI could not finish starting"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(14), 0, dp(8))
            setTextColor(ContextCompat.getColor(activity, R.color.apex_text))
        })

        root.addView(TextView(activity).apply {
            text = "The last launch stopped before the first screen was drawn. " +
                "Resetting app data repairs the most common cause — a credential store " +
                "the system key store can no longer decrypt."
            textSize = 13f
            setTextColor(ContextCompat.getColor(activity, R.color.apex_text_dim))
            setPadding(0, 0, 0, dp(18))
        })

        root.addView(Button(activity).apply {
            text = "Reset app data and retry"
            setOnClickListener { onReset() }
        })

        root.addView(TextView(activity).apply {
            text = "Technical details"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(22), 0, dp(6))
            setTextColor(ContextCompat.getColor(activity, R.color.apex_text_dim))
        })

        root.addView(TextView(activity).apply {
            text = buildString {
                if (!trace.isNullOrBlank()) {
                    appendLine("Start-up trace (last run):")
                    appendLine(trace.takeLast(1200))
                    appendLine()
                }
                append(crashText?.take(5000) ?: "No exception was recorded — the process was killed.")
            }
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setTextColor(ContextCompat.getColor(activity, R.color.apex_text_faint))
        })

        activity.setContentView(
            ScrollView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(root)
            }
        )
    }
}
