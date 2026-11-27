package com.sfj.n64plus

/**
 * Narrow JNI boundary for Libretro cores. Implementations must not assume a core
 * is available until the native library/core loader reports success.
 */
interface LibretroBridge {
    fun loadCore(corePath: String): Boolean
    fun setEnvironment(videoWidth: Int, videoHeight: Int, sampleRate: Int)
    fun loadContent(contentPath: String): Boolean
    fun runFrame()
    fun setInput(player: Int, port: Int, control: Int, value: Int)
    fun unloadContent()
    fun unloadCore()
}

/** Placeholder bridge: intentionally fails closed until native JNI is implemented. */
class StubLibretroBridge : LibretroBridge {
    override fun loadCore(corePath: String) = false
    override fun setEnvironment(videoWidth: Int, videoHeight: Int, sampleRate: Int) = Unit
    override fun loadContent(contentPath: String) = false
    override fun runFrame() = Unit
    override fun setInput(player: Int, port: Int, control: Int, value: Int) = Unit
    override fun unloadContent() = Unit
    override fun unloadCore() = Unit
}