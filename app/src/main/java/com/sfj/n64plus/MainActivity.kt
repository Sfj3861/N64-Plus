package com.sfj.n64plus

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val registry = EngineRegistry(mapOf(
            Console.N64 to { N64Engine() },
            Console.SNES to { SnesEngine() },
            Console.WII to { DolphinEngine() },
            Console.GAMECUBE to { DolphinEngine() }
        ))
        val text = TextView(this).apply {
            text = "N64 Plus\n\nEngine registry wired.\nNative emulator integration is still pending."
            textSize = 22f
            setPadding(32, 32, 32, 32)
        }
        setContentView(text)
    }
}