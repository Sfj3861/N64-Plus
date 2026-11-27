package com.sfj.n64plus

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.TextView
import java.io.File

class EmulatorActivity : Activity() {
    companion object { init { System.loadLibrary("n64plus_runtime") } }
    private external fun nativeProbeCore(path: String): String
    private external fun nativeCloseCore()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val game = intent.getStringExtra("game_title") ?: "Selected game"
        val core = File(filesDir, "cores/libretro_snes.so")
        val message = if (core.exists()) nativeProbeCore(core.absolutePath)
            else "Game selected: $game\n\nSNES core binary (libretro_snes.so) has not been packaged. This is a runtime scaffold, not playable emulation."
        val view = TextView(this).apply {
            setBackgroundColor(Color.rgb(13,18,30)); setTextColor(Color.WHITE); textSize=18f
            setPadding(32,32,32,32); text = "N64 PLUS — SNES runtime\n\n$message"
        }
        setContentView(view)
    }
    override fun onDestroy() { nativeCloseCore(); super.onDestroy() }
}
