package com.sfj.n64plus

enum class Console { GAMECUBE, WII, N64, SNES }
enum class EngineState { IDLE, INITIALIZING, READY, RUNNING, PAUSED, STOPPING, FAILED }

interface EmulatorEngine {
    val console: Console
    fun initialize(): Result<Unit>
    fun loadGame(uri: String): Result<Unit>
    fun start(): Result<Unit>
    fun pause(): Result<Unit>
    fun resume(): Result<Unit>
    fun stop()
}

/** Engines are selected lazily; no native engine is loaded at app startup. */
class EngineRegistry(private val factories: Map<Console, () -> EmulatorEngine>) {
    fun create(console: Console): EmulatorEngine? = factories[console]?.invoke()
}