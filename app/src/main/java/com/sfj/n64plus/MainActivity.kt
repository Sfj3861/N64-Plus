package com.sfj.n64plus

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
    private var selectedGame: GameEntry? = null
    private lateinit var folderDisplay: TextView
    private lateinit var games: LinearLayout
    private lateinit var status: TextView
    private lateinit var systems: LinearLayout
    private val bg = Color.rgb(10, 14, 25)
    private val panel = Color.rgb(22, 29, 46)
    private val accent = Color.rgb(108, 139, 255)
    private val muted = Color.rgb(166, 179, 205)

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi() }
    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(14)); setBackgroundColor(bg) }
        val scroll = ScrollView(this); val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; scroll.addView(content)
        content.addView(label("N64_P", 34, Color.WHITE, true))
        content.addView(label("YOUR RETRO LIBRARY", 12, accent, true))
        content.addView(space(12))
        content.addView(panelCard().apply { addView(label("WELCOME BACK", 12, muted, true)); addView(label("Play your classics.", 22, Color.WHITE, true)); addView(label("Organize your games and build your library.", 14, muted)) })
        content.addView(space(16)); content.addView(label("SYSTEMS", 14, muted, true))
        systems = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; content.addView(systems); refreshSystems()
        content.addView(space(14)); content.addView(label("GAME LIBRARY", 18, Color.WHITE, true))
        content.addView(panelCard().apply { folderDisplay = label("Folder not selected", 14, muted); addView(folderDisplay); addView(button("＋  Select library folder", accent) { browseFolder() }); addView(button("↻  Refresh library", Color.rgb(49, 64, 91)) { scanFolder() }) })
        content.addView(space(8)); games = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; content.addView(games)
        content.addView(space(8)); content.addView(button("▶  Play selected game", Color.rgb(43, 160, 117)) { launchSelected() })
        content.addView(space(16)); content.addView(panelCard().apply { addView(label("STATUS", 12, accent, true)); status = label("Select Super Nintendo and add your game folder.", 14, muted); addView(status) })
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root); refreshFolder()
    }
    private fun refreshSystems() {
        systems.removeAllViews()
        consoles.forEach { name -> val selected = selectedConsole == name
            systems.addView(button((if (selected) "◆   " else "◇   ") + name, if (selected) accent else panel) {
                selectedConsole = name; selectedGame = null; refreshSystems(); refreshFolder(); games.removeAllViews(); status.text = "$name selected. Choose a folder to scan."
            })
        }
    }
    private fun storedFolder(console: String): Uri? = prefs.getString(console, null)?.let(Uri::parse)
    private fun refreshFolder() { val uri = storedFolder(selectedConsole); folderDisplay.text = uri?.let { "Library folder assigned\n$it" } ?: "No folder assigned to $selectedConsole" }
    private fun browseFolder() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 101) }
    @Deprecated("Legacy result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) data?.data?.let { uri ->
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: SecurityException) {}
            prefs.edit().putString(selectedConsole, uri.toString()).apply(); refreshFolder(); scanFolder()
        }
    }
    private fun scanFolder() {
        games.removeAllViews(); selectedGame = null
        val folder = storedFolder(selectedConsole)
        if (folder == null) { status.text = "Select a folder first."; return }
        if (selectedConsole != "Super Nintendo") { status.text = "The SNES library scanner is currently the only scanner implemented."; return }
        val entries = SnesLibraryScanner(this).scan(folder)
        if (entries.isEmpty()) { status.text = "No supported files found (.sfc, .smc, .fig, .swc, .zip)."; return }
        status.text = "${entries.size} title(s) found. Tap a game to select it."
        entries.forEach { game -> games.addView(panelCard().apply {
            addView(label("🎮  ${game.title}", 16, Color.WHITE, true)); addView(label("${game.extension.uppercase()}  •  ${formatSize(game.size)}", 12, muted))
            addView(button("Select game", Color.rgb(49, 64, 91)) { selectedGame = game; status.text = "Selected: ${game.title}" })
        }) }
    }
    private fun launchSelected() { val game = selectedGame ?: run { Toast.makeText(this, "Select a game first.", Toast.LENGTH_SHORT).show(); return }
        status.text = "Launch backend is not yet complete. ${game.title} is selected."; Toast.makeText(this, "SNES playback engine integration is still in development.", Toast.LENGTH_LONG).show()
    }
    private fun panelCard() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(panel); val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(5), 0, dp(5)); layoutParams = lp }
    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply { this.text = text; textSize = size.toFloat(); setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD; setPadding(dp(3), dp(5), dp(3), dp(5)) }
    private fun button(text: String, color: Int, action: () -> Unit) = Button(this).apply { this.text = text; setTextColor(Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(color); setAllCaps(false); gravity = Gravity.CENTER; setOnClickListener { action() }; val p = LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(4), 0, dp(4)); layoutParams = p }
    private fun space(n: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(n)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun formatSize(bytes: Long): String = if (bytes < 1024) "$bytes B" else String.format("%.1f MB", bytes / 1048576.0)
}