package com.sfj.n64plus

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Lightweight, dependency-free library UI. Engine execution is intentionally gated until native backends exist. */
class MainActivity : Activity() {
    private val consoles = listOf("Nintendo 64", "Super Nintendo", "Wii", "GameCube")
    private val extensions = mapOf(
        "Nintendo 64" to "N64 ROMs (.n64, .v64, .z64)",
        "Super Nintendo" to "SNES ROMs (.sfc, .smc)",
        "Wii" to "Wii games (supported formats to be wired)",
        "GameCube" to "GameCube games (supported formats to be wired)"
    )
    private var selectedConsole = "Nintendo 64"
    private var selectedUri: Uri? = null
    private lateinit var status: TextView
    private lateinit var selectedGame: TextView
    private lateinit var consoleList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(13, 18, 30))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        content.addView(label("N64 PLUS", 30, Color.WHITE, true))
        content.addView(label("Your games, one library.", 16, Color.rgb(174, 190, 215)))
        content.addView(space(18))

        content.addView(label("Choose a system", 20, Color.WHITE, true))
        consoleList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(consoleList)
        refreshConsoleCards()

        content.addView(space(16))
        content.addView(label("Game library", 20, Color.WHITE, true))
        selectedGame = label("No game selected", 16, Color.rgb(210, 220, 235))
        content.addView(selectedGame)
        content.addView(button("Browse files", Color.rgb(62, 126, 238)) { browse() })
        content.addView(space(12))
        content.addView(label("Selected system", 14, Color.rgb(174, 190, 215)))
        content.addView(label(extensions[selectedConsole] ?: "", 14, Color.LTGRAY))
        content.addView(space(16))

        content.addView(button("▶   Launch game", Color.rgb(42, 168, 120)) { launch() })
        content.addView(space(20))
        content.addView(label("Status", 20, Color.WHITE, true))
        status = label("Select a system and a game file to get started.", 15, Color.rgb(210, 220, 235))
        content.addView(status)
        content.addView(space(20))
        content.addView(label("About this build", 18, Color.WHITE, true))
        content.addView(label("Friendly UI prototype. Native emulator engines are not yet connected, so launching is currently disabled with an explanation.", 14, Color.rgb(174, 190, 215)))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun refreshConsoleCards() {
        consoleList.removeAllViews()
        consoles.forEach { name ->
            val card = button((if (selectedConsole == name) "●  " else "○  ") + name, if (selectedConsole == name) Color.rgb(48, 86, 155) else Color.rgb(35, 44, 62)) {
                selectedConsole = name
                selectedUri = null
                selectedGame.text = "No game selected"
                refreshConsoleCards()
                status.text = "System selected: $name. Choose a game file."
            }
            consoleList.addView(card)
        }
    }

    private fun browse() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, 100)
    }

    @Deprecated("Legacy activity result used to keep this scaffold dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                selectedUri = uri
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectedGame.text = uri.lastPathSegment ?: uri.toString()
                status.text = "File selected. The engine for $selectedConsole is not yet implemented."
            }
        }
    }

    private fun launch() {
        if (selectedUri == null) {
            Toast.makeText(this, "Choose a game file first.", Toast.LENGTH_SHORT).show()
            return
        }
        status.text = "Launch unavailable: the $selectedConsole engine is still a development stub. Your file has not been run."
        Toast.makeText(this, "Emulator engine not connected yet.", Toast.LENGTH_LONG).show()
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(4, 8, 4, 8)
    }

    private fun button(text: String, color: Int, action: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(color))
        setAllCaps(false)
        gravity = Gravity.CENTER
        setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, 5, 0, 5)
        layoutParams = params
    }

    private fun space(dp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp * resources.displayMetrics.density.toInt())
    }
}
