package com.apex.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apex.ai.agents.AgentEvent
import com.apex.ai.agents.AgentSpec
import com.apex.ai.core.AppContainer
import com.apex.ai.core.ApexLog
import com.apex.ai.core.ApexCrashHandler
import com.apex.ai.core.BootTrace
import com.apex.ai.core.DeviceProfile
import com.apex.ai.core.StartupGuard
import com.apex.ai.data.model.Session
import com.apex.ai.speech.SpeechEngine
import com.apex.ai.speech.VoiceInput
import com.apex.ai.ui.ChatViewModel
import com.apex.ai.ui.SettingsActivity
import com.apex.ai.ui.StartupRecovery
import com.apex.ai.ui.ToolkitActivity
import com.apex.ai.ui.chat.AgentAdapter
import com.apex.ai.ui.chat.AgentPickerSheet
import com.apex.ai.ui.chat.ChatAdapter
import com.apex.ai.ui.chat.ChatItem
import com.apex.ai.ui.chat.RowKind
import com.apex.ai.ui.chat.SessionAdapter
import com.apex.ai.ui.chat.toChatItem
import com.apex.ai.ui.motion.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var container: AppContainer
    private val viewModel: ChatViewModel by viewModels()

    private lateinit var drawer: DrawerLayout
    private lateinit var recyclerChat: RecyclerView
    private lateinit var recyclerAgents: RecyclerView
    private lateinit var inputPrompt: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var tvAgentName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var emptyState: View

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var agentAdapter: AgentAdapter
    private lateinit var historyAdapter: SessionAdapter
    private lateinit var recyclerHistory: RecyclerView
    private var speechEngine: SpeechEngine? = null

    /** Built on first use: binding to the TTS service must not sit on the
     *  start-up path, where a slow engine can stall the first frame. */
    private fun speech(): SpeechEngine =
        speechEngine ?: SpeechEngine(this).also { speechEngine = it }
    private lateinit var voiceInput: VoiceInput
    private lateinit var btnMic: ImageButton

    private var currentAgent: AgentSpec? = null
    private var streamJob: Job? = null
    private var streamingIndex = -1
    private val tokenBuffer = StringBuilder()
    private val main = Handler(Looper.getMainLooper())

    private val flushRunnable = Runnable { flushTokens() }

    private var caretVisible = true
    private val caretRunnable = object : Runnable {
        override fun run() {
            if (streamingIndex < 0) return
            caretVisible = !caretVisible
            val item = chatAdapter.itemAt(streamingIndex)
            if (item != null) {
                item.streaming = caretVisible
                chatAdapter.refresh(streamingIndex)
            }
            main.postDelayed(this, 480L)
        }
    }

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startDictation() else toast(getString(R.string.msg_mic_denied))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BootTrace.step(this, "main: onCreate entered")

        container = AppContainer.get(this)
        BootTrace.step(this, "main: container ready")

        // If the previous launch died before drawing, do not repeat it — repair it.
        if (StartupGuard.crashedLastLaunch(this)) {
            ApexLog.w("Main", "previous launch never reached its first frame")
            StartupRecovery.show(this, ApexCrashHandler.lastReport(this)) {
                runCatching {
                    container.vault.resetAll()
                    container.sessions.clear()
                    ApexCrashHandler.clear(this)
                }
                StartupGuard.clear(this)
                recreate()
            }
            return
        }

        StartupGuard.begin(this)
        try {
            startChatUi()
        } catch (t: Throwable) {
            ApexLog.e("Main", "start-up failed", t)
            runCatching { StartupRecovery.show(this, t.stackTraceToString()) { finish() } }
        }
    }

    private fun startChatUi() {
        BootTrace.step(this, "main: inflating chat layout")
        setContentView(R.layout.activity_main)
        BootTrace.step(this, "main: layout inflated")

        container.haptics.enabled = container.vault.hapticsEnabled
        voiceInput = VoiceInput(this)

        bindViews()
        BootTrace.step(this, "main: views bound")
        wireChat()
        wireAgents()
        wireComposer()
        buildSuggestions()
        BootTrace.step(this, "main: wiring complete")

        val agent = viewModel.catalog.byId(container.vault.selectedAgentId)
        applyAgent(agent, resetSession = false)
        ApexLog.d("Main", "ready · ${container.tools.size()} tools · agent=${agent.id}")
        clearStartupMarkerAfterFirstFrame()
    }

    private fun clearStartupMarkerAfterFirstFrame() {
        val decor = window.decorView
        decor.viewTreeObserver.addOnDrawListener(
            object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    if (decor.viewTreeObserver.isAlive) {
                        decor.viewTreeObserver.removeOnDrawListener(this)
                    }
                    StartupGuard.clear(this@MainActivity)
                    BootTrace.ok(this@MainActivity)
                    ApexLog.d("Main", "first frame drawn")
                }
            }
        )
    }

    // ---------------------------------------------------------------- binding
    private fun bindViews() {
        drawer = findViewById(R.id.drawerLayout)
        recyclerChat = findViewById(R.id.recyclerChat)
        recyclerAgents = findViewById(R.id.recyclerAgents)
        recyclerHistory = findViewById(R.id.recyclerHistory)
        inputPrompt = findViewById(R.id.inputPrompt)
        btnSend = findViewById(R.id.btnSend)
        tvAgentName = findViewById(R.id.tvAgentName)
        tvStatus = findViewById(R.id.tvAgentStatus)
        emptyState = findViewById(R.id.emptyState)

        btnMic = findViewById(R.id.btnMic)
        Motion.attachPressScale(btnSend, 0.9f)
        Motion.attachPressScale(btnMic, 0.9f)
    }

    private fun wireChat() {
        chatAdapter = ChatAdapter(
            onCopy = { text -> copyToClipboard(text) },
            onSpeak = { text -> speech().speak(text) }
        )
        recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerChat.adapter = chatAdapter
        recyclerChat.itemAnimator?.addDuration = 180
        recyclerChat.setItemViewCacheSize(DeviceProfile.recyclerCacheSize)
        recyclerChat.setHasFixedSize(false)
    }

    private fun wireAgents() {
        agentAdapter = AgentAdapter(viewModel.catalog.all, container.vault.selectedAgentId) { agent ->
            applyAgent(agent, resetSession = true)
        }
        recyclerAgents.layoutManager = LinearLayoutManager(this)
        recyclerAgents.adapter = agentAdapter

        findViewById<ImageButton>(R.id.btnAgents).setOnClickListener {
            container.haptics.tap(it)
            refreshHistory()
            drawer.openDrawer(GravityCompat.START)
        }
        historyAdapter = SessionAdapter { session -> openSession(session) }
        recyclerHistory.layoutManager = LinearLayoutManager(this)
        recyclerHistory.adapter = historyAdapter
        recyclerHistory.setItemViewCacheSize(6)

        findViewById<TextView>(R.id.btnExportChat).setOnClickListener {
            container.haptics.tap(it)
            exportChat()
        }

        tvAgentName.setOnLongClickListener {
            currentAgent?.let { agent ->
                AgentPickerSheet.show(this, viewModel.catalog.all, agent.id) { picked ->
                    applyAgent(picked, resetSession = true)
                }
            }
            true
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnTools).setOnClickListener {
            startActivity(Intent(this, ToolkitActivity::class.java))
        }
    }

    private fun wireComposer() {
        btnSend.setOnClickListener {
            if (streamJob?.isActive == true) stopStreaming() else sendPrompt(inputPrompt.text.toString())
        }
        inputPrompt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendPrompt(inputPrompt.text.toString())
                true
            } else {
                false
            }
        }
        findViewById<ImageButton>(R.id.btnNewChat).setOnClickListener {
            newChat()
        }
        btnMic.setOnClickListener { onMicTapped() }
    }

    // ---------------------------------------------------------------- dictation
    private fun onMicTapped() {
        if (voiceInput.isListening) {
            voiceInput.stop()
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startDictation() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startDictation() {
        if (!voiceInput.isAvailable()) {
            toast(getString(R.string.msg_no_speech))
            return
        }
        container.haptics.tap(btnMic)
        voiceInput.start(object : VoiceInput.Listener {
            override fun onState(listening: Boolean) {
                btnMic.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (listening) R.color.apex_cyan else R.color.apex_text_dim
                    )
                )
                tvStatus.text = if (listening) {
                    getString(R.string.label_listening)
                } else {
                    currentAgent?.tagline ?: "ready"
                }
            }

            override fun onPartial(text: String) {
                inputPrompt.setText(text)
                inputPrompt.setSelection(inputPrompt.text.length)
            }

            override fun onFinal(text: String) {
                inputPrompt.setText(text)
                inputPrompt.setSelection(inputPrompt.text.length)
                container.haptics.receive()
            }

            override fun onError(message: String) {
                toast(message)
                btnMic.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, R.color.apex_text_dim)
                )
            }
        })
    }

    // ---------------------------------------------------------------- agents
    private fun applyAgent(agent: AgentSpec, resetSession: Boolean) {
        currentAgent = agent
        tvAgentName.text = agent.name
        tvStatus.text = agent.tagline
        agentAdapter.update(viewModel.catalog.all, agent.id)
        if (resetSession) {
            streamJob?.cancel()
            viewModel.newSession(agent.id)
            chatAdapter.clear()
            emptyState.visibility = View.VISIBLE
        }
        if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START)
        container.haptics.tap(tvAgentName)
    }

    private fun newChat() {
        streamJob?.cancel()
        val agent = currentAgent ?: viewModel.catalog.byId("general")
        viewModel.newSession(agent.id)
        chatAdapter.clear()
        emptyState.visibility = View.VISIBLE
        streamingIndex = -1
        tokenBuffer.setLength(0)
        setStreamingUi(false)
        tvStatus.text = agent.tagline
        container.haptics.tap(btnSend)
    }

    // ---------------------------------------------------------------- sending
    private fun sendPrompt(raw: String) {
        val prompt = raw.trim()
        val agent = currentAgent ?: return
        if (prompt.isEmpty() || streamJob?.isActive == true) return

        if (!container.providers.isConfigured()) {
            toast(getString(R.string.label_no_key))
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        if (!container.network.isOnline()) {
            toast("No network connection — check Wi-Fi or mobile data.")
            return
        }

        inputPrompt.setText("")
        emptyState.visibility = View.GONE
        chatAdapter.add(ChatItem(kind = RowKind.USER, text = prompt))
        streamingIndex = chatAdapter.add(
            ChatItem(
                kind = RowKind.AGENT,
                text = "",
                agentName = agent.name,
                glyph = agent.glyph,
                streaming = true
            )
        )
        scrollToEnd()
        setStreamingUi(true)
        startCaretBlink()
        container.haptics.confirm()

        streamJob = lifecycleScope.launch {
            viewModel.send(agent.id, prompt).collect { event -> handleEvent(event) }
        }
    }

    private fun handleEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.Token -> {
                tokenBuffer.append(event.text)
                scheduleFlush()
            }
            is AgentEvent.Reasoning -> Unit
            is AgentEvent.Status -> tvStatus.text = event.label
            is AgentEvent.Plan -> chatAdapter.add(
                ChatItem(
                    kind = RowKind.STATUS,
                    text = event.steps.joinToString("\n"),
                    title = "Plan · ${event.steps.size} steps",
                    glyph = "◇",
                    meta = "plan"
                )
            ).also { scrollToEnd() }
            is AgentEvent.AgentSpawned -> chatAdapter.add(
                ChatItem(
                    kind = RowKind.STATUS,
                    text = event.brief,
                    title = "▸ ${event.name}",
                    glyph = "⬡",
                    meta = "worker"
                )
            ).also { scrollToEnd() }
            is AgentEvent.ToolStarted -> tvStatus.text = "running ${event.toolId}"
            is AgentEvent.ToolFinished -> {
                val argsLabel = event.preview.lineSequence().firstOrNull()?.take(90) ?: ""
                chatAdapter.add(
                    ChatItem(
                        kind = RowKind.TOOL,
                        text = event.preview,
                        title = "${event.toolId}  $argsLabel",
                        meta = if (event.ok) "${event.durationMs}ms" else "failed",
                        ok = event.ok,
                        glyph = if (event.ok) "⌘" else "⚠"
                    )
                )
                scrollToEnd()
                tvStatus.text = "thinking"
            }
            is AgentEvent.Completed -> finishStreaming()
            is AgentEvent.Failed -> {
                tokenBuffer.append("\n\n⚠ ").append(event.message)
                flushTokens()
                finishStreaming()
                container.haptics.warn()
            }
        }
    }

    private fun scheduleFlush() {
        main.removeCallbacks(flushRunnable)
        main.postDelayed(flushRunnable, 45L)
    }

    private fun flushTokens() {
        if (streamingIndex < 0) return
        if (tokenBuffer.isEmpty()) return
        val item = chatAdapter.itemAt(streamingIndex) ?: return
        item.text = item.text + tokenBuffer.toString()
        tokenBuffer.setLength(0)
        chatAdapter.refresh(streamingIndex)
        scrollToEnd()
    }

    private fun startCaretBlink() {
        main.removeCallbacks(caretRunnable)
        caretVisible = true
        main.postDelayed(caretRunnable, 480L)
    }

    private fun finishStreaming() {
        main.removeCallbacks(flushRunnable)
        main.removeCallbacks(caretRunnable)
        flushTokens()
        val index = streamingIndex
        if (index >= 0) {
            val item = chatAdapter.itemAt(index)
            if (item != null) {
                item.streaming = false
                chatAdapter.refresh(index)
                viewModel.recordAssistant(item.agentName, item.text)
                if (container.vault.voiceEnabled && item.text.isNotBlank()) {
                    speech().speak(item.text)
                }
            }
        }
        streamingIndex = -1
        setStreamingUi(false)
        tvStatus.text = currentAgent?.tagline ?: "ready"
        viewModel.persist()
        refreshHistory()
        container.haptics.receive()
    }

    private fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        finishStreaming()
        container.haptics.tap(btnSend)
    }

    private fun setStreamingUi(streaming: Boolean) {
        btnSend.setImageResource(if (streaming) R.drawable.ic_stop else R.drawable.ic_send)
        btnSend.contentDescription = getString(if (streaming) R.string.action_stop else R.string.action_send)
    }

    // ---------------------------------------------------------------- history
    private fun refreshHistory() {
        lifecycleScope.launch {
            val sessions = withContext(Dispatchers.IO) { viewModel.recentSessions() }
            historyAdapter.update(sessions)
        }
    }

    private fun openSession(session: Session) {
        val opened = viewModel.open(session.id) ?: return
        val agent = viewModel.catalog.byId(opened.agentId)
        currentAgent = agent
        tvAgentName.text = agent.name
        tvStatus.text = agent.tagline
        agentAdapter.update(viewModel.catalog.all, agent.id)

        val items = opened.messages.map { it.toChatItem(agent.name, agent.glyph) }
        chatAdapter.replaceAll(items)
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        drawer.closeDrawer(GravityCompat.START)
        scrollToEnd()
        container.haptics.tap(recyclerChat)
    }

    private fun exportChat() {
        val transcript = viewModel.transcriptText()
        if (viewModel.session.messages.isEmpty()) {
            toast("Nothing to export yet")
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Apex AI · ${viewModel.session.title}")
            putExtra(Intent.EXTRA_TEXT, transcript)
        }
        startActivity(Intent.createChooser(intent, "Export chat"))
    }

    // ---------------------------------------------------------------- misc ui
    private fun buildSuggestions() {
        val row = findViewById<LinearLayout>(R.id.suggestionRow)
        row.removeAllViews()
        val prompts = listOf(
            "Plan a 7-day Hampi trip on ₹30,000",
            "Review this Kotlin snippet for leaks",
            "Explain SSE streaming in five lines",
            "What can your tools actually do?"
        )
        prompts.forEach { prompt ->
            val chip = TextView(this)
            chip.text = prompt
            chip.setTextColor(ContextCompat.getColor(this, R.color.apex_text_dim))
            chip.textSize = 12f
            chip.setBackgroundResource(R.drawable.bg_chip)
            chip.setPadding(dp(14), dp(9), dp(14), dp(9))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = dp(8)
            chip.layoutParams = params
            chip.setOnClickListener { sendPrompt(prompt) }
            Motion.attachPressScale(chip, 0.94f)
            row.addView(chip)
        }
    }

    private fun scrollToEnd() {
        val count = chatAdapter.itemCount
        if (count == 0) return
        recyclerChat.post { recyclerChat.scrollToPosition(count - 1) }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Apex AI", text))
        toast("Copied")
        container.haptics.tap(recyclerChat)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        viewModel.persist()
    }

    override fun onDestroy() {
        main.removeCallbacks(flushRunnable)
        streamJob?.cancel()
        runCatching { voiceInput.destroy() }
        speechEngine?.shutdown()
        speechEngine = null
        super.onDestroy()
    }
}
