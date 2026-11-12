package com.apex.ai.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.apex.ai.R
import com.apex.ai.core.AppFacts
import com.apex.ai.core.AppContainer

/**
 * Offline help, shipped as real HTML/CSS/JS inside the APK.
 *
 * A WebView for a local asset is the right tool here: the content is documents,
 * tables and a filter box, and writing that as native views would be four times
 * the code for a worse result. JavaScript is enabled because the page uses it;
 * navigation to anything remote is blocked in the page itself and in this class.
 */
class AboutActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // The Java platform layer, rendered as plain text above the page.
        val facts = AppFacts.self(this)
        findViewById<TextView>(R.id.tvFacts).text = buildString {
            appendLine("package ${facts["package"] ?: "?"} · v${facts["versionName"] ?: "?"} (${facts["versionCode"] ?: "?"})")
            appendLine("sdk ${facts["minSdk"] ?: "?"} → ${facts["targetSdk"] ?: "?"} · ${facts["abis"] ?: "?"}")
            appendLine("installer ${facts["installer"] ?: "?"}")
            append("signature ${(facts["signingSha256"] ?: "?").take(24)}…")
        }

        val web = findViewById<WebView>(R.id.webHelp)
        web.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            domStorageEnabled = false
            setSupportZoom(false)
        }
        web.setBackgroundColor(0xFF06080F.toInt())
        web.loadUrl("file:///android_asset/help/index.html")
    }

    override fun onDestroy() {
        runCatching { findViewById<WebView>(R.id.webHelp).destroy() }
        super.onDestroy()
    }
}
