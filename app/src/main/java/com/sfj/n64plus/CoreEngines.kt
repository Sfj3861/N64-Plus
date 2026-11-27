package com.sfj.n64plus

/**
 * Initial engine adapters only establish lifecycle/API wiring.
 * They do not yet execute imported native emulator code.
 */
abstract class LibretroEngineAdapter(
    override val console: Console,
    protected val bridge: LibretroBridge
) : EmulatorEngine {
    override fun initialize() = Result.failure<Unit>(UnsupportedOperationException("Native core not connected yet"))
    override fun loadGame(uri: String) = Result.failure<Unit>(UnsupportedOperationException("Engine initialization required"))
    override fun start() = Result.failure<Unit>(UnsupportedOperationException("Engine initialization required"))
    override fun pause() = Result.failure<Unit>(UnsupportedOperationException("Engine initialization required"))
    override fun resume() = Result.failure<Unit>(UnsupportedOperationException("Engine initialization required"))
    override fun stop() { bridge.unloadContent(); bridge.unloadCore() }
}

class N64Engine(bridge: LibretroBridge = StubLibretroBridge()) : LibretroEngineAdapter(Console.N64, bridge)
class SnesEngine(bridge: LibretroBridge = StubLibretroBridge()) : LibretroEngineAdapter(Console.SNES, bridge)

/** Dolphin needs its Android/native build pipeline and host surface integration. */
class DolphinEngine : EmulatorEngine {
    override val console = Console.WII
    override fun initialize() = Result.failure<Unit>(UnsupportedOperationException("Dolphin JNI adapter pending"))
    override fun loadGame(uri: String) = Result.failure<Unit>(UnsupportedOperationException("Dolphin JNI adapter pending"))
    override fun start() = Result.failure<Unit>(UnsupportedOperationException("Dolphin JNI adapter pending"))
    override fun pause() = Result.failure<Unit>(UnsupportedOperationException("Dolphin JNI adapter pending"))
    override fun resume() = Result.failure<Unit>(UnsupportedOperationException("Dolphin JNI adapter pending"))
    override fun stop() = Unit
}