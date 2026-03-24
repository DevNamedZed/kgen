package org.wark.examples.wasm4

import org.wark.HostFunction
import org.wark.WarkImports
import org.wark.WarkInstance

/**
 * WASM-4 fantasy console host implementation.
 *
 * WASM-4 games are tiny .wasm cartridges (max 64KB) that use a fixed
 * 160x160 pixel, 4-color display at 60 Hz. Memory-mapped I/O at fixed
 * addresses in linear memory.
 *
 * Import surface (all from "env" module):
 * - Drawing: blit, blitSub, line, hline, vline, oval, rect, text
 * - Sound: tone
 * - Storage: diskr, diskw
 * - Debug: trace, tracef
 *
 * Game exports: start(), update()
 *
 * @see <a href="https://wasm4.org">wasm4.org</a>
 */
class Wasm4Host(
    private val frameCallback: FrameCallback? = null,
) {
    private var framebuffer = ByteArray(SCREEN_SIZE * SCREEN_SIZE / 4)
    private val storage = ByteArray(1024)
    private var traceOutput: StringBuilder = StringBuilder()

    fun registerImports(builder: WarkImports.Builder) {
        builder.function("env", "blit", blit())
        builder.function("env", "blitSub", blitSub())
        builder.function("env", "line", line())
        builder.function("env", "hline", hline())
        builder.function("env", "vline", vline())
        builder.function("env", "oval", oval())
        builder.function("env", "rect", rect())
        builder.function("env", "text", text())
        builder.function("env", "tone", tone())
        builder.function("env", "diskr", diskr())
        builder.function("env", "diskw", diskw())
        builder.function("env", "trace", trace())
        builder.function("env", "tracef", tracef())
    }

    fun buildImports(): WarkImports {
        val builder = WarkImports.builder()
        registerImports(builder)
        return builder.build()
    }

    fun traceOutput(): String = traceOutput.toString()

    fun framebuffer(): ByteArray = framebuffer

    fun getPixel(x: Int, y: Int): Int {
        if (x < 0 || x >= SCREEN_SIZE || y < 0 || y >= SCREEN_SIZE) {
            return 0
        }
        val index = y * SCREEN_SIZE + x
        val byteIndex = index / 4
        val bitOffset = (index % 4) * 2
        return (framebuffer[byteIndex].toInt() shr bitOffset) and 0x03
    }

    private fun readFramebufferFromMemory(instance: WarkInstance) {
        val memory = instance.memory()
        framebuffer = memory.readBytes(FRAMEBUFFER_ADDRESS, SCREEN_SIZE * SCREEN_SIZE / 4)
    }

    private fun setPixel(x: Int, y: Int, color: Int) {
        if (x < 0 || x >= SCREEN_SIZE || y < 0 || y >= SCREEN_SIZE) {
            return
        }
        val index = y * SCREEN_SIZE + x
        val byteIndex = index / 4
        val bitOffset = (index % 4) * 2
        val mask = (0x03 shl bitOffset).inv()
        framebuffer[byteIndex] = ((framebuffer[byteIndex].toInt() and mask) or ((color and 0x03) shl bitOffset)).toByte()
    }

    private fun rect(): HostFunction = HostFunction { instance, args ->
        val x = args[0].toInt()
        val y = args[1].toInt()
        val width = args[2].toInt()
        val height = args[3].toInt()
        val drawColors = readDrawColors(instance)
        val fillColor = drawColors and 0x0F
        if (fillColor != 0) {
            for (row in y until y + height) {
                for (col in x until x + width) {
                    setPixel(col, row, (fillColor - 1) and 0x03)
                }
            }
        }
        writeFramebufferToMemory(instance)
        longArrayOf()
    }

    private fun line(): HostFunction = HostFunction { instance, args ->
        longArrayOf()
    }

    private fun hline(): HostFunction = HostFunction { instance, args ->
        val x = args[0].toInt()
        val y = args[1].toInt()
        val length = args[2].toInt()
        val drawColors = readDrawColors(instance)
        val color = (drawColors and 0x0F)
        if (color != 0) {
            for (col in x until x + length) {
                setPixel(col, y, (color - 1) and 0x03)
            }
        }
        writeFramebufferToMemory(instance)
        longArrayOf()
    }

    private fun vline(): HostFunction = HostFunction { instance, args ->
        longArrayOf()
    }

    private fun oval(): HostFunction = HostFunction { instance, args ->
        longArrayOf()
    }

    private fun blit(): HostFunction = HostFunction { instance, args ->
        longArrayOf()
    }

    private fun blitSub(): HostFunction = HostFunction { instance, args ->
        longArrayOf()
    }

    private fun text(): HostFunction = HostFunction { instance, args ->
        val stringAddress = args[0].toInt()
        val x = args[1].toInt()
        val y = args[2].toInt()
        val memory = instance.memory()
        val textContent = memory.readUtf8(stringAddress)
        traceOutput.append("[text@($x,$y): $textContent]")
        longArrayOf()
    }

    private fun tone(): HostFunction = HostFunction { instance, args ->
        longArrayOf()
    }

    private fun diskr(): HostFunction = HostFunction { instance, args ->
        val destAddress = args[0].toInt()
        val size = args[1].toInt()
        val memory = instance.memory()
        val readSize = minOf(size, storage.size)
        memory.writeBytes(destAddress, storage.copyOf(readSize))
        longArrayOf(readSize.toLong())
    }

    private fun diskw(): HostFunction = HostFunction { instance, args ->
        val srcAddress = args[0].toInt()
        val size = args[1].toInt()
        val memory = instance.memory()
        val writeSize = minOf(size, storage.size)
        val data = memory.readBytes(srcAddress, writeSize)
        data.copyInto(storage, 0, 0, writeSize)
        longArrayOf(writeSize.toLong())
    }

    private fun trace(): HostFunction = HostFunction { instance, args ->
        val stringAddress = args[0].toInt()
        val memory = instance.memory()
        val message = memory.readUtf8(stringAddress)
        traceOutput.appendLine(message)
        longArrayOf()
    }

    private fun tracef(): HostFunction = HostFunction { instance, args ->
        longArrayOf()
    }

    private fun readDrawColors(instance: WarkInstance): Int {
        return instance.memory().readI32(DRAW_COLORS_ADDRESS) and 0xFFFF
    }

    private fun writeFramebufferToMemory(instance: WarkInstance) {
        instance.memory().writeBytes(FRAMEBUFFER_ADDRESS, framebuffer)
    }

    fun interface FrameCallback {
        fun onFrame(framebuffer: ByteArray, width: Int, height: Int)
    }

    companion object {
        const val SCREEN_SIZE = 160
        const val FRAMEBUFFER_ADDRESS = 0x00A0
        const val DRAW_COLORS_ADDRESS = 0x0014
        const val GAMEPAD1_ADDRESS = 0x0016
        const val MOUSE_X_ADDRESS = 0x001A
        const val MOUSE_Y_ADDRESS = 0x001C
        const val MOUSE_BUTTONS_ADDRESS = 0x001E
        const val SYSTEM_FLAGS_ADDRESS = 0x001F
    }
}
