package com.sfj.n64plus

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.max

class EmulatorActivity : Activity(), SurfaceHolder.Callback {
    companion object {
        const val EXTRA_GAME_URI = "game_uri"
        const val EXTRA_GAME_TITLE = "game_title"
        const val EXTRA_GAME_EXTENSION = "game_extension"
        private const val INPUT_B = 0
        private const val INPUT_Y = 1
        private const val INPUT_SELECT = 2
        private const val INPUT_START = 3
        private const val INPUT_UP = 4
        private const val INPUT_DOWN = 5
        private const val INPUT_LEFT = 6
        private const val INPUT_RIGHT = 7
        private const val INPUT_A = 8
        private const val INPUT_X = 9
        private const val INPUT_L = 10
        private const val INPUT_R = 11
        init { System.loadLibrary("n64plus_runtime") }
    }

    private external fun nativePrepare(corePath: String, gamePath: String, surface: android.view.Surface): Double
    private external fun nativeRun()
    private external fun nativeSetInput(mask: Int)
    private external fun nativeStop()

    private val io = Executors.newSingleThreadExecutor()
    private val inputMask = java.util.concurrent.atomic.AtomicInteger(0)
    private val audioLock = Any()
    @Volatile private var closing = false
    @Volatile private var surfaceReady = false
    @Volatile private var started = false
    @Volatile private var stagedGame: File? = null
    private lateinit var message: TextView
    private lateinit var surfaceView: SurfaceView
    private var audioTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildGameUi()
        surfaceView.holder.addCallback(this)
        val uri = intent.getStringExtra(EXTRA_GAME_URI)?.let(Uri::parse)
        val extension = intent.getStringExtra(EXTRA_GAME_EXTENSION)?.lowercase().orEmpty()
        if (uri == null) {
            setStatus("No game file was supplied.")
            return
        }
        val title = intent.getStringExtra(EXTRA_GAME_TITLE) ?: "SNES game"
        setStatus("Preparing $title…")
        io.execute {
            try {
                val staged = stageContent(uri, extension)
                if (closing) return@execute
                stagedGame = staged
                runOnUiThread {
                    setStatus("ROM ready: $title. Starting emulator…")
                    startIfReady()
                }
            } catch (e: Exception) {
                setStatus("Could not prepare game: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun buildGameUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 14, 25))
            setPadding(8, 8, 8, 8)
        }
        message = TextView(this).apply {
            text = "Waiting for game…"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(8, 6, 8, 6)
        }
        surfaceView = SurfaceView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(message)
        root.addView(surfaceView)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(2, 8, 2, 4)
        }
        controls.addView(makeDpad(), LinearLayout.LayoutParams(0, dp(150), 1.15f))
        controls.addView(makeActionPad(), LinearLayout.LayoutParams(0, dp(150), 1f))
        root.addView(controls)
        val utility = LinearLayout(this).apply { gravity = Gravity.CENTER }
        utility.addView(inputButton("SELECT", INPUT_SELECT), LinearLayout.LayoutParams(0, dp(44), 1f))
        utility.addView(inputButton("START", INPUT_START), LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(utility)
        setContentView(root)
    }

    private fun makeDpad(): View {
        val grid = GridLayout(this).apply { columnCount = 3; rowCount = 3; alignmentMode = GridLayout.ALIGN_BOUNDS }
        val labels = arrayOf("", "▲", "", "◀", "●", "▶", "", "▼", "")
        val ids = intArrayOf(-1, INPUT_UP, -1, INPUT_LEFT, -1, INPUT_RIGHT, -1, INPUT_DOWN, -1)
        for (i in labels.indices) {
            val view = if (ids[i] < 0) TextView(this) else inputButton(labels[i], ids[i])
            val lp = GridLayout.LayoutParams().apply {
                width = 0; height = 0
                columnSpec = GridLayout.spec(i % 3, 1f)
                rowSpec = GridLayout.spec(i / 3, 1f)
                setMargins(2, 2, 2, 2)
            }
            if (view is Button) view.textSize = 21f
            grid.addView(view, lp)
        }
        return grid
    }

    private fun makeActionPad(): View {
        val grid = GridLayout(this).apply { columnCount = 2; rowCount = 3 }
        val items = listOf("Y" to INPUT_Y, "X" to INPUT_X, "B" to INPUT_B, "A" to INPUT_A, "L" to INPUT_L, "R" to INPUT_R)
        items.forEach { (label, id) ->
            val lp = GridLayout.LayoutParams().apply {
                width = 0; height = 0
                columnSpec = GridLayout.spec((grid.childCount) % 2, 1f)
                rowSpec = GridLayout.spec((grid.childCount) / 2, 1f)
                setMargins(3, 3, 3, 3)
            }
            grid.addView(inputButton(label, id), lp)
        }
        return grid
    }

    private fun inputButton(label: String, id: Int) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(48, 62, 91))
        // Each control owns its touch stream. Do not treat POINTER_UP from another
        // finger as this button's release; Android delivers DOWN/UP per touch target.
        var held = false
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    held = true
                    setInput(id, true)
                    view.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (held) setInput(id, false)
                    held = false
                    view.isPressed = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (held) setInput(id, false)
                    held = false
                    view.isPressed = false
                    true
                }
                else -> true
            }
        }
    }

    private fun setInput(id: Int, pressed: Boolean) {
        val bit = 1 shl id
        val previous = inputMask.get()
        val next = if (pressed) previous or bit else previous and bit.inv()
        if (next == previous) return
        inputMask.set(next)
        if (started) nativeSetInput(next)
    }

    private fun releaseAllInputs() {
        inputMask.set(0)
        if (started) nativeSetInput(0)
    }

    private fun startIfReady() {
        if (closing || started || !surfaceReady || stagedGame == null) return
        val game = stagedGame ?: return
        val surface = surfaceView.holder.surface
        if (!surface.isValid) return
        started = true
        setStatus("Loading SNES core…")
        io.execute {
            try {
                val core = File(applicationInfo.nativeLibraryDir, "libretro_snes.so")
                if (!core.isFile) throw IOException("SNES core missing. Expected bundled core: ${core.absolutePath}")
                val sampleRate = nativePrepare(core.absolutePath, game.absolutePath, surface)
                if (sampleRate <= 0.0) throw IOException("The core could not initialize or rejected this ROM. Check that it is an Android ARM64 Libretro SNES core.")
                val rate = sampleRate.toInt().coerceIn(8000, 96000)
                val minBytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(max(minBytes, rate / 5 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                synchronized(audioLock) { audioTrack = track }
                track.play()
                nativeRun()
                nativeSetInput(inputMask.get())
                runOnUiThread { setStatus("PLAYING · SNES · Touch the controls below") }
            } catch (e: Exception) {
                nativeStop()
                started = false
                setStatus("Unable to start: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun onAudioSamples(samples: ShortArray, count: Int) {
        synchronized(audioLock) {
            val track = audioTrack ?: return
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.write(samples, 0, count.coerceAtMost(samples.size), AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startIfReady()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        releaseAllInputs()
        stopRuntime()
    }

    private fun stopRuntime() {
        if (!started) return
        started = false
        io.execute {
            nativeStop()
            synchronized(audioLock) {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.release()
                audioTrack = null
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

    private fun setStatus(text: String) {
        runOnUiThread { if (!closing && ::message.isInitialized) message.text = text }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onPause() {
        releaseAllInputs()
        stopRuntime()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::surfaceView.isInitialized && surfaceView.holder.surface.isValid) {
            surfaceReady = true
            startIfReady()
        }
    }

    override fun onDestroy() {
        closing = true
        releaseAllInputs()
        started = false
        io.execute {
            nativeStop()
            synchronized(audioLock) {
                audioTrack?.release()
                audioTrack = null
            }
        }
        io.shutdown()
        super.onDestroy()
    }
}