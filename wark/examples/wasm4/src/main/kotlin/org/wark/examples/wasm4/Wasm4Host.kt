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
        val strokeColor = (drawColors shr 4) and 0x0F
        if (fillColor != 0) {
            val fc = (fillColor - 1) and 0x03
            for (row in y until y + height) {
                for (col in x until x + width) {
                    setPixelInMemory(instance, col, row, fc)
                }
            }
        }
        if (strokeColor != 0) {
            val sc = (strokeColor - 1) and 0x03
            for (col in x until x + width) {
                setPixelInMemory(instance, col, y, sc)
                setPixelInMemory(instance, col, y + height - 1, sc)
            }
            for (row in y until y + height) {
                setPixelInMemory(instance, x, row, sc)
                setPixelInMemory(instance, x + width - 1, row, sc)
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
        val strokeColor = (drawColors shr 4) and 0x0F
        if (fillColor == 0 && strokeColor == 0) { return@HostFunction longArrayOf() }
        val rx = width / 2.0
        val ry = height / 2.0
        val cx = x + rx
        val cy = y + ry
        for (row in y until y + height) {
            for (col in x until x + width) {
                val dx = (col + 0.5 - cx) / rx
                val dy = (row + 0.5 - cy) / ry
                if (dx * dx + dy * dy > 1.0) { continue }
                if (strokeColor != 0 && isOvalBorder(col, row, cx, cy, rx, ry)) {
                    setPixelInMemory(instance, col, row, (strokeColor - 1) and 0x03)
                } else if (fillColor != 0) {
                    setPixelInMemory(instance, col, row, (fillColor - 1) and 0x03)
                }
            }
        }
        longArrayOf()
    }

    private fun isOvalBorder(col: Int, row: Int, cx: Double, cy: Double, rx: Double, ry: Double): Boolean {
        val neighbors = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((ndx, ndy) in neighbors) {
            val nx = (col + ndx + 0.5 - cx) / rx
            val ny = (row + ndy + 0.5 - cy) / ry
            if (nx * nx + ny * ny > 1.0) { return true }
        }
        return false
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
        val textBytes = readNullTerminatedBytes(memory, stringAddress)
        drawText(instance, textBytes, x, y)
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
        val memory = instance.memory()
        val textBytes = memory.readBytes(stringAddress, byteLength)
        drawText(instance, textBytes, x, y)
        longArrayOf()
    }

    private fun textUtf16(): HostFunction = HostFunction { instance, args ->
        val stringAddress = args[0].toInt()
        val byteLength = args[1].toInt()
        val x = args[2].toInt()
        val y = args[3].toInt()
        longArrayOf()
    }

    private fun drawText(instance: WarkInstance, textBytes: ByteArray, startX: Int, startY: Int) {
        val drawColors = readDrawColors(instance)
        val foreground = drawColors and 0x0F
        val background = (drawColors shr 4) and 0x0F
        var cursorX = startX
        var cursorY = startY

        for (rawByte in textBytes) {
            val charCode = rawByte.toInt() and 0xFF
            if (charCode == 0x0A) {
                cursorX = startX
                cursorY += Wasm4Font.GLYPH_SIZE
                continue
            }
            val glyphIndex = charCode - Wasm4Font.FIRST_CHAR
            if (glyphIndex < 0 || glyphIndex >= Wasm4Font.GLYPH_COUNT) { continue }
            drawGlyph(instance, glyphIndex, cursorX, cursorY, foreground, background)
            cursorX += Wasm4Font.GLYPH_SIZE
        }
    }

    private fun drawGlyph(
        instance: WarkInstance, glyphIndex: Int,
        x: Int, y: Int, foreground: Int, background: Int,
    ) {
        val offset = glyphIndex * Wasm4Font.GLYPH_SIZE
        for (row in 0 until Wasm4Font.GLYPH_SIZE) {
            val rowBits = Wasm4Font.data[offset + row].toInt() and 0xFF
            for (col in 0 until Wasm4Font.GLYPH_SIZE) {
                val isForeground = (rowBits shr (7 - col)) and 1 == 0
                if (isForeground) {
                    if (foreground != 0) {
                        setPixelInMemory(instance, x + col, y + row, (foreground - 1) and 0x03)
                    }
                } else {
                    if (background != 0) {
                        setPixelInMemory(instance, x + col, y + row, (background - 1) and 0x03)
                    }
                }
            }
        }
    }

    private fun readNullTerminatedBytes(memory: org.wark.WarkMemory, address: Int): ByteArray {
        var end = address
        while (memory.readByte(end) != 0.toByte()) {
            end++
        }
        if (end == address) { return ByteArray(0) }
        return memory.readBytes(address, end - address)
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

    private fun readDrawColors(instance: WarkInstance): Int = instance.memory().readI32(DRAW_COLORS_ADDRESS) and 0xFFFF

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
