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

/** Console-specific folder library. Launch remains gated until native backends are available. */
class MainActivity : Activity() {
    private val consoles = listOf("Nintendo 64", "Super Nintendo", "Wii", "GameCube")
    private val folderPrefs by lazy { getSharedPreferences("console_folders", MODE_PRIVATE) }
    private var selectedConsole = "Nintendo 64"
    private var selectedFolder: Uri? = null
    private lateinit var status: TextView
    private lateinit var folderDisplay: TextView
    private lateinit var consoleList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi() }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 28, 24, 24); setBackgroundColor(Color.rgb(13, 18, 30)) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        content.addView(label("N64 PLUS", 30, Color.WHITE, true))
        content.addView(label("Your games, one library.", 16, Color.rgb(174, 190, 215)))
        content.addView(space(18))
        content.addView(label("Choose a system", 20, Color.WHITE, true))
        consoleList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; content.addView(consoleList); refreshConsoleCards()
        content.addView(space(16)); content.addView(label("Game library folder", 20, Color.WHITE, true))
        folderDisplay = label("No folder selected for " + selectedConsole, 16, Color.rgb(210, 220, 235)); content.addView(folderDisplay)
        content.addView(button("Choose " + selectedConsole + " folder", Color.rgb(62, 126, 238)) { browseFolder() })
        content.addView(space(12)); content.addView(label("Each console has its own folder. Select a folder containing that system's games.", 14, Color.rgb(174, 190, 215)))
        content.addView(space(16)); content.addView(button("▶   Launch game", Color.rgb(42, 168, 120)) { launch() })
        content.addView(space(20)); content.addView(label("Status", 20, Color.WHITE, true))
        status = label("Select a console and assign its games folder.", 15, Color.rgb(210, 220, 235)); content.addView(status)
        content.addView(space(20)); content.addView(label("About this build", 18, Color.WHITE, true))
        content.addView(label("Folder selection is implemented using Android's Storage Access Framework. Game scanning and native execution are still pending.", 14, Color.rgb(174, 190, 215)))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root); refreshFolderDisplay()
    }

    private fun refreshConsoleCards() {
        consoleList.removeAllViews()
        consoles.forEach { name -> consoleList.addView(button((if (selectedConsole == name) "●  " else "○  ") + name, if (selectedConsole == name) Color.rgb(48, 86, 155) else Color.rgb(35, 44, 62)) {
            selectedConsole = name; selectedFolder = storedFolder(name); refreshConsoleCards(); refreshFolderDisplay(); status.text = name + " selected. Assign or use its folder."
        }) }
    }

    private fun storedFolder(console: String): Uri? = folderPrefs.getString(console, null)?.let { Uri.parse(it) }
    private fun refreshFolderDisplay() { selectedFolder = storedFolder(selectedConsole); folderDisplay.text = selectedFolder?.let { "Folder assigned to " + selectedConsole + ":\n" + it } ?: "No folder selected for " + selectedConsole }
    private fun browseFolder() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 101) }

    @Deprecated("Legacy activity result used for dependency-free scaffold")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) data?.data?.let { uri ->
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: SecurityException) { }
            folderPrefs.edit().putString(selectedConsole, uri.toString()).apply(); selectedFolder = uri; refreshFolderDisplay()
            status.text = "Folder saved for " + selectedConsole + ". Game discovery is not yet implemented."
        }
    }

    private fun launch() {
        if (selectedFolder == null) { Toast.makeText(this, "Choose a folder for " + selectedConsole + " first.", Toast.LENGTH_SHORT).show(); return }
        status.text = "Launch unavailable: the " + selectedConsole + " engine and folder scanning are not yet implemented."
        Toast.makeText(this, "Emulator engine not connected yet.", Toast.LENGTH_LONG).show()
    }
    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply { this.text = text; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(null, android.graphics.Typeface.BOLD); setPadding(4, 8, 4, 8) }
    private fun button(text: String, color: Int, action: () -> Unit) = Button(this).apply { this.text = text; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(color)); setAllCaps(false); gravity = Gravity.CENTER; setOnClickListener { action() }; val params = LinearLayout.LayoutParams(-1, -2); params.setMargins(0, 5, 0, 5); layoutParams = params }
    private fun space(dp: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, (dp * resources.displayMetrics.density).toInt()) }
}
