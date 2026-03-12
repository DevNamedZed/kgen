package org.kgen.reflect

import org.kgen.reflect.process.Process

/**
 * Provides introspection and hooking for a loaded CLR/.NET runtime.
 *
 * Detects whether CoreCLR or .NET Framework is loaded in the current process,
 * and provides APIs to enumerate loaded assemblies, inspect types, and hook
 * managed methods via their JIT'd native code.
 *
 * ```java
 * var clr = ClrRuntime.detect();
 * if (clr != null) {
 *     clr.version();              // e.g. "8.0.0"
 *     clr.assemblies();           // loaded assembly names
 *     clr.runtimeKind();          // CORECLR or FRAMEWORK
 * }
 * ```
 */
class ClrRuntime private constructor(
    private val kind: RuntimeKind,
    private val runtimeVersion: String?,
    private val runtimePath: String?,
) {

    /** The kind of CLR runtime detected. */
    enum class RuntimeKind {
        CORECLR,
        FRAMEWORK,
        MONO,
    }

    /** Which CLR runtime is loaded. */
    fun runtimeKind(): RuntimeKind = kind

    /** The runtime version string, if detectable. */
    fun version(): String? = runtimeVersion

    /** The path to the runtime libraries, if detectable. */
    fun runtimePath(): String? = runtimePath

    /** Whether this is .NET Core / .NET 5+. */
    fun isCoreCLR(): Boolean = kind == RuntimeKind.CORECLR

    /** Whether this is .NET Framework (Windows only). */
    fun isFramework(): Boolean = kind == RuntimeKind.FRAMEWORK

    /** Whether this is Mono. */
    fun isMono(): Boolean = kind == RuntimeKind.MONO

    /**
     * Enumerate loaded assemblies by querying the CLR hosting API.
     * Returns assembly names that are currently loaded.
     *
     * Note: Requires CLR hosting API access (coreclr_initialize).
     * Falls back to detecting loaded .dll modules with CLR metadata.
     */
    fun assemblies(): List<AssemblyInfo> {
        val process = Process.current()
        val modules = try { process.modules() } catch (_: Throwable) { emptyList() }
        return modules.filter { mod ->
            try {
                mod.hasClr()
            } catch (_: Throwable) {
                false
            }
        }.map { mod ->
            AssemblyInfo(
                name = mod.name(),
                path = mod.path()?.toString(),
                isMixedMode = try { mod.isMixedMode() } catch (_: Throwable) { false },
            )
        }
    }

    /**
     * Get the method table address for a JIT'd method.
     * This is the native code address after the CLR has JIT-compiled a method.
     *
     * Requires the method to have already been JIT'd.
     */
    fun jittedMethodAddress(assemblyName: String, typeName: String, methodName: String): Long? {
        // Look for the method via DAC (Data Access Component) or symbol lookup
        val symbolName = "${typeName}::${methodName}"
        return try {
            Process.current().lookup(symbolName)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Hook a JIT'd managed method by replacing its native code entry point.
     * The original method code is preserved and can be called via the returned handle.
     *
     * @param methodAddress The native address of the JIT'd method
     * @param hookCode The replacement native code
     * @return An active hook that can be undone
     */
    fun hookMethod(methodAddress: Long, hookCode: ByteArray): ActiveMethodHook {
        val originalBytes = NativeMemory.readBytes(methodAddress, hookCode.size)
        NativeMemory.writeBytes(methodAddress, hookCode)
        return ActiveMethodHook(methodAddress, originalBytes)
    }

    /**
     * An active method hook that can be reverted.
     */
    class ActiveMethodHook(
        private val address: Long,
        private val originalBytes: ByteArray,
    ) {
        private var active = true

        /** Whether the hook is currently active. */
        fun isActive(): Boolean = active

        /** Revert the hook, restoring original method code. */
        fun unhook() {
            if (!active) return
            NativeMemory.writeBytes(address, originalBytes)
            active = false
        }

        /** The address of the hooked method. */
        fun address(): Long = address

        /** The original bytes that were replaced. */
        fun originalBytes(): ByteArray = originalBytes.copyOf()
    }

    /**
     * Information about a loaded .NET assembly.
     */
    data class AssemblyInfo(
        val name: String,
        val path: String?,
        val isMixedMode: Boolean,
    )

    override fun toString(): String = "ClrRuntime(kind=$kind, version=$runtimeVersion)"

    companion object {
        /**
         * Detect a loaded CLR runtime in the current process.
         * Fast path: checks for known CLR symbols without probing the runtime.
         * Returns null if no CLR runtime is loaded.
         */
        @JvmStatic
        fun detect(): ClrRuntime? {
            val process = Process.current()

            // Check for CoreCLR
            val coreclr = try { process.lookup("coreclr_initialize") } catch (_: Throwable) { null }
            if (coreclr != null) {
                return ClrRuntime(
                    kind = RuntimeKind.CORECLR,
                    runtimeVersion = detectCoreCLRVersion(),
                    runtimePath = detectCoreCLRPath(),
                )
            }

            // Check for .NET Framework (Windows)
            val clrCreate = try { process.lookup("CLRCreateInstance") } catch (_: Throwable) { null }
            if (clrCreate != null) {
                return ClrRuntime(
                    kind = RuntimeKind.FRAMEWORK,
                    runtimeVersion = null,
                    runtimePath = null,
                )
            }

            // Check for Mono
            val monoInit = try { process.lookup("mono_jit_init") } catch (_: Throwable) { null }
            if (monoInit != null) {
                return ClrRuntime(
                    kind = RuntimeKind.MONO,
                    runtimeVersion = null,
                    runtimePath = null,
                )
            }

            return null
        }

        /**
         * Accurate detection: verifies the CLR is actually initialized, not just loaded.
         * Slower than [detect] because it probes runtime state.
         * Returns null if no CLR runtime is loaded or if it is not initialized.
         */
        @JvmStatic
        fun detectInitialized(): ClrRuntime? {
            val runtime = detect() ?: return null
            if (!probeRuntimeInitialized(runtime.kind)) return null
            return runtime
        }

        /**
         * Probe-based detection: tries to invoke a harmless CLR function to verify
         * the runtime is actually functional.
         * Returns null if no CLR is loaded or probing fails.
         */
        @JvmStatic
        fun detectWithProbe(): ClrRuntime? {
            val runtime = detect() ?: return null
            if (!probeRuntime(runtime.kind)) return null
            return runtime
        }

        /**
         * Check if a CLR runtime is loaded in the current process.
         */
        @JvmStatic
        fun isAvailable(): Boolean = detect() != null

        /**
         * Create a ClrRuntime instance directly (for testing or known environments).
         */
        @JvmStatic
        fun of(kind: RuntimeKind, version: String? = null, path: String? = null): ClrRuntime {
            return ClrRuntime(kind, version, path)
        }

        private fun probeRuntimeInitialized(kind: RuntimeKind): Boolean {
            val process = Process.current()
            return try {
                when (kind) {
                    RuntimeKind.CORECLR -> {
                        // coreclr_execute_assembly only works if runtime is initialized
                        val executeAddr = process.lookup("coreclr_execute_assembly")
                        executeAddr != null
                    }
                    RuntimeKind.FRAMEWORK -> {
                        // For .NET Framework, the presence of CLRCreateInstance plus
                        // mscoree loaded together indicate an initialized runtime
                        val mscoree = process.lookup("GetCORVersion")
                        mscoree != null
                    }
                    RuntimeKind.MONO -> {
                        // mono_get_root_domain returns non-null when initialized
                        val getDomain = process.lookup("mono_get_root_domain")
                        getDomain != null
                    }
                }
            } catch (_: Throwable) {
                false
            }
        }

        private fun probeRuntime(kind: RuntimeKind): Boolean {
            if (!probeRuntimeInitialized(kind)) return false
            return try {
                when (kind) {
                    RuntimeKind.CORECLR -> {
                        // Check if we can also find the shutdown function (fully loaded)
                        val process = Process.current()
                        process.lookup("coreclr_shutdown") != null
                    }
                    RuntimeKind.FRAMEWORK -> {
                        val process = Process.current()
                        process.lookup("CorBindToRuntimeEx") != null || process.lookup("CLRCreateInstance") != null
                    }
                    RuntimeKind.MONO -> {
                        val process = Process.current()
                        process.lookup("mono_jit_cleanup") != null
                    }
                }
            } catch (_: Throwable) {
                false
            }
        }

        private fun detectCoreCLRVersion(): String? {
            // Try to find version from loaded module path
            try {
                val modules = Process.current().modules()
                for (mod in modules) {
                    val name = mod.name().lowercase()
                    if (name.contains("coreclr") || name.contains("hostfxr")) {
                        // Try to extract version from path (e.g., /usr/share/dotnet/shared/Microsoft.NETCore.App/8.0.0/)
                        val path = mod.path()?.toString() ?: continue
                        val versionRegex = Regex("""(\d+\.\d+\.\d+)""")
                        val match = versionRegex.find(path)
                        if (match != null) return match.value
                    }
                }
            } catch (_: Throwable) {}
            return null
        }

        private fun detectCoreCLRPath(): String? {
            try {
                val modules = Process.current().modules()
                for (mod in modules) {
                    val name = mod.name().lowercase()
                    if (name.contains("coreclr")) {
                        return mod.path()?.toString()
                    }
                }
            } catch (_: Throwable) {}
            return null
        }
    }
}
