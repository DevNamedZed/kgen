package org.wark.examples.wasm4

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import org.wark.ExecutionMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Wasm4HostTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun minimalWasm4Game() {
        val bytes = buildWasmBytes {
            importFunction("env", "rect", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "text", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "trace", listOf(WasmValueType.I32), emptyList())
            importFunction("env", "blit", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "blitSub", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "line", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "hline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "vline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "oval", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "tone", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "diskr", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "diskw", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "tracef", listOf(WasmValueType.I32, WasmValueType.I32), emptyList())

            memory("mem", 1, exported = true)

            function("start", emptyList(), emptyList(), exported = true) { func, asm ->
                asm.i32Const(1000)
                asm.i32Const(72)
                asm.i32Store8(0, 0)
                asm.i32Const(1001)
                asm.i32Const(105)
                asm.i32Store8(0, 0)
                asm.i32Const(1002)
                asm.i32Const(0)
                asm.i32Store8(0, 0)

                asm.i32Const(1000)
                asm.call(2)
            }

            function("update", emptyList(), emptyList(), exported = true) { func, asm ->
                asm.i32Const(10)
                asm.i32Const(10)
                asm.i32Const(20)
                asm.i32Const(20)
                asm.call(0)
            }
        }

        val runner = Wasm4Runner.load(bytes, ExecutionMode.INTERPRET)
        runner.start()

        assertTrue(runner.traceOutput().contains("Hi"))

        for (frame in 0 until 10) {
            runner.update()
        }
        assertEquals(10, runner.frameCount())
    }

    @Test
    fun wasm4CounterGame() {
        val bytes = buildWasmBytes {
            importFunction("env", "rect", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "text", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "trace", listOf(WasmValueType.I32), emptyList())
            importFunction("env", "blit", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "blitSub", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "line", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "hline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "vline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "oval", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "tone", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "diskr", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "diskw", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "tracef", listOf(WasmValueType.I32, WasmValueType.I32), emptyList())

            memory("mem", 1, exported = true)

            function("start", emptyList(), emptyList(), exported = true) { func, asm ->
                asm.i32Const(2000)
                asm.i32Const(0)
                asm.i32Store(0, 0)
            }

            function("update", emptyList(), emptyList(), exported = true) { func, asm ->
                asm.i32Const(2000)
                asm.i32Const(2000)
                asm.i32Load(0, 0)
                asm.i32Const(1)
                asm.i32Add()
                asm.i32Store(0, 0)
            }
        }

        val runner = Wasm4Runner.load(bytes, ExecutionMode.INTERPRET)
        runner.start()

        for (frame in 0 until 60) {
            runner.update()
        }

        assertEquals(60, runner.frameCount())
    }

    @Test
    fun textRendersPixelsToFramebuffer() {
        val bytes = buildWasmBytes {
            importFunction("env", "rect", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "text", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "trace", listOf(WasmValueType.I32), emptyList())
            importFunction("env", "blit", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "blitSub", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "line", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "hline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "vline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "oval", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "tone", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "diskr", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "diskw", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "tracef", listOf(WasmValueType.I32, WasmValueType.I32), emptyList())

            memory("mem", 1, exported = true)

            function("start", emptyList(), emptyList(), exported = true) { func, asm -> }

            function("update", emptyList(), emptyList(), exported = true) { func, asm ->
                // Write "A" at address 1000 (null-terminated)
                asm.i32Const(1000)
                asm.i32Const(65) // 'A'
                asm.i32Store8(0, 0)
                asm.i32Const(1001)
                asm.i32Const(0) // null terminator
                asm.i32Store8(0, 0)

                // Set draw colors: foreground=2 (palette index 1)
                asm.i32Const(Wasm4Host.DRAW_COLORS_ADDRESS)
                asm.i32Const(0x0002)
                asm.i32Store16(0, 0)

                // text(1000, 0, 0) — draw "A" at top-left
                asm.i32Const(1000)
                asm.i32Const(0)
                asm.i32Const(0)
                asm.call(1) // text
            }
        }

        val runner = Wasm4Runner.load(bytes, ExecutionMode.INTERPRET)
        runner.start()
        runner.update()

        // 'A' glyph has foreground pixels — count non-zero pixels in the 8x8 area
        var nonZeroPixels = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (runner.getPixel(x, y) != 0) { nonZeroPixels++ }
            }
        }
        assertTrue(nonZeroPixels > 0, "text('A') should render pixels to framebuffer, got $nonZeroPixels")

        // 'A' glyph: top row (0xc7 inverted = 00111000) has 3 foreground pixels at columns 2,3,4
        // With draw color 2 (palette index 1), these pixels should be non-zero
        assertEquals(0, runner.getPixel(0, 0), "top-left corner of 'A' should be empty")
        assertTrue(runner.getPixel(3, 0) != 0, "top-center of 'A' should be filled")
    }

    @Test
    fun rectDrawsOutline() {
        val bytes = buildWasmBytes {
            importFunction("env", "rect", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "text", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "trace", listOf(WasmValueType.I32), emptyList())
            importFunction("env", "blit", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "blitSub", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "line", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "hline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "vline", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "oval", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "tone", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), emptyList())
            importFunction("env", "diskr", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "diskw", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "tracef", listOf(WasmValueType.I32, WasmValueType.I32), emptyList())

            memory("mem", 1, exported = true)

            function("start", emptyList(), emptyList(), exported = true) { func, asm -> }

            function("update", emptyList(), emptyList(), exported = true) { func, asm ->
                // draw colors: fill=2 (color 1 nibble), stroke=3 (color 2 nibble)
                asm.i32Const(Wasm4Host.DRAW_COLORS_ADDRESS)
                asm.i32Const(0x0032) // stroke=3, fill=2
                asm.i32Store16(0, 0)

                // rect(10, 10, 20, 20)
                asm.i32Const(10)
                asm.i32Const(10)
                asm.i32Const(20)
                asm.i32Const(20)
                asm.call(0) // rect
            }
        }

        val runner = Wasm4Runner.load(bytes, ExecutionMode.INTERPRET)
        runner.start()
        runner.update()

        // Border pixel at (10, 10) should be stroke color (palette index 2)
        val borderPixel = runner.getPixel(10, 10)
        assertEquals(2, borderPixel, "border pixel should be palette index 2 (stroke color 3 - 1)")

        // Interior pixel at (15, 15) should be fill color (palette index 1)
        val fillPixel = runner.getPixel(15, 15)
        assertEquals(1, fillPixel, "interior pixel should be palette index 1 (fill color 2 - 1)")

        // Border pixel at (29, 29) should be stroke color
        val bottomRightBorder = runner.getPixel(29, 29)
        assertEquals(2, bottomRightBorder, "bottom-right border should be stroke color")
    }
}
