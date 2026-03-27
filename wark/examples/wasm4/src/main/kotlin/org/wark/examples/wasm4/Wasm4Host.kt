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
        val wasmMemory = org.wark.WarkMemory.create(2, 2)
        initializeSystemMemory(wasmMemory)
        builder.memory("env", "memory", wasmMemory)
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
        builder.function("env", "textUtf8", textUtf8())
        builder.function("env", "textUtf16", textUtf16())
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

    fun readFramebuffer(instance: WarkInstance) {
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
                    setPixelInMemory(instance, col, row, (fillColor - 1) and 0x03)
                }
            }
        }
        longArrayOf()
    }

    private fun line(): HostFunction = HostFunction { instance, args ->
        val x1 = args[0].toInt()
        val y1 = args[1].toInt()
        val x2 = args[2].toInt()
        val y2 = args[3].toInt()
        val drawColors = readDrawColors(instance)
        val color = (drawColors and 0x0F)
        if (color == 0) { return@HostFunction longArrayOf() }
        val c = (color - 1) and 0x03
        var dx = kotlin.math.abs(x2 - x1)
        var dy = -kotlin.math.abs(y2 - y1)
        var sx = if (x1 < x2) { 1 } else { -1 }
        var sy = if (y1 < y2) { 1 } else { -1 }
        var err = dx + dy
        var cx = x1; var cy = y1
        while (true) {
            setPixelInMemory(instance, cx, cy, c)
            if (cx == x2 && cy == y2) { break }
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; cx += sx }
            if (e2 <= dx) { err += dx; cy += sy }
        }
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
                setPixelInMemory(instance, col, y, (color - 1) and 0x03)
            }
        }
        longArrayOf()
    }

    private fun vline(): HostFunction = HostFunction { instance, args ->
        val x = args[0].toInt()
        val y = args[1].toInt()
        val length = args[2].toInt()
        val drawColors = readDrawColors(instance)
        val color = (drawColors and 0x0F)
        if (color != 0) {
            for (row in y until y + length) {
                setPixelInMemory(instance, x, row, (color - 1) and 0x03)
            }
        }
        longArrayOf()
    }

    private fun oval(): HostFunction = HostFunction { instance, args ->
        val x = args[0].toInt()
        val y = args[1].toInt()
        val width = args[2].toInt()
        val height = args[3].toInt()
        val drawColors = readDrawColors(instance)
        val fillColor = drawColors and 0x0F
        if (fillColor == 0) { return@HostFunction longArrayOf() }
        val c = (fillColor - 1) and 0x03
        val rx = width / 2.0
        val ry = height / 2.0
        val cx = x + rx
        val cy = y + ry
        for (row in y until y + height) {
            for (col in x until x + width) {
                val dx = (col + 0.5 - cx) / rx
                val dy = (row + 0.5 - cy) / ry
                if (dx * dx + dy * dy <= 1.0) {
                    setPixelInMemory(instance, col, row, c)
                }
            }
        }
        longArrayOf()
    }

    private fun blit(): HostFunction = HostFunction { instance, args ->
        val spritePtr = args[0].toInt()
        val x = args[1].toInt()
        val y = args[2].toInt()
        val width = args[3].toInt()
        val height = args[4].toInt()
        val flags = args[5].toInt()
        blitImpl(instance, spritePtr, x, y, width, height, 0, 0, width, flags)
        longArrayOf()
    }

    private fun blitSub(): HostFunction = HostFunction { instance, args ->
        val spritePtr = args[0].toInt()
        val x = args[1].toInt()
        val y = args[2].toInt()
        val width = args[3].toInt()
        val height = args[4].toInt()
        val srcX = args[5].toInt()
        val srcY = args[6].toInt()
        val stride = args[7].toInt()
        val flags = args[8].toInt()
        blitImpl(instance, spritePtr, x, y, width, height, srcX, srcY, stride, flags)
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

    private fun textUtf8(): HostFunction = HostFunction { instance, args ->
        val stringAddress = args[0].toInt()
        val byteLength = args[1].toInt()
        val x = args[2].toInt()
        val y = args[3].toInt()
        longArrayOf()
    }

    private fun textUtf16(): HostFunction = HostFunction { instance, args ->
        val stringAddress = args[0].toInt()
        val byteLength = args[1].toInt()
        val x = args[2].toInt()
        val y = args[3].toInt()
        longArrayOf()
    }

    private fun blitImpl(
        instance: WarkInstance, spritePtr: Int,
        destX: Int, destY: Int, width: Int, height: Int,
        srcX: Int, srcY: Int, stride: Int, flags: Int,
    ) {
        val memory = instance.memory()
        val drawColors = readDrawColors(instance)
        val bpp2 = (flags and 1) != 0
        val flipX = (flags and 2) != 0
        val flipY = (flags and 4) != 0
        val rotate = (flags and 8) != 0

        for (row in 0 until height) {
            for (col in 0 until width) {
                val sx = srcX + col
                val sy = srcY + row
                val bitIndex = if (bpp2) {
                    (sy * stride + sx) * 2
                } else {
                    sy * stride + sx
                }
                val byteIndex = spritePtr + bitIndex / 8
                val bitOffset = bitIndex % 8

                val colorIndex = if (bpp2) {
                    (memory.readByte(byteIndex).toInt() shr (6 - bitOffset)) and 0x03
                } else {
                    (memory.readByte(byteIndex).toInt() shr (7 - bitOffset)) and 0x01
                }

                val mappedColor = (drawColors shr (colorIndex * 4)) and 0x0F
                if (mappedColor == 0) { continue }

                var px = if (flipX) { width - 1 - col } else { col }
                var py = if (flipY) { height - 1 - row } else { row }
                if (rotate) {
                    val temp = px
                    px = py
                    py = temp
                }

                setPixelInMemory(instance, destX + px, destY + py, (mappedColor - 1) and 0x03)
            }
        }
    }

    private fun readDrawColors(instance: WarkInstance): Int {
        return instance.memory().readI32(DRAW_COLORS_ADDRESS) and 0xFFFF
    }

    private fun setPixelInMemory(instance: WarkInstance, x: Int, y: Int, color: Int) {
        if (x < 0 || x >= SCREEN_SIZE || y < 0 || y >= SCREEN_SIZE) { return }
        val index = y * SCREEN_SIZE + x
        val byteOffset = FRAMEBUFFER_ADDRESS + index / 4
        val bitOffset = (index % 4) * 2
        val current = instance.memory().readByte(byteOffset).toInt() and 0xFF
        val mask = (0x03 shl bitOffset).inv() and 0xFF
        val newByte = (current and mask) or ((color and 0x03) shl bitOffset)
        instance.memory().writeByte(byteOffset, newByte.toByte())
    }

    fun interface FrameCallback {
        fun onFrame(framebuffer: ByteArray, width: Int, height: Int)
    }

    private fun initializeSystemMemory(memory: org.wark.WarkMemory) {
        memory.writeI32(0x04, 0xe0f8cf.toInt())
        memory.writeI32(0x08, 0x86c06c)
        memory.writeI32(0x0C, 0x306850)
        memory.writeI32(0x10, 0x071821)
        memory.writeByte(0x14, 0x03)
        memory.writeByte(0x15, 0x12)
        memory.writeI32(MOUSE_X_ADDRESS, 0x7fff.toShort().toInt())
        memory.writeI32(MOUSE_Y_ADDRESS, 0x7fff.toShort().toInt())
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
