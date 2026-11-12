package com.apex.ai

import android.app.Application
import com.apex.ai.core.ApexLog
import com.apex.ai.core.AppContainer

class ApexApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.get(this)
        ApexLog.d("Apex", "Application booted · tools=${container.tools.size()} · agents=${container.agents.all.size}")
        if (!container.vault.isHardwareBacked) {
            ApexLog.w("Apex", "Credential store is not hardware backed on this device")
        }
    }
}
