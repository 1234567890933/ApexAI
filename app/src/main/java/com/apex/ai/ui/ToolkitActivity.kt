package com.apex.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apex.ai.R
import com.apex.ai.core.AppContainer
import com.apex.ai.sandbox.SandboxFs
import com.apex.ai.sandbox.importFromUri
import com.apex.ai.tools.Tool
import com.apex.ai.tools.ToolCall
import com.apex.ai.tools.ToolCategory
import com.apex.ai.tools.ToolResult
import com.apex.ai.ui.motion.Motion
import com.apex.ai.ui.toolkit.ToolAdapter
import kotlinx.coroutines.launch

/**
 * Direct, human-driven access to the same tool runtime the agents use. Useful
 * both as a utility belt and as a way to see exactly what an agent can do.
 */
class ToolkitActivity : AppCompatActivity() {

    private lateinit var container: AppContainer
    private lateinit var sandbox: SandboxFs
    private lateinit var recycler: RecyclerView
    private lateinit var categoryRow: LinearLayout
    private lateinit var adapter: ToolAdapter

    private var activeCategory: ToolCategory? = null

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                runCatching { sandbox.importFromUri(this@ToolkitActivity, uri) }
                    .onSuccess { name -> toast("Imported $name into the sandbox") }
                    .onFailure { toast("Import failed: ${it.message}") }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_toolkit)

        container = AppContainer.get(this)
        sandbox = SandboxFs(this)

        recycler = findViewById(R.id.recyclerTools)
        categoryRow = findViewById(R.id.categoryRow)

        adapter = ToolAdapter(container.tools.all()) { tool -> openTool(tool) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnImport).setOnClickListener {
            container.haptics.tap(it)
            importLauncher.launch(arrayOf("*/*"))
        }

        buildCategoryChips()
    }

    private fun buildCategoryChips() {
        categoryRow.removeAllViews()
        val entries = mutableListOf<Pair<String, ToolCategory?>>("All" to null)
        ToolCategory.values().forEach { entries.add(label(it) to it) }

        entries.forEach { (label, category) ->
            val chip = TextView(this)
            chip.text = label
            chip.textSize = 12f
            chip.setPadding(dp(14), dp(9), dp(14), dp(9))
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = dp(8)
            chip.layoutParams = params
            chip.setBackgroundResource(if (category == activeCategory) R.drawable.bg_chip_active else R.drawable.bg_chip)
            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (category == activeCategory) R.color.apex_text else R.color.apex_text_dim
                )
            )
            chip.setOnClickListener {
                activeCategory = category
                Motion.pulse(it)
                applyFilter()
                buildCategoryChips()
            }
            categoryRow.addView(chip)
        }
    }

    private fun applyFilter() {
        val category = activeCategory
        val tools = if (category == null) container.tools.all() else container.tools.byCategory(category)
        adapter.update(tools)
    }

    private fun label(category: ToolCategory): String = when (category) {
        ToolCategory.COMPUTE -> "Compute"
        ToolCategory.TEXT -> "Text"
        ToolCategory.NETWORK -> "Network"
        ToolCategory.SECURITY -> "Security"
        ToolCategory.FILES -> "Files"
        ToolCategory.SYSTEM -> "System"
    }

    private fun openTool(tool: Tool) {
        container.haptics.tap(recycler)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }
        val fields = LinkedHashMap<String, EditText>()

        if (tool.spec.args.isEmpty()) {
            form.addView(hintText("This tool takes no arguments."))
        }
        tool.spec.args.forEach { arg ->
            val caption = TextView(this).apply {
                text = buildString {
                    append(arg.name)
                    append(" · ")
                    append(arg.type)
                    if (arg.required) append(" · required")
                }
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@ToolkitActivity, R.color.apex_text_dim))
                setPadding(0, dp(10), 0, dp(4))
            }
            val input = EditText(this).apply {
                this.hint = arg.description
                textSize = 13f
                setBackgroundResource(R.drawable.bg_input_field)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setTextColor(ContextCompat.getColor(this@ToolkitActivity, R.color.apex_text))
                setHintTextColor(ContextCompat.getColor(this@ToolkitActivity, R.color.apex_text_faint))
            }
            form.addView(caption)
            form.addView(input)
            fields[arg.name] = input
        }

        val scroll = ScrollView(this).apply { addView(form) }

        AlertDialog.Builder(this, R.style.Apex_Dialog)
            .setTitle(tool.spec.name)
            .setView(scroll)
            .setPositiveButton("Run") { _, _ ->
                val args = LinkedHashMap<String, String>()
                fields.forEach { (name, input) ->
                    val value = input.text.toString().trim()
                    if (value.isNotEmpty()) args[name] = value
                }
                execute(tool, args)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun execute(tool: Tool, args: Map<String, String>) {
        val missing = tool.spec.args.filter { it.required && args[it.name].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            toast("Missing: " + missing.joinToString { it.name })
            return
        }
        lifecycleScope.launch {
            val result = container.tools.invoke(
                ToolCall(id = "ui-${System.currentTimeMillis()}", toolId = tool.spec.id, args = args)
            )
            if (result.ok) container.haptics.success() else container.haptics.warn()
            showResult(tool, result)
        }
    }

    private fun showResult(tool: Tool, result: ToolResult) {
        val body = TextView(this).apply {
            text = result.render()
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setTextColor(
                ContextCompat.getColor(
                    this@ToolkitActivity,
                    if (result.ok) R.color.apex_text else R.color.apex_red
                )
            )
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        val scroll = ScrollView(this).apply { addView(body) }

        AlertDialog.Builder(this, R.style.Apex_Dialog)
            .setTitle("${tool.spec.name} · ${result.durationMs}ms")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Apex AI", result.render()))
                toast("Copied")
            }
            .show()
    }

    private fun hintText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(ContextCompat.getColor(this@ToolkitActivity, R.color.apex_text_dim))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
