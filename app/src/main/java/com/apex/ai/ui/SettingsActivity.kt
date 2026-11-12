package com.apex.ai.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.apex.ai.R
import com.apex.ai.core.ApexLog
import com.apex.ai.core.AppContainer
import com.apex.ai.core.ProviderIds
import com.apex.ai.core.Redactor
import com.apex.ai.llm.ModelCatalog
import com.apex.ai.ui.motion.Motion
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var container: AppContainer

    private lateinit var providerRow: LinearLayout
    private lateinit var tvProviderHint: TextView
    private lateinit var tvKeyState: TextView
    private lateinit var tvTempLabel: TextView
    private lateinit var tvStepsLabel: TextView
    private lateinit var tvLog: TextView
    private lateinit var inputKey: EditText
    private lateinit var inputBase: EditText
    private lateinit var inputModel: EditText
    private lateinit var seekTemp: SeekBar
    private lateinit var seekSteps: SeekBar
    private lateinit var switchVoice: MaterialSwitch
    private lateinit var switchHaptics: MaterialSwitch

    private var selectedProvider: String = ProviderIds.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        container = AppContainer.get(this)
        selectedProvider = container.vault.providerId

        bind()
        buildProviderChips()
        restore()
        wire()
    }

    private fun bind() {
        providerRow = findViewById(R.id.providerRow)
        tvProviderHint = findViewById(R.id.tvProviderHint)
        tvKeyState = findViewById(R.id.tvKeyState)
        tvTempLabel = findViewById(R.id.tvTempLabel)
        tvStepsLabel = findViewById(R.id.tvStepsLabel)
        tvLog = findViewById(R.id.tvLog)
        inputKey = findViewById(R.id.inputKey)
        inputBase = findViewById(R.id.inputBase)
        inputModel = findViewById(R.id.inputModel)
        seekTemp = findViewById(R.id.seekTemp)
        seekSteps = findViewById(R.id.seekSteps)
        switchVoice = findViewById(R.id.switchVoice)
        switchHaptics = findViewById(R.id.switchHaptics)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun buildProviderChips() {
        providerRow.removeAllViews()
        ModelCatalog.descriptors.forEach { descriptor ->
            val chip = TextView(this)
            chip.text = descriptor.label
            chip.textSize = 12f
            chip.setPadding(dp(14), dp(9), dp(14), dp(9))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = dp(8)
            chip.layoutParams = params
            chip.setOnClickListener {
                selectedProvider = descriptor.id
                refreshProviderUi()
                Motion.pulse(it)
                container.haptics.tap(it)
            }
            providerRow.addView(chip)
        }
        refreshProviderUi()
    }

    private fun refreshProviderUi() {
        ModelCatalog.descriptors.forEachIndexed { index, descriptor ->
            val chip = providerRow.getChildAt(index) as? TextView ?: return@forEachIndexed
            val active = descriptor.id == selectedProvider
            chip.setBackgroundResource(if (active) R.drawable.bg_chip_active else R.drawable.bg_chip)
            chip.setTextColor(
                ContextCompat.getColor(this, if (active) R.color.apex_text else R.color.apex_text_dim)
            )
        }
        val descriptor = ModelCatalog.find(selectedProvider)
        tvProviderHint.text = descriptor.hint

        val stored = container.vault.getApiKey(selectedProvider)
        tvKeyState.text = if (stored != null) {
            "Key stored · ${Redactor.fingerprint(stored)} · ${if (container.vault.isHardwareBacked) "hardware-backed" else "software only"}"
        } else {
            "No key stored for this provider"
        }

        inputKey.setText("")
        val sameProvider = container.vault.providerId == selectedProvider
        inputBase.setText(if (sameProvider) container.vault.baseUrl else descriptor.defaultBaseUrl)
        inputModel.setText(if (sameProvider) container.vault.modelId else "")
        inputModel.hint = "Model id · e.g. ${descriptor.models.firstOrNull() ?: "default"}"
        inputKey.isEnabled = descriptor.needsKey
    }

    private fun restore() {
        seekTemp.progress = (container.vault.temperature * 10f).toInt().coerceIn(0, 20)
        seekSteps.progress = (container.vault.maxAgentSteps - 1).coerceIn(0, 23)
        switchVoice.isChecked = container.vault.voiceEnabled
        switchHaptics.isChecked = container.vault.hapticsEnabled
        updateTempLabel()
        updateStepsLabel()
        renderLog()
    }

    private fun wire() {
        seekTemp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateTempLabel()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        seekSteps.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateStepsLabel()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave).setOnClickListener {
            save()
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClear).setOnClickListener {
            confirmWipe()
        }
    }

    private fun updateTempLabel() {
        tvTempLabel.text = String.format(Locale.US, "Creativity %.1f", seekTemp.progress / 10f)
    }

    private fun updateStepsLabel() {
        tvStepsLabel.text = "Agent step budget ${seekSteps.progress + 1}"
    }

    private fun renderLog() {
        val lines = ApexLog.snapshot().takeLast(80)
        tvLog.text = if (lines.isEmpty()) "No engine events yet." else lines.joinToString("\n")
    }

    private fun save() {
        val key = inputKey.text.toString().trim()
        if (key.isNotEmpty()) {
            container.vault.saveApiKey(selectedProvider, key)
            ApexLog.d("Settings", "key updated for $selectedProvider")
        }
        container.vault.providerId = selectedProvider
        container.vault.baseUrl = inputBase.text.toString().trim()
        container.vault.modelId = inputModel.text.toString().trim()
        container.vault.temperature = seekTemp.progress / 10f
        container.vault.maxAgentSteps = seekSteps.progress + 1
        container.vault.voiceEnabled = switchVoice.isChecked
        container.vault.hapticsEnabled = switchHaptics.isChecked
        container.haptics.enabled = switchHaptics.isChecked
        container.haptics.success()

        val configured = container.providers.isConfigured()
        Toast.makeText(
            this,
            if (configured) "Configuration saved" else "Saved — this provider still needs a key",
            Toast.LENGTH_SHORT
        ).show()
        if (configured) finish()
    }

    private fun confirmWipe() {
        AlertDialog.Builder(this, R.style.Apex_Dialog)
            .setTitle("Wipe everything?")
            .setMessage("This deletes the stored API keys and the whole conversation history from this device.")
            .setPositiveButton("Wipe") { _, _ ->
                container.vault.clearVault()
                container.sessions.clear()
                Toast.makeText(this, "Credentials and history wiped", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        renderLog()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
