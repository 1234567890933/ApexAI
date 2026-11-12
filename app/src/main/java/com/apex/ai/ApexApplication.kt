package com.apex.ai

import android.app.Application
import com.apex.ai.core.ApexCrashHandler
import com.apex.ai.core.ApexLog
import com.apex.ai.core.BootTrace
import com.apex.ai.core.DeviceProfile
import com.apex.ai.core.AppContainer

class ApexApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else touches the container: if start-up ever
        // fails, the reason lands in a file the user can send us.
        ApexCrashHandler.install(this)
        BootTrace.begin(this)
        DeviceProfile.detect(this)
        BootTrace.step(this, "application: profile detected")
        container = AppContainer.get(this)
        BootTrace.step(this, "application: container built")

        // The Keystore is the slowest call in the app and it is NOT allowed on
        // this thread. Warm it up in the background; reads fall back to defaults
        // until it is ready.
        Thread({
            BootTrace.step(this, "vault: warm-up started")
            container.vault.warmUp()
            BootTrace.step(this, "vault: warm-up finished ready=${container.vault.isReady}")
        }, "apex-vault-warmup").apply { priority = Thread.MIN_PRIORITY }.start()
        ApexLog.d("Apex", "Application booted · tools=${container.tools.size()} · agents=${container.agents.all.size}")
        if (!container.vault.isHardwareBacked) {
            ApexLog.w("Apex", "Credential store is not hardware backed on this device")
        }
    }
}
