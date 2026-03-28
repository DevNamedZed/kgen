package org.wark.examples.wasm4

import org.wark.ExecutionMode
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.nio.file.Files
import java.nio.file.Path

object Wasm4SdlRunner {

    private const val SCALE = 4
    private const val WINDOW_SIZE = Wasm4Host.SCREEN_SIZE * SCALE

    private const val SDL_INIT_VIDEO = 0x00000020
    private const val SDL_WINDOW_SHOWN = 0x00000004
    private const val SDL_WINDOWPOS_CENTERED = 0x2FFF0000
    private const val SDL_QUIT_EVENT = 0x100
    private const val SDL_KEYDOWN = 0x300
    private const val SDL_KEYUP = 0x301
    private const val SDL_MOUSEMOTION = 0x400
    private const val SDL_MOUSEBUTTONDOWN = 0x401
    private const val SDL_MOUSEBUTTONUP = 0x402

    private var sdlWindow = 0L
    private var sdlRenderer = 0L
    private var sdlTexture = 0L
    private var running = true
    private val arena = Arena.ofShared()
    private val linker = Linker.nativeLinker()
    private var sdlLookup: SymbolLookup? = null

    private val palette = intArrayOf(
        0xFF071821.toInt(),
        0xFF306850.toInt(),
        0xFF86C06C.toInt(),
        0xFFE0F8CF.toInt(),
    )

    private var gamepadState = 0
    private var mouseState = MouseState(0, 0, 0)
    private var frameCount = 0

    private data class MouseState(val x: Int, val y: Int, val buttons: Int)
    private val pixelBuffer = arena.allocate((WINDOW_SIZE * WINDOW_SIZE * 4).toLong())

    @JvmStatic
    fun main(args: Array<String>) {
        val cartPath = if (args.isNotEmpty() && !args[0].startsWith("--")) {
            Path.of(args[0])
        } else {
            selectGame()
        }

        if (cartPath == null) {
            return
        }

        if (!Files.exists(cartPath)) {
            println("Cartridge not found: $cartPath")
            return
        }

        try {
            sdlLookup = try {
                SymbolLookup.libraryLookup("SDL2", arena)
            } catch (e: Exception) {
                SymbolLookup.libraryLookup("libSDL2.so", arena)
            }
        } catch (e: Exception) {
            println("SDL2 not found. Place SDL2.dll next to the jar.")
            return
        }

        val useInterpreter = args.contains("--interpret")
        val mode = if (useInterpreter) { ExecutionMode.INTERPRET } else { ExecutionMode.JIT }

        println("Loading ${cartPath.fileName} (${mode.name})...")
        val runner = Wasm4Runner.load(cartPath, mode)

        println("Starting...")
        runner.start()

        initSdl(cartPath.fileName.toString().removeSuffix(".wasm"))
        gameLoop(runner)
        cleanup()
    }

    private fun selectGame(): Path? {
        val gamesDir = findGamesDir()
        if (gamesDir == null) {
            println("No games directory found. Usage: wasm4 <cartridge.wasm>")
            return null
        }

        val cartridges = Files.list(gamesDir)
            .filter { it.toString().endsWith(".wasm") }
            .sorted()
            .toList()

        if (cartridges.isEmpty()) {
            println("No .wasm cartridges found in $gamesDir")
            return null
        }

        println("╔══════════════════════════════════╗")
        println("║       WASM-4 Game Selector       ║")
        println("║          powered by wark          ║")
        println("╠══════════════════════════════════╣")
        for ((index, cart) in cartridges.withIndex()) {
            val name = cart.fileName.toString().removeSuffix(".wasm")
            val sizeKb = Files.size(cart) / 1024
            println("║  ${index + 1}. %-20s %4dK  ║".format(name, sizeKb))
        }
        println("╠══════════════════════════════════╣")
        println("║  0. Quit                         ║")
        println("╚══════════════════════════════════╝")
        print("Select game: ")
        System.out.flush()

        val input = readlnOrNull()?.trim() ?: return null
        val choice = input.toIntOrNull() ?: return null

        if (choice == 0) { return null }
        if (choice < 1 || choice > cartridges.size) {
            println("Invalid choice.")
            return null
        }

        return cartridges[choice - 1]
    }

    private fun findGamesDir(): Path? {
        val candidates = listOf(
            Path.of("games"),
            Path.of("../assets/wasm4"),
            Path.of("wasm4"),
        )
        return candidates.firstOrNull { Files.isDirectory(it) }
    }

    private fun gameLoop(runner: Wasm4Runner) {
        val eventBuffer = arena.allocate(56)

        while (running) {
            while (pollEvent(eventBuffer)) {
                val eventType = eventBuffer.get(JAVA_INT, 0)
                if (frameCount < 3) {
                    System.err.println("[EVENT] type=$eventType (0x${Integer.toHexString(eventType)})")
                }
                when (eventType) {
                    SDL_QUIT_EVENT -> { running = false }
                    SDL_KEYDOWN -> {
                        val scancode = eventBuffer.get(JAVA_INT, 16)
                        System.err.println("[KEY] DOWN scancode=$scancode")
                        handleKey(scancode, true)
                    }
                    SDL_KEYUP -> {
                        val scancode = eventBuffer.get(JAVA_INT, 16)
                        System.err.println("[KEY] UP scancode=$scancode")
                        handleKey(scancode, false)
                    }
                    SDL_MOUSEMOTION -> {
                        val mouseX = eventBuffer.get(JAVA_INT, 20)
                        val mouseY = eventBuffer.get(JAVA_INT, 24)
                        mouseState = mouseState.copy(x = mouseX / SCALE, y = mouseY / SCALE)
                    }
                    SDL_MOUSEBUTTONDOWN -> {
                        val button = eventBuffer.get(ValueLayout.JAVA_BYTE, 16).toInt() and 0xFF
                        if (button == 1) { mouseState = mouseState.copy(buttons = mouseState.buttons or 0x01) }
                        if (button == 3) { mouseState = mouseState.copy(buttons = mouseState.buttons or 0x02) }
                    }
                    SDL_MOUSEBUTTONUP -> {
                        val button = eventBuffer.get(ValueLayout.JAVA_BYTE, 16).toInt() and 0xFF
                        if (button == 1) { mouseState = mouseState.copy(buttons = mouseState.buttons and 0x01.inv()) }
                        if (button == 3) { mouseState = mouseState.copy(buttons = mouseState.buttons and 0x02.inv()) }
                    }
                }
            }

            runner.setGamepad(gamepadState)
            runner.setMouse(mouseState.x, mouseState.y, mouseState.buttons)
            if (gamepadState != 0 && frameCount < 5) {
                System.err.println("[INPUT] gamepad=0x${gamepadState.toString(16)}")
            }

            try {
                runner.update()
            } catch (e: Exception) {
                System.err.println("Error: ${e.message}")
                e.printStackTrace(System.err)
                running = false
                break
            }

            renderFrame(runner)
            if (frameCount == 1) {
                var nonZero = 0
                for (y in 0 until Wasm4Host.SCREEN_SIZE) {
                    for (x in 0 until Wasm4Host.SCREEN_SIZE) {
                        if (runner.getPixel(x, y) != 0) { nonZero++ }
                    }
                }
                System.err.println("[FRAME1] non-zero pixels: $nonZero / ${Wasm4Host.SCREEN_SIZE * Wasm4Host.SCREEN_SIZE}")
            }
            frameCount++
            sdlDelay(16)
        }
    }

    private fun handleKey(scancode: Int, down: Boolean) {
        val button = when (scancode) {
            82 -> 0x40 // Up arrow → DPAD_UP
            81 -> 0x80 // Down arrow → DPAD_DOWN
            80 -> 0x10 // Left arrow → DPAD_LEFT
            79 -> 0x20 // Right arrow → DPAD_RIGHT
            27 -> 0x01 // X → BUTTON_1
            29 -> 0x02 // Z → BUTTON_2
            40 -> 0x01 // Enter → BUTTON_1
            44 -> 0x02 // Space → BUTTON_2
            else -> 0
        }
        if (button != 0) {
            if (down) {
                gamepadState = gamepadState or button
            } else {
                gamepadState = gamepadState and button.inv()
            }
        }
    }

    private fun renderFrame(runner: Wasm4Runner) {
        if (sdlTexture == 0L) { return }

        val pixels = pixelBuffer
        for (y in 0 until Wasm4Host.SCREEN_SIZE) {
            for (x in 0 until Wasm4Host.SCREEN_SIZE) {
                val colorIndex = runner.getPixel(x, y)
                val argb = palette[colorIndex and 0x03]
                for (sy in 0 until SCALE) {
                    for (sx in 0 until SCALE) {
                        val px = x * SCALE + sx
                        val py = y * SCALE + sy
                        pixels.set(JAVA_INT, ((py * WINDOW_SIZE + px) * 4).toLong(), argb)
                    }
                }
            }
        }

        callSdl("SDL_UpdateTexture",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT),
            MemorySegment.ofAddress(sdlTexture).reinterpret(1),
            MemorySegment.NULL, pixels, WINDOW_SIZE * 4)
        callSdl("SDL_RenderClear",
            FunctionDescriptor.of(JAVA_INT, ADDRESS),
            MemorySegment.ofAddress(sdlRenderer).reinterpret(1))
        callSdl("SDL_RenderCopy",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
            MemorySegment.ofAddress(sdlRenderer).reinterpret(1),
            MemorySegment.ofAddress(sdlTexture).reinterpret(1),
            MemorySegment.NULL, MemorySegment.NULL)
        callSdl("SDL_RenderPresent",
            FunctionDescriptor.ofVoid(ADDRESS),
            MemorySegment.ofAddress(sdlRenderer).reinterpret(1))
        if (frameCount < 5) {
            System.err.println("[RENDER] frame=$frameCount presented")
        }
    }

    private fun initSdl(title: String) {
        callSdl("SDL_Init", FunctionDescriptor.of(JAVA_INT, JAVA_INT), SDL_INIT_VIDEO)
        val titleSeg = arena.allocateFrom("WASM-4: $title")
        sdlWindow = (callSdl("SDL_CreateWindow",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT),
            titleSeg, SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED,
            WINDOW_SIZE, WINDOW_SIZE, SDL_WINDOW_SHOWN) as MemorySegment).address()
        val windowSeg = MemorySegment.ofAddress(sdlWindow).reinterpret(1)
        callSdl("SDL_RaiseWindow", FunctionDescriptor.ofVoid(ADDRESS), windowSeg)
        sdlRenderer = (callSdl("SDL_CreateRenderer",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT),
            windowSeg, -1, 0) as MemorySegment).address()
        sdlTexture = (callSdl("SDL_CreateTexture",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT),
            MemorySegment.ofAddress(sdlRenderer).reinterpret(1),
            0x16362004, 0, WINDOW_SIZE, WINDOW_SIZE) as MemorySegment).address()
    }

    private fun pollEvent(eventBuffer: MemorySegment): Boolean = (callSdl("SDL_PollEvent",
        FunctionDescriptor.of(JAVA_INT, ADDRESS), eventBuffer) as Int) != 0

    private fun sdlDelay(ms: Int) {
        callSdl("SDL_Delay", FunctionDescriptor.ofVoid(JAVA_INT), ms)
    }

    private fun cleanup() {
        if (sdlTexture != 0L) { callSdl("SDL_DestroyTexture", FunctionDescriptor.ofVoid(ADDRESS), MemorySegment.ofAddress(sdlTexture).reinterpret(1)) }
        if (sdlRenderer != 0L) { callSdl("SDL_DestroyRenderer", FunctionDescriptor.ofVoid(ADDRESS), MemorySegment.ofAddress(sdlRenderer).reinterpret(1)) }
        if (sdlWindow != 0L) { callSdl("SDL_DestroyWindow", FunctionDescriptor.ofVoid(ADDRESS), MemorySegment.ofAddress(sdlWindow).reinterpret(1)) }
        callSdl("SDL_Quit", FunctionDescriptor.ofVoid())
        arena.close()
    }

    private fun callSdl(name: String, descriptor: FunctionDescriptor, vararg args: Any): Any? {
        val lookup = sdlLookup ?: return null
        val symbol = lookup.find(name).orElse(null) ?: return null
        return linker.downcallHandle(symbol, descriptor).invokeWithArguments(*args)
    }
}
