package com.apex.ai.core

import android.app.ActivityManager
import android.content.Context

/**
 * Classifies the phone once, at start-up, and every visual subsystem asks this
 * before spending a frame.
 *
 * Target: play comfortably on an entry-level 4-core phone with 2 GB of RAM
 * (Snapdragon 6-series and below). The rules are deliberately conservative —
 * when in doubt, assume the weaker device.
 */
object DeviceProfile {

    @Volatile
    var isLowEnd: Boolean = false
        private set

    @Volatile
    var totalRamMb: Int = 0
        private set

    @Volatile
    var cores: Int = 1
        private set

    @Volatile
    var isLowRamDeviceFlag: Boolean = false
        private set

    fun detect(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        isLowRamDeviceFlag = runCatching { activityManager?.isLowRamDevice ?: false }.getOrDefault(false)

        val info = ActivityManager.MemoryInfo()
        runCatching { activityManager?.getMemoryInfo(info) }
        totalRamMb = (info.totalMem / (1024L * 1024L)).toInt()
        cores = Runtime.getRuntime().availableProcessors()

        isLowEnd = isLowRamDeviceFlag || (totalRamMb in 1..2_700) || cores <= 4
        ApexLog.d(
            "DeviceProfile",
            "ram=${totalRamMb}MB cores=$cores lowRam=$isLowRamDeviceFlag lowEnd=$isLowEnd"
        )
    }

    /** Spring and stagger animations cost frames; low-end devices skip them. */
    val animationsEnabled: Boolean get() = !isLowEnd

    /** The ambient shader layer is the single most expensive thing we draw. */
    val auroraEnabled: Boolean get() = !isLowEnd && totalRamMb > 3_000

    val recyclerCacheSize: Int get() = if (isLowEnd) 8 else 16

    val toolCallBudget: Int get() = if (isLowEnd) 12 else 24
}
