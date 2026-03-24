package org.wark.examples.doom

import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkInstance
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Runs DOOM with an SDL2 window for rendering.
 *
 * ```
 * DoomSdlRunner.main(arrayOf("doom.wasm", "doom1.wad"))
 * ```
 */
object DoomSdlRunner {

    private const val SDL_INIT_VIDEO = 0x00000020
    private const val SDL_WINDOW_SHOWN = 0x00000004
    private const val SDL_WINDOWPOS_CENTERED = 0x2FFF0000

    private const val SDL_QUIT_EVENT = 0x100
    private const val SDL_KEYDOWN = 0x300
    private const val SDL_KEYUP = 0x301

    private var sdlWindow = 0L
    private var sdlRenderer = 0L
    private var sdlTexture = 0L
    private var screenWidth = 0
    private var screenHeight = 0
    private var running = true
    private var arena = Arena.ofShared()
    private var linker = Linker.nativeLinker()
    private var sdlLookup: SymbolLookup? = null

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) {
            println("Usage: DoomSdlRunner <doom.wasm> <doom1.wad>")
            return
        }

        val wasmPath = Path.of(args[0])
        val wadPath = Path.of(args[1])

        if (!Files.exists(wasmPath)) {
            println("WASM file not found: $wasmPath")
            return
        }
        if (!Files.exists(wadPath)) {
            println("WAD file not found: $wadPath")
            return
        }

        try {
            sdlLookup = try {
                SymbolLookup.libraryLookup("SDL2", arena)
            } catch (exception: Exception) {
                SymbolLookup.libraryLookup("libSDL2.so", arena)
            }
        } catch (exception: Exception) {
            println("SDL2 not found. Install SDL2 and try again.")
            println("  Linux: sudo apt install libsdl2-dev")
            println("  macOS: brew install sdl2")
            println("  Windows: download SDL2.dll to project directory")
            return
        }

        val wasmBytes = Files.readAllBytes(wasmPath)
        val wadBytes = Files.readAllBytes(wadPath)

        println("Loading DOOM...")
        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
        val module = runtime.load(wasmBytes)

        var host: DoomHost? = null
        host = DoomHost(wadBytes) { instance, framebufferAddress ->
            if (screenWidth == 0) {
                screenWidth = host!!.screenWidth
                screenHeight = host!!.screenHeight
            }
            renderFrame(instance, framebufferAddress)
        }

        val builder = WarkImports.builder()
        host.registerImports(builder)
        val instance = module.instantiate(builder.build())

        println("Initializing DOOM...")
        instance.call("initGame")

        screenWidth = host.screenWidth
        screenHeight = host.screenHeight
        println("DOOM initialized: ${screenWidth}x${screenHeight}")
        println("Starting game loop...")

        gameLoop(instance)

        cleanup()
        println("DOOM exited.")
    }

    private fun gameLoop(instance: WarkInstance) {
        val eventBuffer = arena.allocate(56)

        while (running) {
            while (pollEvent(eventBuffer)) {
                val eventType = eventBuffer.get(JAVA_INT, 0)
                when (eventType) {
                    SDL_QUIT_EVENT -> { running = false }
                    SDL_KEYDOWN -> {
                        val scancode = eventBuffer.get(JAVA_INT, 16)
                        val doomKey = scancodeToKey(scancode)
                        if (doomKey != 0) {
                            instance.call("reportKeyDown", doomKey.toLong())
                        }
                    }
                    SDL_KEYUP -> {
                        val scancode = eventBuffer.get(JAVA_INT, 16)
                        val doomKey = scancodeToKey(scancode)
                        if (doomKey != 0) {
                            instance.call("reportKeyUp", doomKey.toLong())
                        }
                    }
                }
            }

            instance.call("tickGame")
            sdlDelay(16)
        }
    }

    private fun renderFrame(instance: WarkInstance, framebufferAddress: Int) {
        if (sdlWindow == 0L) {
            initSdl()
        }

        val memory = instance.memory()
        val frameSize = screenWidth * screenHeight * 4
        val pixelData = memory.readBytes(framebufferAddress, frameSize)

        updateTexture(pixelData)
        renderPresent()
    }

    private fun initSdl() {
        callSdl("SDL_Init", FunctionDescriptor.of(JAVA_INT, JAVA_INT), SDL_INIT_VIDEO)

        val title = arena.allocateFrom("DOOM - wark")
        sdlWindow = (callSdl("SDL_CreateWindow",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT),
            title, SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED,
            screenWidth * 2, screenHeight * 2, SDL_WINDOW_SHOWN) as MemorySegment).address()

        sdlRenderer = (callSdl("SDL_CreateRenderer",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT),
            MemorySegment.ofAddress(sdlWindow).reinterpret(1), -1, 0) as MemorySegment).address()

        sdlTexture = (callSdl("SDL_CreateTexture",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT),
            MemorySegment.ofAddress(sdlRenderer).reinterpret(1),
            0x16362004, // SDL_PIXELFORMAT_ARGB8888
            0, // SDL_TEXTUREACCESS_STATIC
            screenWidth, screenHeight) as MemorySegment).address()
    }

    private fun updateTexture(pixels: ByteArray) {
        if (sdlTexture == 0L) { return }
        val pixelSegment = arena.allocate(pixels.size.toLong())
        pixelSegment.copyFrom(MemorySegment.ofArray(pixels))
        callSdl("SDL_UpdateTexture",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT),
            MemorySegment.ofAddress(sdlTexture).reinterpret(1),
            MemorySegment.NULL,
            pixelSegment,
            screenWidth * 4)
        callSdl("SDL_RenderClear",
            FunctionDescriptor.of(JAVA_INT, ADDRESS),
            MemorySegment.ofAddress(sdlRenderer).reinterpret(1))
        callSdl("SDL_RenderCopy",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
            MemorySegment.ofAddress(sdlRenderer).reinterpret(1),
            MemorySegment.ofAddress(sdlTexture).reinterpret(1),
            MemorySegment.NULL, MemorySegment.NULL)
    }

    private fun renderPresent() {
        if (sdlRenderer == 0L) { return }
        callSdl("SDL_RenderPresent",
            FunctionDescriptor.ofVoid(ADDRESS),
            MemorySegment.ofAddress(sdlRenderer).reinterpret(1))
    }

    private fun pollEvent(eventBuffer: MemorySegment): Boolean {
        val result = callSdl("SDL_PollEvent",
            FunctionDescriptor.of(JAVA_INT, ADDRESS),
            eventBuffer) as Int
        return result != 0
    }

    private fun sdlDelay(ms: Int) {
        callSdl("SDL_Delay", FunctionDescriptor.ofVoid(JAVA_INT), ms)
    }

    private fun cleanup() {
        if (sdlTexture != 0L) {
            callSdl("SDL_DestroyTexture", FunctionDescriptor.ofVoid(ADDRESS),
                MemorySegment.ofAddress(sdlTexture).reinterpret(1))
        }
        if (sdlRenderer != 0L) {
            callSdl("SDL_DestroyRenderer", FunctionDescriptor.ofVoid(ADDRESS),
                MemorySegment.ofAddress(sdlRenderer).reinterpret(1))
        }
        if (sdlWindow != 0L) {
            callSdl("SDL_DestroyWindow", FunctionDescriptor.ofVoid(ADDRESS),
                MemorySegment.ofAddress(sdlWindow).reinterpret(1))
        }
        callSdl("SDL_Quit", FunctionDescriptor.ofVoid())
        arena.close()
    }

    private fun callSdl(name: String, descriptor: FunctionDescriptor, vararg args: Any): Any? {
        val lookup = sdlLookup ?: return null
        val symbol = lookup.find(name).orElse(null) ?: return null
        val handle = linker.downcallHandle(symbol, descriptor)
        return handle.invokeWithArguments(*args)
    }

    private fun scancodeToKey(scancode: Int): Int = when (scancode) {
        80 -> DoomRunner.KEY_LEFTARROW      // Left arrow
        79 -> DoomRunner.KEY_RIGHTARROW     // Right arrow
        82 -> DoomRunner.KEY_UPARROW        // Up arrow
        81 -> DoomRunner.KEY_DOWNARROW      // Down arrow
        44 -> DoomRunner.KEY_USE            // Space = use (open doors)
        224 -> DoomRunner.KEY_FIRE          // Left Ctrl = fire
        228 -> DoomRunner.KEY_FIRE          // Right Ctrl = fire
        41 -> DoomRunner.KEY_ESCAPE         // Escape
        40 -> DoomRunner.KEY_ENTER          // Enter
        43 -> DoomRunner.KEY_TAB            // Tab = automap
        225 -> DoomRunner.KEY_RSHIFT        // Left Shift = run
        229 -> DoomRunner.KEY_RSHIFT        // Right Shift = run
        226 -> DoomRunner.KEY_RALT          // Left Alt = strafe
        230 -> DoomRunner.KEY_RALT          // Right Alt = strafe
        4 -> DoomRunner.KEY_STRAFE_L        // A = strafe left
        7 -> DoomRunner.KEY_STRAFE_R        // D = strafe right
        26 -> DoomRunner.KEY_UPARROW        // W = forward
        22 -> DoomRunner.KEY_DOWNARROW      // S = backward
        8 -> DoomRunner.KEY_USE             // E = use
        9 -> DoomRunner.KEY_FIRE            // F = fire (alt)
        else -> 0
    }
}
