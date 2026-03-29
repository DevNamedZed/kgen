package org.wark.examples.quake1

import org.wark.ExecutionMode
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.nio.file.Path

object QuakeSdlRunner {

    private val arena = Arena.ofShared()
    private val linker = Linker.nativeLinker()
    private var sdlLookup: SymbolLookup? = null

    private var sdlWindow = 0L
    private var sdlRenderer = 0L
    private var sdlTexture = 0L

    private const val SDL_INIT_VIDEO = 0x00000020
    private const val SDL_WINDOWPOS_CENTERED = 0x2FFF0000L
    private const val SDL_WINDOW_SHOWN = 0x00000004

    private const val SDL_QUIT = 0x100
    private const val SDL_KEYDOWN = 0x300
    private const val SDL_KEYUP = 0x301
    private const val SDL_MOUSEMOTION = 0x400
    private const val SDL_MOUSEBUTTONDOWN = 0x401
    private const val SDL_MOUSEBUTTONUP = 0x402

    private const val PIXEL_FORMAT_ARGB8888 = 0x16362004
    private const val TEXTURE_ACCESS_STREAMING = 1

    private const val SCALE = 3

    private fun loadSdl() {
        sdlLookup = try {
            SymbolLookup.libraryLookup("SDL2", arena)
        } catch (exception: Exception) {
            try {
                SymbolLookup.libraryLookup("libSDL2.so", arena)
            } catch (exception2: Exception) {
                SymbolLookup.libraryLookup("SDL2.dll", arena)
            }
        }
    }

    private fun callSdl(name: String, descriptor: FunctionDescriptor, vararg args: Any): Any? {
        val symbol = sdlLookup!!.find(name).orElseThrow { RuntimeException("SDL function not found: $name") }
        val handle = linker.downcallHandle(symbol, descriptor)
        return handle.invokeWithArguments(*args)
    }

    private fun pollEvent(eventBuffer: MemorySegment): Boolean {
        val result = callSdl("SDL_PollEvent", FunctionDescriptor.of(JAVA_INT, ADDRESS), eventBuffer) as Int
        return result != 0
    }

    private fun sdlDelay(ms: Int) {
        callSdl("SDL_Delay", FunctionDescriptor.ofVoid(JAVA_INT), ms)
    }

    private fun initSdl(screenWidth: Int, screenHeight: Int) {
        callSdl("SDL_Init", FunctionDescriptor.of(JAVA_INT, JAVA_INT), SDL_INIT_VIDEO)

        val title = arena.allocateFrom("Quake - wark JIT")
        val windowWidth = screenWidth * SCALE
        val windowHeight = screenHeight * SCALE

        sdlWindow = (callSdl("SDL_CreateWindow",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT),
            title, SDL_WINDOWPOS_CENTERED.toInt(), SDL_WINDOWPOS_CENTERED.toInt(),
            windowWidth, windowHeight, SDL_WINDOW_SHOWN) as MemorySegment).address()

        val windowSeg = MemorySegment.ofAddress(sdlWindow).reinterpret(1)
        sdlRenderer = (callSdl("SDL_CreateRenderer",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT),
            windowSeg, -1, 0) as MemorySegment).address()

        val rendererSeg = MemorySegment.ofAddress(sdlRenderer).reinterpret(1)
        sdlTexture = (callSdl("SDL_CreateTexture",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT),
            rendererSeg, PIXEL_FORMAT_ARGB8888, TEXTURE_ACCESS_STREAMING,
            screenWidth, screenHeight) as MemorySegment).address()
    }

    private fun renderFrame(runner: QuakeRunner) {
        val memory = runner.memory()
        val framebufferPtr = runner.framebufferPointer()
        val width = runner.framebufferWidth()
        val height = runner.framebufferHeight()
        val palettePtr = runner.palettePointer()

        val paletteBytes = memory.readBytes(palettePtr, 768)
        val palette = IntArray(256)
        for (i in 0 until 256) {
            val r = paletteBytes[i * 3].toInt() and 0xFF
            val g = paletteBytes[i * 3 + 1].toInt() and 0xFF
            val b = paletteBytes[i * 3 + 2].toInt() and 0xFF
            palette[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val indexedPixels = memory.readBytes(framebufferPtr, width * height)

        val pixelData = arena.allocate((width * height * 4).toLong())
        for (i in indexedPixels.indices) {
            val colorIndex = indexedPixels[i].toInt() and 0xFF
            pixelData.set(JAVA_INT, (i * 4).toLong(), palette[colorIndex])
        }

        val textureSeg = MemorySegment.ofAddress(sdlTexture).reinterpret(1)
        val rendererSeg = MemorySegment.ofAddress(sdlRenderer).reinterpret(1)
        val nullSeg = MemorySegment.NULL

        callSdl("SDL_UpdateTexture",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT),
            textureSeg, nullSeg, pixelData, width * 4)

        callSdl("SDL_RenderClear",
            FunctionDescriptor.of(JAVA_INT, ADDRESS), rendererSeg)

        callSdl("SDL_RenderCopy",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
            rendererSeg, textureSeg, nullSeg, nullSeg)

        callSdl("SDL_RenderPresent",
            FunctionDescriptor.ofVoid(ADDRESS), rendererSeg)
    }

    private fun mapScancode(scancode: Int): Int {
        return when (scancode) {
            82 -> 0xAD   // UP
            81 -> 0xAF   // DOWN
            80 -> 0xAC   // LEFT
            79 -> 0xAE   // RIGHT
            224 -> 0x9D  // LCTRL → fire
            225 -> 0xB6  // LSHIFT → run
            44 -> 0x20   // SPACE → jump
            40 -> 0x0D   // ENTER
            41 -> 0x1B   // ESCAPE
            43 -> 0x09   // TAB
            26 -> 'w'.code  // W
            4 -> 'a'.code   // A
            22 -> 's'.code  // S
            7 -> 'd'.code   // D
            else -> {
                if (scancode in 4..29) {
                    ('a'.code + scancode - 4)
                } else if (scancode in 30..39) {
                    ('0'.code + ((scancode - 29) % 10))
                } else {
                    0
                }
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) {
            println("Usage: quake-sdl <path/to/quake.wasm> <game-directory> [interpret|jit]")
            return
        }

        val wasmPath = Path.of(args[0])
        val gameDirectory = Path.of(args[1])
        val mode = if (args.size > 2 && args[2] == "interpret") {
            ExecutionMode.INTERPRET
        } else {
            ExecutionMode.JIT
        }

        println("[QUAKE] Loading: $wasmPath")
        println("[QUAKE] Mode: $mode")

        val runner = QuakeRunner.load(wasmPath, gameDirectory, mode)

        println("[QUAKE] Initializing...")
        runner.initialize()

        val screenWidth = runner.framebufferWidth()
        val screenHeight = runner.framebufferHeight()
        println("[QUAKE] Framebuffer: ${screenWidth}x${screenHeight}")

        loadSdl()
        initSdl(screenWidth, screenHeight)
        println("[QUAKE] SDL window opened (${screenWidth * SCALE}x${screenHeight * SCALE})")

        val eventBuffer = arena.allocate(56)
        var running = true
        var lastTime = System.nanoTime()

        while (running) {
            while (pollEvent(eventBuffer)) {
                val eventType = eventBuffer.get(JAVA_INT, 0)
                when (eventType) {
                    SDL_QUIT -> running = false
                    SDL_KEYDOWN -> {
                        val scancode = eventBuffer.get(JAVA_INT, 16)
                        val quakeKey = mapScancode(scancode)
                        if (quakeKey != 0) {
                            runner.keyEvent(quakeKey, true)
                        }
                    }
                    SDL_KEYUP -> {
                        val scancode = eventBuffer.get(JAVA_INT, 16)
                        val quakeKey = mapScancode(scancode)
                        if (quakeKey != 0) {
                            runner.keyEvent(quakeKey, false)
                        }
                    }
                    SDL_MOUSEMOTION -> {
                        val dx = eventBuffer.get(JAVA_INT, 20)
                        val dy = eventBuffer.get(JAVA_INT, 24)
                        runner.mouseMove(dx, dy)
                    }
                    SDL_MOUSEBUTTONDOWN -> {
                        val button = eventBuffer.get(JAVA_BYTE, 16).toInt()
                        runner.mouseButton(button, true)
                    }
                    SDL_MOUSEBUTTONUP -> {
                        val button = eventBuffer.get(JAVA_BYTE, 16).toInt()
                        runner.mouseButton(button, false)
                    }
                }
            }

            val now = System.nanoTime()
            val deltaTime = (now - lastTime) / 1_000_000_000.0f
            lastTime = now

            runner.frame(deltaTime.coerceAtMost(0.05f))
            renderFrame(runner)
        }

        val rendererSeg = MemorySegment.ofAddress(sdlRenderer).reinterpret(1)
        val textureSeg = MemorySegment.ofAddress(sdlTexture).reinterpret(1)
        val windowSeg = MemorySegment.ofAddress(sdlWindow).reinterpret(1)

        callSdl("SDL_DestroyTexture", FunctionDescriptor.ofVoid(ADDRESS), textureSeg)
        callSdl("SDL_DestroyRenderer", FunctionDescriptor.ofVoid(ADDRESS), rendererSeg)
        callSdl("SDL_DestroyWindow", FunctionDescriptor.ofVoid(ADDRESS), windowSeg)
        callSdl("SDL_Quit", FunctionDescriptor.ofVoid())

        runner.shutdown()
        arena.close()
    }
}
