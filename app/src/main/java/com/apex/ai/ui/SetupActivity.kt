package com.apex.ai.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.apex.ai.R
import com.apex.ai.core.AppContainer
import com.apex.ai.core.PackManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one-tap installer.
 *
 * Downloads every pack listed in the manifest, verifies each against its
 * published SHA-256, and writes it to app storage. A failed checksum aborts the
 * whole install rather than leaving half-verified content behind.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var container: AppContainer
    private lateinit var packs: PackManager
    private lateinit var progress: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var btnDownload: MaterialButton
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        container = AppContainer.get(this)
        packs = PackManager(this)

        progress = findViewById(R.id.progress)
        tvStatus = findViewById(R.id.tvStatus)
        btnDownload = findViewById(R.id.btnDownload)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnSkip).setOnClickListener { finish() }
        btnDownload.setOnClickListener { startInstall() }

        refreshState()
    }

    private fun refreshState() {
        if (packs.isInstalled()) {
            val kb = packs.installedSizeBytes() / 1024
            tvStatus.text = "Installed · ${packs.installedCount()} files · ${kb} KB on this device"
            progress.progress = 100
            btnDownload.text = "Reinstall packs"
        } else {
            tvStatus.text = "Not installed yet."
            progress.progress = 0
            btnDownload.text = "Download all packs"
        }
    }

    private fun startInstall() {
        if (job?.isActive == true) return
        if (!container.network.isOnline()) {
            toast("No network connection")
            return
        }
        btnDownload.isEnabled = false
        job = lifecycleScope.launch {
            val result = packs.installAll { fraction, label ->
                runOnUiThread {
                    progress.progress = (fraction * 100).toInt().coerceIn(0, 100)
                    tvStatus.text = label
                }
            }
            btnDownload.isEnabled = true
            when (result) {
                is com.apex.ai.core.Outcome.Ok -> {
                    container.haptics.success()
                    progress.progress = 100
                    tvStatus.text = "Installed ${result.value} pack(s) · verified"
                    btnDownload.text = "Reinstall packs"
                    toast("Packs installed")
                }
                is com.apex.ai.core.Outcome.Err -> {
                    container.haptics.warn()
                    tvStatus.text = "Install failed: ${result.message}"
                    toast("Install failed: ${result.message}")
                }
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }
}
