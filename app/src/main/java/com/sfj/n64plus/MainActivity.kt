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

class MainActivity : Activity() {
    private val consoles = listOf("Nintendo 64", "Super Nintendo", "Wii", "GameCube")
    private val prefs by lazy { getSharedPreferences("console_folders", MODE_PRIVATE) }
    private var selectedConsole = "Super Nintendo"
    private var selectedFolder: Uri? = null
    private var selectedGame: GameEntry? = null
    private lateinit var status: TextView
    private lateinit var folderDisplay: TextView
    private lateinit var consoleList: LinearLayout
    private lateinit var gameList: LinearLayout
    private lateinit var folderButton: Button

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi() }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 24, 22, 20); setBackgroundColor(Color.rgb(13, 18, 30)) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        content.addView(label("N64 PLUS", 30, Color.WHITE, true))
        content.addView(label("Game library • prototype", 16, Color.rgb(174, 190, 215)))
        content.addView(space(12))
        content.addView(label("Systems", 20, Color.WHITE, true))
        consoleList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; content.addView(consoleList); refreshConsoleCards()
        content.addView(space(12)); content.addView(label("Library folder", 20, Color.WHITE, true))
        folderDisplay = label("No folder selected", 14, Color.rgb(210, 220, 235)); content.addView(folderDisplay)
        folderButton = button("Choose folder", Color.rgb(62, 126, 238)) { browseFolder() }; content.addView(folderButton)
        content.addView(button("↻   Scan folder", Color.rgb(50, 90, 130)) { scanFolder() })
        content.addView(space(10)); content.addView(label("Detected games", 20, Color.WHITE, true))
        gameList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; content.addView(gameList)
        content.addView(space(14)); content.addView(button("▶   Launch selected", Color.rgb(42, 168, 120)) { launchSelected() })
        content.addView(space(20)); content.addView(label("Status", 20, Color.WHITE, true))
        status = label("Choose the SNES folder to scan it.", 15, Color.rgb(210, 220, 235)); content.addView(status)
        content.addView(space(14)); content.addView(label("SNES engine integration is not yet connected. This build detects games and prepares selection; it does not claim to emulate them yet.", 13, Color.rgb(174, 190, 215)))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root); refreshFolderDisplay()
    }

    private fun refreshConsoleCards() {
        consoleList.removeAllViews()
        consoles.forEach { name -> consoleList.addView(button((if (selectedConsole == name) "●  " else "○  ") + name, if (selectedConsole == name) Color.rgb(48, 86, 155) else Color.rgb(35, 44, 62)) {
            selectedConsole = name; selectedGame = null; refreshConsoleCards(); refreshFolderDisplay(); gameList.removeAllViews()
            status.text = "$name selected. Choose its folder and scan."
        }) }
    }

    private fun storedFolder(console: String): Uri? = prefs.getString(console, null)?.let(Uri::parse)
    private fun refreshFolderDisplay() {
        selectedFolder = storedFolder(selectedConsole)
        folderDisplay.text = selectedFolder?.toString() ?: "No folder assigned to $selectedConsole"
        folderButton.text = "Choose $selectedConsole folder"
    }
    private fun browseFolder() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 101) }

    @Deprecated("Legacy result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) data?.data?.let { uri ->
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: SecurityException) { }
            prefs.edit().putString(selectedConsole, uri.toString()).apply()
            selectedFolder = uri; refreshFolderDisplay(); scanFolder()
        }
    }

    private fun scanFolder() {
        gameList.removeAllViews(); selectedGame = null
        val folder = storedFolder(selectedConsole)
        if (folder == null) { status.text = "Choose a folder first."; return }
        if (selectedConsole != "Super Nintendo") {
            status.text = "Detection is currently implemented for SNES only. Wii, GameCube and N64 are pending."
            return
        }
        val games = SnesLibraryScanner(this).scan(folder)
        if (games.isEmpty()) { status.text = "No supported SNES files found (.sfc, .smc, .fig, .swc, .zip)."; return }
        status.text = "Found ${games.size} game(s). Select one below."
        games.forEach { game ->
            gameList.addView(button(game.title + "   (" + game.extension.uppercase() + ")", Color.rgb(35, 44, 62)) {
                selectedGame = game; status.text = "Selected: ${game.title}\nURI: ${game.uri}"
            })
        }
    }

    private fun launchSelected() {
        val game = selectedGame
        if (game == null) { Toast.makeText(this, "Select a detected game first.", Toast.LENGTH_SHORT).show(); return }
        status.text = "Ready to load ${game.title}. The Libretro SNES core is not yet wired to the Android runtime."
        Toast.makeText(this, "Game detected and selected. Emulator core integration remains pending.", Toast.LENGTH_LONG).show()
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD); setPadding(4, 8, 4, 8)
    }
    private fun button(text: String, color: Int, action: () -> Unit) = Button(this).apply {
        this.text = text; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(color))
        setAllCaps(false); gravity = Gravity.CENTER; setOnClickListener { action() }
        val p = LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 4, 0, 4); layoutParams = p
    }
    private fun space(dp: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, (dp * resources.displayMetrics.density).toInt()) }
}
