package org.wark.examples.quake3

import org.wark.HostFunction
import org.wark.WarkImports

/**
 * Emscripten runtime host. Provides the "env" functions that Emscripten-compiled
 * WASM modules expect. Required for running C/C++ programs compiled with emcc.
 *
 * This covers the core Emscripten runtime functions. SDL/OpenGL functions are
 * handled by [SdlHost] separately.
 */
class EmscriptenHost {
    private val startTime = System.nanoTime()
    private var mainLoopCallback: Int = -1
    private var running = true

    fun registerImports(builder: WarkImports.Builder) {
        builder.function("env", "emscripten_get_now", emscriptenGetNow())
        builder.function("env", "emscripten_set_main_loop", emscriptenSetMainLoop())
        builder.function("env", "emscripten_cancel_main_loop", emscriptenCancelMainLoop())
        builder.function("env", "emscripten_resize_heap", emscriptenResizeHeap())
        builder.function("env", "emscripten_memcpy_js", emscriptenMemcpyJs())
        builder.function("env", "emscripten_date_now", emscriptenDateNow())
        builder.function("env", "emscripten_asm_const_int", emscriptenAsmConstInt())
        builder.function("env", "emscripten_run_script", emscriptenRunScript())

        builder.function("env", "__syscall_openat", syscallStub())
        builder.function("env", "__syscall_fcntl64", syscallStub())
        builder.function("env", "__syscall_ioctl", syscallStub())
        builder.function("env", "__syscall_faccessat", syscallStub())
        builder.function("env", "__syscall_rmdir", syscallStub())
        builder.function("env", "__syscall_unlinkat", syscallStub())
        builder.function("env", "__syscall_stat64", syscallStub())
        builder.function("env", "__syscall_fstat64", syscallStub())
        builder.function("env", "__syscall_lstat64", syscallStub())
        builder.function("env", "__syscall_getcwd", syscallStub())
        builder.function("env", "__syscall_mkdirat", syscallStub())

        builder.function("env", "abort", abort())
        builder.function("env", "_abort", abort())
        builder.function("env", "__cxa_throw", cxaThrow())
        builder.function("env", "__cxa_atexit", cxaAtexit())
    }

    fun isRunning(): Boolean = running

    fun mainLoopFunction(): Int = mainLoopCallback

    private fun emscriptenGetNow(): HostFunction = HostFunction { instance, args ->
        val millis = (System.nanoTime() - startTime).toDouble() / 1_000_000.0
        longArrayOf(java.lang.Double.doubleToRawLongBits(millis))
    }

    private fun emscriptenSetMainLoop(): HostFunction = HostFunction { instance, args ->
        mainLoopCallback = args[0].toInt()
        longArrayOf()
    }

    private fun emscriptenCancelMainLoop(): HostFunction = HostFunction { instance, args ->
        running = false
        mainLoopCallback = -1
        longArrayOf()
    }

    private fun emscriptenResizeHeap(): HostFunction = HostFunction { instance, args ->
        val requestedSize = args[0].toInt()
        val memory = instance.memory()
        val currentSize = memory.sizeBytes()
        if (requestedSize > currentSize) {
            val pagesNeeded = ((requestedSize - currentSize + 65535) / 65536)
            val result = memory.grow(pagesNeeded)
            longArrayOf(if (result >= 0) 1L else 0L)
        } else {
            longArrayOf(1L)
        }
    }

    private fun emscriptenMemcpyJs(): HostFunction = HostFunction { instance, args ->
        val destination = args[0].toInt()
        val source = args[1].toInt()
        val length = args[2].toInt()
        val memory = instance.memory()
        memory.copy(destination, source, length)
        longArrayOf()
    }

    private fun emscriptenDateNow(): HostFunction = HostFunction { instance, args ->
        longArrayOf(java.lang.Double.doubleToRawLongBits(System.currentTimeMillis().toDouble()))
    }

    private fun emscriptenAsmConstInt(): HostFunction = HostFunction { instance, args ->
        longArrayOf(0)
    }

    private fun emscriptenRunScript(): HostFunction = HostFunction { instance, args ->
        longArrayOf()
    }

    private fun abort(): HostFunction = HostFunction { instance, args ->
        throw org.wark.WasmTrap("abort() called")
    }

    private fun cxaThrow(): HostFunction = HostFunction { instance, args ->
        throw org.wark.WasmTrap("C++ exception thrown")
    }

    private fun cxaAtexit(): HostFunction = HostFunction { instance, args ->
        longArrayOf(0)
    }

    private fun syscallStub(): HostFunction = HostFunction { instance, args ->
        longArrayOf(-1)
    }
}
