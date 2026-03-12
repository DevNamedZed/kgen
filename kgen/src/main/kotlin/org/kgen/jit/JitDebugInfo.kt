package org.kgen.jit

import java.io.File

/**
 * Debug info support for JIT-compiled code. Makes JIT'd function names visible
 * to profilers and debuggers.
 *
 * Supports:
 * - **perf map** — writes `/tmp/perf-<pid>.map` for Linux `perf` profiler
 * - **Custom listeners** — register callbacks for JIT events (load, unload)
 *
 * ```java
 * var debug = new JitDebugInfo();
 * debug.enablePerfMap();
 * jit.setDebugInfo(debug);
 *
 * jit.addModule(module);
 * // perf map file updated with symbol addresses
 *
 * // Or use custom listener:
 * debug.addListener((event) -> {
 *     System.out.println(event.name() + " at " + event.address());
 * });
 * ```
 */
class JitDebugInfo : AutoCloseable {

    /**
     * Event types for JIT debug listeners.
     */
    enum class EventType { LOAD, UNLOAD }

    /**
     * A JIT debug event — a symbol being loaded or unloaded.
     */
    data class JitEvent(
        val type: EventType,
        val name: String,
        val address: Long,
        val size: Long,
    )

    /**
     * Listener for JIT events.
     */
    fun interface JitEventListener {
        fun onEvent(event: JitEvent)
    }

    private var perfMapFile: File? = null
    private var perfMapWriter: java.io.BufferedWriter? = null
    private val listeners = mutableListOf<JitEventListener>()
    private val symbols = mutableListOf<SymbolEntry>()

    private data class SymbolEntry(
        val name: String,
        val address: Long,
        val size: Long,
        val moduleName: String,
    )

    /**
     * Enable perf map output. Creates `/tmp/perf-<pid>.map` for the Linux `perf` tool.
     * Each JIT'd function is written as: `<hex-address> <hex-size> <name>`.
     */
    fun enablePerfMap() {
        val pid = ProcessHandle.current().pid()
        val file = File("/tmp/perf-$pid.map")
        perfMapFile = file
        perfMapWriter = file.bufferedWriter()
    }

    /**
     * Enable perf map output to a custom file.
     */
    fun enablePerfMap(file: File) {
        perfMapFile = file
        perfMapWriter = file.bufferedWriter()
    }

    /**
     * Add a listener for JIT events.
     */
    fun addListener(listener: JitEventListener) {
        listeners.add(listener)
    }

    /**
     * Called when a module is loaded. Records all symbols and notifies listeners.
     */
    internal fun notifyLoad(module: JitModule) {
        val syms = module.symbols.values
        for (sym in syms) {
            val entry = SymbolEntry(sym.name, sym.address, sym.size, module.name)
            symbols.add(entry)
            writePerfMapEntry(sym.address, sym.size, sym.name)
            notifyListeners(JitEvent(EventType.LOAD, sym.name, sym.address, sym.size))
        }
    }

    /**
     * Called when a module is unloaded. Removes symbols and notifies listeners.
     */
    internal fun notifyUnload(module: JitModule) {
        val removed = symbols.filter { it.moduleName == module.name }
        symbols.removeAll { it.moduleName == module.name }
        for (entry in removed) {
            notifyListeners(JitEvent(EventType.UNLOAD, entry.name, entry.address, entry.size))
        }
        // Rewrite perf map without removed symbols
        rewritePerfMap()
    }

    /**
     * All currently tracked symbols.
     */
    fun symbols(): List<JitEvent> = symbols.map {
        JitEvent(EventType.LOAD, it.name, it.address, it.size)
    }

    private fun writePerfMapEntry(address: Long, size: Long, name: String) {
        val writer = perfMapWriter ?: return
        writer.write("${address.toHexString()} ${size.toHexString()} $name")
        writer.newLine()
        writer.flush()
    }

    private fun rewritePerfMap() {
        val writer = perfMapWriter ?: return
        val file = perfMapFile ?: return
        writer.close()
        perfMapWriter = file.bufferedWriter()
        for (entry in symbols) {
            writePerfMapEntry(entry.address, entry.size, entry.name)
        }
    }

    private fun notifyListeners(event: JitEvent) {
        for (listener in listeners) {
            listener.onEvent(event)
        }
    }

    private fun Long.toHexString(): String = java.lang.Long.toHexString(this)

    override fun close() {
        perfMapWriter?.close()
        perfMapWriter = null
        perfMapFile?.delete()
        symbols.clear()
    }
}
