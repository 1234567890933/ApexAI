package com.apex.ai.core

import android.content.Context
import com.apex.ai.agents.AgentCatalog
import com.apex.ai.data.SessionStore
import com.apex.ai.llm.ProviderRegistry
import com.apex.ai.tools.ToolRegistry
import com.apex.ai.tools.builtin.registerCoreTools
import com.apex.ai.tools.builtin.registerNetworkTools
import com.apex.ai.tools.builtin.registerSandboxTools
import com.apex.ai.tools.builtin.registerSecurityTools
import com.apex.ai.tools.builtin.registerTextTools

/**
 * Hand-rolled dependency container. Deliberately no DI framework: an annotation
 * processor would add build time and reflection cost to a process that already
 * spends its budget on streaming tokens.
 */
class AppContainer private constructor(context: Context) {

    val appContext: Context = context.applicationContext
    val vault = LocalVault(appContext)
    val haptics = Haptics(appContext)
    val network = NetworkMonitor(appContext)
    val sessions = SessionStore(appContext)
    val providers = ProviderRegistry(vault)
    val agents = AgentCatalog()

    val tools: ToolRegistry = ToolRegistry().apply {
        registerCoreTools(appContext)
        registerTextTools()
        registerNetworkTools()
        registerSecurityTools(appContext)
        registerSandboxTools(appContext)
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
    }
}
