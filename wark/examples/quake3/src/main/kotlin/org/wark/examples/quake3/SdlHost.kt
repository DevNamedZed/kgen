package org.wark.examples.quake3

import org.wark.HostFunction
import org.wark.WarkImports
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.lang.invoke.MethodHandle

/**
 * SDL2 host bindings via FFM. Registers SDL functions as WASM imports
 * so WASM code can create windows, poll events, and swap buffers.
 *
 * Requires SDL2 shared library on the system:
 * - Linux: libSDL2.so (apt install libsdl2-dev)
 * - macOS: libSDL2.dylib (brew install sdl2)
 * - Windows: SDL2.dll
 *
 * ```kotlin
 * val sdl = SdlHost.load()
 * sdl.registerImports(importsBuilder)
 * ```
 */
class SdlHost private constructor(
    private val library: SymbolLookup,
    private val linker: Linker,
    private val arena: Arena,
) {

    fun registerImports(builder: WarkImports.Builder) {
        builder.function("env", "SDL_Init", wrapSdlInit())
        builder.function("env", "SDL_CreateWindow", wrapSdlCreateWindow())
        builder.function("env", "SDL_GL_CreateContext", wrapSdlGlCreateContext())
        builder.function("env", "SDL_GL_SwapWindow", wrapSdlGlSwapWindow())
        builder.function("env", "SDL_PollEvent", wrapSdlPollEvent())
        builder.function("env", "SDL_Delay", wrapSdlDelay())
        builder.function("env", "SDL_GetTicks", wrapSdlGetTicks())
        builder.function("env", "SDL_Quit", wrapSdlQuit())
        builder.function("env", "SDL_GL_SetAttribute", wrapSdlGlSetAttribute())
    }

    private fun findOrNull(name: String): MemorySegment? {
        return library.find(name).orElse(null)
    }

    private fun wrapSdlInit(): HostFunction = HostFunction { instance, args ->
        val handle = downcall("SDL_Init", FunctionDescriptor.of(JAVA_INT, JAVA_INT))
        if (handle != null) {
            val result = handle.invoke(args[0].toInt()) as Int
            longArrayOf(result.toLong())
        } else {
            longArrayOf(0)
        }
    }

    private fun wrapSdlCreateWindow(): HostFunction = HostFunction { instance, args ->
        val handle = downcall("SDL_CreateWindow",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT))
        if (handle != null) {
            val memory = instance.memory()
            val titleAddress = args[0].toInt()
            val title = memory.readUtf8(titleAddress)
            val titleSegment = arena.allocateFrom(title)
            val result = handle.invoke(titleSegment, args[1].toInt(), args[2].toInt(),
                args[3].toInt(), args[4].toInt(), args[5].toInt()) as MemorySegment
            longArrayOf(result.address())
        } else {
            longArrayOf(0)
        }
    }

    private fun wrapSdlGlCreateContext(): HostFunction = HostFunction { instance, args ->
        val handle = downcall("SDL_GL_CreateContext", FunctionDescriptor.of(ADDRESS, ADDRESS))
        if (handle != null) {
            val window = MemorySegment.ofAddress(args[0]).reinterpret(1)
            val result = handle.invoke(window) as MemorySegment
            longArrayOf(result.address())
        } else {
            longArrayOf(0)
        }
    }

    private fun wrapSdlGlSwapWindow(): HostFunction = HostFunction { instance, args ->
        val handle = downcall("SDL_GL_SwapWindow", FunctionDescriptor.ofVoid(ADDRESS))
        if (handle != null) {
            val window = MemorySegment.ofAddress(args[0]).reinterpret(1)
            handle.invoke(window)
        }
        longArrayOf()
    }

    private fun wrapSdlPollEvent(): HostFunction = HostFunction { instance, args ->
        val handle = downcall("SDL_PollEvent", FunctionDescriptor.of(JAVA_INT, ADDRESS))
        if (handle != null) {
            val eventSegment = arena.allocate(56)
            val result = handle.invoke(eventSegment) as Int
            if (result != 0 && args.isNotEmpty()) {
                val memory = instance.memory()
                val eventAddress = args[0].toInt()
                val bytes = eventSegment.toArray(JAVA_BYTE)
                val copySize = minOf(bytes.size, 56)
                memory.writeBytes(eventAddress, bytes.copyOf(copySize))
            }
            longArrayOf(result.toLong())
        } else {
            longArrayOf(0)
        }
    }

    private fun wrapSdlDelay(): HostFunction = HostFunction { instance, args ->
        val handle = downcall("SDL_Delay", FunctionDescriptor.ofVoid(JAVA_INT))
        if (handle != null) {
            handle.invoke(args[0].toInt())
        }
        longArrayOf()
    }

    private fun wrapSdlGetTicks(): HostFunction = HostFunction { instance, args ->
        val handle = downcall("SDL_GetTicks", FunctionDescriptor.of(JAVA_INT))
        if (handle != null) {
            longArrayOf((handle.invoke() as Int).toLong())
        } else {
            longArrayOf(System.currentTimeMillis().toInt().toLong())
        }
    }

    private fun wrapSdlQuit(): HostFunction = HostFunction { instance, args ->
        val handle = downcall("SDL_Quit", FunctionDescriptor.ofVoid())
        handle?.invoke()
        longArrayOf()
    }

    private fun wrapSdlGlSetAttribute(): HostFunction = HostFunction { instance, args ->
        val handle = downcall("SDL_GL_SetAttribute", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT))
        if (handle != null) {
            longArrayOf((handle.invoke(args[0].toInt(), args[1].toInt()) as Int).toLong())
        } else {
            longArrayOf(0)
        }
    }

    private fun downcall(name: String, descriptor: FunctionDescriptor): MethodHandle? {
        val symbol = findOrNull(name) ?: return null
        return linker.downcallHandle(symbol, descriptor)
    }

    companion object {
        @JvmStatic
        fun load(): SdlHost? {
            return try {
                val arena = Arena.ofShared()
                val linker = Linker.nativeLinker()
                val library = SymbolLookup.libraryLookup(sdlLibraryName(), arena)
                SdlHost(library, linker, arena)
            } catch (exception: Exception) {
                null
            }
        }

        @JvmStatic
        fun isAvailable(): Boolean {
            return try {
                val arena = Arena.ofConfined()
                SymbolLookup.libraryLookup(sdlLibraryName(), arena)
                arena.close()
                true
            } catch (exception: Exception) {
                false
            }
        }

        private fun sdlLibraryName(): String {
            val os = System.getProperty("os.name")?.lowercase() ?: ""
            return when {
                "win" in os -> "SDL2"
                "mac" in os || "darwin" in os -> "SDL2"
                else -> "SDL2"
            }
        }
    }
}
