package org.kgen.tools

import org.kgen.binary.SectionKind
import org.kgen.binary.Symbol
import org.kgen.binary.SymbolKind
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.lang.invoke.MethodHandle

class FfmNativeLoader : RuntimeLoader {

    override fun canLoadFormat(format: String): Boolean =
        format in setOf("elf", "pe", "macho", "raw", "objectfile")

    override fun load(name: String, bytes: ByteArray): RuntimeLoader.LoadedModule =
        throw UnsupportedOperationException("Use loadObjectFile() for structured loading, or loadCode() for raw code")

    fun loadCode(code: ByteArray, symbols: Map<String, Long> = emptyMap()): RuntimeLoader.LoadedModule {
        val arena = Arena.ofConfined()
        val execMem = allocateExecutable(code.size.toLong(), arena)
        execMem.copyFrom(MemorySegment.ofArray(code))
        return FfmLoadedModule("raw", arena, execMem, symbols)
    }

    fun loadObjectFile(obj: org.kgen.binary.ObjectFile): RuntimeLoader.LoadedModule {
        val textSection = obj.sections.firstOrNull { it.kind == SectionKind.TEXT }
            ?: throw IllegalArgumentException("No TEXT section found")
        val code = textSection.data
        if (code.isEmpty()) throw IllegalArgumentException("TEXT section is empty")

        val symbolMap = mutableMapOf<String, Long>()
        for (sym in obj.symbols) {
            if (sym.kind != SymbolKind.UNDEFINED) {
                symbolMap[sym.name] = sym.value
            }
        }

        val arena = Arena.ofConfined()
        val execMem = allocateExecutable(code.size.toLong(), arena)
        execMem.copyFrom(MemorySegment.ofArray(code))
        return FfmLoadedModule(obj.format.name, arena, execMem, symbolMap)
    }

    companion object {
        private val linker = Linker.nativeLinker()
        private val nativeLookup = linker.defaultLookup()

        private val isWindows = System.getProperty("os.name").lowercase().contains("win")
        private val isLinux = System.getProperty("os.name").lowercase().contains("linux")
        private val isMac = System.getProperty("os.name").lowercase().contains("mac")

        private fun allocateExecutable(size: Long, arena: Arena): MemorySegment {
            if (isLinux || isMac) {
                return mmapExecutable(size, arena)
            }
            if (isWindows) {
                return virtualAllocExecutable(size, arena)
            }
            throw UnsupportedOperationException("Unsupported OS: ${System.getProperty("os.name")}")
        }

        private fun mmapExecutable(size: Long, arena: Arena): MemorySegment {
            val mmapHandle = linker.downcallHandle(
                nativeLookup.find("mmap").orElseThrow { UnsupportedOperationException("mmap not found") },
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG)
            )

            val protReadWriteExec = 0x1 or 0x2 or 0x4 // PROT_READ | PROT_WRITE | PROT_EXEC
            val mapPrivateAnon = 0x02 or 0x20          // MAP_PRIVATE | MAP_ANONYMOUS

            val result = mmapHandle.invoke(
                MemorySegment.NULL, size, protReadWriteExec, mapPrivateAnon, -1, 0L
            ) as MemorySegment

            if (result.address() == -1L) {
                throw RuntimeException("mmap failed")
            }

            val munmapHandle = linker.downcallHandle(
                nativeLookup.find("munmap").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG)
            )

            val addr = result.address()
            val reinterpreted = result.reinterpret(size, arena) { _ ->
                munmapHandle.invoke(MemorySegment.ofAddress(addr).reinterpret(size), size)
            }
            return reinterpreted
        }

        private val kernel32 by lazy {
            SymbolLookup.libraryLookup("kernel32", Arena.global())
        }

        private fun virtualAllocExecutable(size: Long, arena: Arena): MemorySegment {

            val virtualAllocHandle = linker.downcallHandle(
                kernel32.find("VirtualAlloc").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT)
            )

            val memCommitReserve = 0x1000 or 0x2000 // MEM_COMMIT | MEM_RESERVE
            val pageExecuteReadWrite = 0x40          // PAGE_EXECUTE_READWRITE

            val result = virtualAllocHandle.invoke(
                MemorySegment.NULL, size, memCommitReserve, pageExecuteReadWrite
            ) as MemorySegment

            if (result.address() == 0L) {
                throw RuntimeException("VirtualAlloc failed")
            }

            val virtualFreeHandle = linker.downcallHandle(
                kernel32.find("VirtualFree").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT)
            )

            val memRelease = 0x8000
            val addr = result.address()
            val reinterpreted = result.reinterpret(size, arena) { _ ->
                virtualFreeHandle.invoke(MemorySegment.ofAddress(addr).reinterpret(size), 0L, memRelease)
            }
            return reinterpreted
        }

        fun descriptorFor(returnType: ReturnType, paramCount: Int): FunctionDescriptor {
            val params = Array(paramCount) { JAVA_LONG }
            return when (returnType) {
                ReturnType.INT -> FunctionDescriptor.of(JAVA_INT, *params)
                ReturnType.LONG -> FunctionDescriptor.of(JAVA_LONG, *params)
                ReturnType.FLOAT -> FunctionDescriptor.of(JAVA_FLOAT, *params)
                ReturnType.DOUBLE -> FunctionDescriptor.of(JAVA_DOUBLE, *params)
                ReturnType.VOID -> FunctionDescriptor.ofVoid(*params)
            }
        }
    }

    enum class ReturnType { INT, LONG, FLOAT, DOUBLE, VOID }

    private class FfmLoadedModule(
        override val name: String,
        private val arena: Arena,
        private val execMem: MemorySegment,
        private val symbolMap: Map<String, Long>,
    ) : RuntimeLoader.LoadedModule {

        override fun findSymbol(name: String): Long? {
            val offset = symbolMap[name] ?: return null
            return execMem.address() + offset
        }

        override fun findFunction(name: String): RuntimeLoader.NativeFunction? {
            val offset = symbolMap[name] ?: return null
            return FfmNativeFunction(name, execMem, offset)
        }

        override fun symbols(): List<String> = symbolMap.keys.toList()

        override fun close() {
            arena.close()
        }
    }

    private class FfmNativeFunction(
        override val name: String,
        private val execMem: MemorySegment,
        private val offset: Long,
    ) : RuntimeLoader.NativeFunction {

        override val address: Long get() = execMem.address() + offset

        private fun segment(): MemorySegment = MemorySegment.ofAddress(address)

        private fun handle(desc: FunctionDescriptor): MethodHandle =
            linker.downcallHandle(segment(), desc)

        override fun callInt(vararg args: Long): Int {
            val desc = descriptorFor(ReturnType.INT, args.size)
            val h = handle(desc)
            return when (args.size) {
                0 -> h.invoke() as Int
                1 -> h.invoke(args[0]) as Int
                2 -> h.invoke(args[0], args[1]) as Int
                3 -> h.invoke(args[0], args[1], args[2]) as Int
                4 -> h.invoke(args[0], args[1], args[2], args[3]) as Int
                else -> h.invokeWithArguments(*args.map { it as Any }.toTypedArray()) as Int
            }
        }

        override fun callLong(vararg args: Long): Long {
            val desc = descriptorFor(ReturnType.LONG, args.size)
            val h = handle(desc)
            return when (args.size) {
                0 -> h.invoke() as Long
                1 -> h.invoke(args[0]) as Long
                2 -> h.invoke(args[0], args[1]) as Long
                3 -> h.invoke(args[0], args[1], args[2]) as Long
                4 -> h.invoke(args[0], args[1], args[2], args[3]) as Long
                else -> h.invokeWithArguments(*args.map { it as Any }.toTypedArray()) as Long
            }
        }

        override fun callFloat(vararg args: Long): Float {
            val desc = descriptorFor(ReturnType.FLOAT, args.size)
            val h = handle(desc)
            return when (args.size) {
                0 -> h.invoke() as Float
                1 -> h.invoke(args[0]) as Float
                else -> h.invokeWithArguments(*args.map { it as Any }.toTypedArray()) as Float
            }
        }

        override fun callDouble(vararg args: Long): Double {
            val desc = descriptorFor(ReturnType.DOUBLE, args.size)
            val h = handle(desc)
            return when (args.size) {
                0 -> h.invoke() as Double
                1 -> h.invoke(args[0]) as Double
                else -> h.invokeWithArguments(*args.map { it as Any }.toTypedArray()) as Double
            }
        }

        override fun callVoid(vararg args: Long) {
            val desc = descriptorFor(ReturnType.VOID, args.size)
            val h = handle(desc)
            when (args.size) {
                0 -> h.invoke()
                1 -> h.invoke(args[0])
                2 -> h.invoke(args[0], args[1])
                3 -> h.invoke(args[0], args[1], args[2])
                4 -> h.invoke(args[0], args[1], args[2], args[3])
                else -> h.invokeWithArguments(*args.map { it as Any }.toTypedArray())
            }
        }
    }
}
