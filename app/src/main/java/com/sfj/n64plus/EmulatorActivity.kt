package com.sfj.n64plus

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class EmulatorActivity : Activity() {
    companion object {
        const val EXTRA_GAME_URI = "game_uri"
        const val EXTRA_GAME_TITLE = "game_title"
        const val EXTRA_GAME_EXTENSION = "game_extension"
        init { System.loadLibrary("n64plus_runtime") }
    }

    private external fun nativeProbeCore(path: String): String
    private external fun nativeCloseCore()
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var closing = false
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        message = TextView(this).apply {
            setBackgroundColor(Color.rgb(13, 18, 30))
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 32, 28, 32)
            text = "Preparing game…"
        }
        setContentView(LinearLayout(this).apply {
            setBackgroundColor(Color.rgb(13, 18, 30))
            addView(message, LinearLayout.LayoutParams(-1, -1))
        })

        val uri = intent.getStringExtra(EXTRA_GAME_URI)?.let(Uri::parse)
        val title = intent.getStringExtra(EXTRA_GAME_TITLE) ?: "Selected game"
        val extension = intent.getStringExtra(EXTRA_GAME_EXTENSION)?.lowercase().orEmpty()
        if (uri == null) {
            showStatus("Cannot open game: selected file URI was not provided.")
            return
        }

        io.execute {
            try {
                val staged = stageContent(uri, extension)
                if (closing || isFinishing) return@execute
                val core = File(filesDir, "cores/libretro_snes.so")
                val result = if (core.isFile) {
                    nativeProbeCore(core.absolutePath)
                } else {
                    "Game copied successfully to private app storage.\n\nSNES core not found at:\n${core.absolutePath}\n\nA compatible core is required before emulation can continue."
                }
                showStatus("GAME: $title\n\nCONTENT: ${staged.name} (${staged.length()} bytes)\n\n$result\n\nPlayback is not active yet: Libretro game loading, video, audio and input are still pending.")
            } catch (e: Exception) {
                showStatus("Could not prepare game content.\n\n${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    @Throws(IOException::class)
    private fun stageContent(uri: Uri, extension: String): File {
        val directory = File(cacheDir, "content").apply {
            if (!exists() && !mkdirs()) throw IOException("Could not create private content directory.")
        }
        val safeExtension = extension.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "rom"
        val target = File.createTempFile("snes_", ".$safeExtension", directory)
        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw IOException("Android could not open the selected file.")
            input.use { source -> target.outputStream().use { destination -> source.copyTo(destination) } }
            if (target.length() <= 0L) throw IOException("The selected file is empty.")
            return target
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    private fun showStatus(text: String) {
        runOnUiThread { if (!closing && ::message.isInitialized) message.text = text }
    }

    override fun onDestroy() {
        closing = true
        io.shutdownNow()
        nativeCloseCore()
        super.onDestroy()
    }
}