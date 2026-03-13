package org.kgen.reflect

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.ir.target.Arch
import org.kgen.ir.target.Target
import org.kgen.target.arm64.Arm64Register
import org.kgen.target.arm64.asm.Arm64Assembler
import org.kgen.target.riscv.X6
import org.kgen.target.riscv.asm.RiscVAssembler
import org.kgen.target.x86.asm.X86Assembler
import java.io.File

/**
 * Function hooking via PLT/GOT (ELF) and IAT (PE).
 *
 * PLT/GOT hooking: overwrite the GOT entry for a function so all calls through
 * the PLT are redirected to your code.
 *
 * IAT hooking: overwrite the Import Address Table entry for a function so all
 * calls through the IAT are redirected.
 *
 * ```java
 * // Redirect puts() via GOT: two-step API
 * var got = Hook.findGotEntry(binaryPath, "puts");
 * var hook = Hook.hookGot(baseAddress + got.getOffset(), myPutsAddress);
 * // ... all puts() calls now go to myPutsAddress ...
 * hook.unhook();  // restore original
 *
 * // Inline hook: redirect any function to another
 * var hook = Hook.inlineHook(targetAddr, replacementAddr);
 * hook.unhook();
 * ```
 */
object Hook {

    /**
     * An active hook that can be reverted.
     */
    interface ActiveHook : AutoCloseable {
        /** The address that was hooked. */
        val address: Long

        /** The original value/code before the hook was installed. */
        val originalBytes: ByteArray

        /** Remove the hook and restore the original behavior. */
        fun unhook()

        override fun close() = unhook()
    }

    // --- Inline hooks ---

    /**
     * Install an inline hook: overwrite the first bytes of the function at [target]
     * with a jump to [replacement].
     *
     * Auto-detects the host architecture. For explicit arch control, use the overload
     * that takes an [Arch] parameter.
     *
     * Returns an [ActiveHook] that can restore the original code.
     */
    @JvmStatic
    fun inlineHook(target: Long, replacement: Long): ActiveHook {
        return inlineHook(target, replacement, Target.native().arch)
    }

    /**
     * Install an inline hook for a specific architecture.
     */
    @JvmStatic
    fun inlineHook(target: Long, replacement: Long, arch: Arch): ActiveHook {
        return when (arch) {
            Arch.X86_64 -> inlineHookX86(target, replacement)
            Arch.ARM64 -> inlineHookArm64(target, replacement)
            Arch.RISCV64 -> inlineHookRiscV(target, replacement)
            else -> error("Inline hook not supported for $arch")
        }
    }

    private fun inlineHookX86(target: Long, replacement: Long): ActiveHook {
        val distance = replacement - target - 5
        val use64bit = distance > Int.MAX_VALUE || distance < Int.MIN_VALUE
        val patchSize = if (use64bit) 14 else 5
        val original = CodePatch.read(target, patchSize)
        if (use64bit) {
            CodePatch.writeAbsoluteJump(target, replacement)
        } else {
            CodePatch.writeJump(target, replacement)
        }
        return InlineHookImpl(target, original)
    }

    private fun inlineHookArm64(target: Long, replacement: Long): ActiveHook {
        val distance = replacement - target
        val useAbsolute = distance < -0x8000000L || distance > 0x7FFFFFFL
        val patchSize = if (useAbsolute) 20 else 4
        val original = CodePatch.read(target, patchSize)
        if (useAbsolute) {
            CodePatch.writeArm64AbsoluteJump(target, replacement)
        } else {
            CodePatch.writeArm64Jump(target, replacement)
        }
        return InlineHookImpl(target, original)
    }

    private fun inlineHookRiscV(target: Long, replacement: Long): ActiveHook {
        val distance = replacement - target
        val useAbsolute = distance < -0x100000L || distance > 0xFFFFFL
        if (useAbsolute) {
            // Variable-length absolute jump — compute size by assembling
            val asm = RiscVAssembler()
            asm.li(X6, replacement)
            asm.jalr(org.kgen.target.riscv.X0, X6, 0)
            val patchSize = asm.toByteArray().size
            val original = CodePatch.read(target, patchSize)
            CodePatch.writeRiscVAbsoluteJump(target, replacement)
            return InlineHookImpl(target, original)
        } else {
            val original = CodePatch.read(target, 4)
            CodePatch.writeRiscVJump(target, replacement)
            return InlineHookImpl(target, original)
        }
    }

    /**
     * Create a trampoline that executes the original code displaced by an inline hook,
     * then jumps back to the hooked function after the hook site.
     *
     * Auto-detects the host architecture.
     */
    @JvmStatic
    fun createTrampoline(originalBytes: ByteArray, continueAddress: Long): NativeCode {
        return createTrampoline(originalBytes, continueAddress, Target.native().arch)
    }

    /**
     * Create a trampoline for a specific architecture.
     */
    @JvmStatic
    fun createTrampoline(originalBytes: ByteArray, continueAddress: Long, arch: Arch): NativeCode {
        val jumpBack = when (arch) {
            Arch.X86_64 -> {
                val asm = X86Assembler()
                asm.emitByte(0xFF)
                asm.emitByte(0x25)
                asm.emitInt32(0)
                asm.emitInt64(continueAddress)
                asm.toByteArray()
            }
            Arch.ARM64 -> {
                val asm = Arm64Assembler()
                asm.movz(Arm64Register.X16, (continueAddress and 0xFFFF).toInt(), 0)
                asm.movk(Arm64Register.X16, ((continueAddress shr 16) and 0xFFFF).toInt(), 16)
                asm.movk(Arm64Register.X16, ((continueAddress shr 32) and 0xFFFF).toInt(), 32)
                asm.movk(Arm64Register.X16, ((continueAddress shr 48) and 0xFFFF).toInt(), 48)
                asm.br(Arm64Register.X16)
                asm.bytes()
            }
            Arch.RISCV64 -> {
                val asm = RiscVAssembler()
                asm.li(X6, continueAddress)
                asm.jalr(org.kgen.target.riscv.X0, X6, 0)
                asm.toByteArray()
            }
            else -> error("Trampoline not supported for $arch")
        }
        val trampoline = originalBytes + jumpBack
        return NativeCode.loadBytes(trampoline, mapOf("trampoline" to 0L))
    }

    // --- GOT hooks (ELF) ---

    /**
     * Find the GOT entry address for [symbolName] in the ELF binary at [binaryPath].
     * Returns null if not found.
     *
     * Note: this reads the binary file to find the GOT offset. The actual GOT in memory
     * is at the process base + offset. For PIE binaries, you need the actual load address.
     */
    @JvmStatic
    fun findGotEntry(binaryPath: String, symbolName: String): GotEntry? {
        val data = File(binaryPath).readBytes()
        val elf = ElfReader.read(data)

        // Find .rela.plt relocations for the symbol
        val relaPlt = elf.sections.firstOrNull { it.name == ".rela.plt" } ?: return null
        val dynsym = elf.dynamicSymbols

        for (reloc in elf.relocations) {
            if (reloc.sectionName == ".rela.plt") {
                val sym = dynsym.getOrNull(reloc.symbolIndex)
                if (sym?.name == symbolName) {
                    return GotEntry(symbolName, reloc.offset, reloc.type)
                }
            }
        }
        return null
    }

    /**
     * Overwrite a GOT entry at [gotAddress] to point to [newTarget].
     * Returns an [ActiveHook] that can restore the original address.
     */
    @JvmStatic
    fun hookGot(gotAddress: Long, newTarget: Long): ActiveHook {
        val original = NativeMemory.readBytes(gotAddress, 8)
        NativeMemory.mprotect(gotAddress, 8, NativeMemory.PROT_READ or NativeMemory.PROT_WRITE)
        val seg = java.lang.foreign.MemorySegment.ofAddress(gotAddress).reinterpret(8)
        seg.set(java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED, 0, newTarget)
        return GotHookImpl(gotAddress, original)
    }

    // --- IAT hooks (PE) ---

    /**
     * Find the IAT entry for [functionName] imported from [dllName] in the PE at [binaryPath].
     */
    @JvmStatic
    fun findIatEntry(binaryPath: String, dllName: String, functionName: String): IatEntry? {
        val data = File(binaryPath).readBytes()
        val pe = PeReader.read(data)

        val dir = pe.importDirectories.firstOrNull {
            it.name.equals(dllName, ignoreCase = true)
        } ?: return null

        for ((i, entry) in dir.entries.withIndex()) {
            if (entry.name == functionName) {
                // IAT RVA = importAddressTableRVA + i * 8 (for 64-bit)
                val iatRva = dir.importAddressTableRVA + i * 8L
                return IatEntry(dllName, functionName, iatRva)
            }
        }
        return null
    }

    /**
     * Overwrite an IAT entry at [iatAddress] to point to [newTarget].
     */
    @JvmStatic
    fun hookIat(iatAddress: Long, newTarget: Long): ActiveHook {
        val original = NativeMemory.readBytes(iatAddress, 8)
        NativeMemory.mprotect(iatAddress, 8, NativeMemory.PROT_READ or NativeMemory.PROT_WRITE)
        val seg = java.lang.foreign.MemorySegment.ofAddress(iatAddress).reinterpret(8)
        seg.set(java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED, 0, newTarget)
        return IatHookImpl(iatAddress, original)
    }

    // --- Stub hooks (Mach-O) ---

    /**
     * Find the `__la_symbol_ptr` entry for [symbolName] in the Mach-O binary at [binaryPath].
     * Returns null if not found.
     *
     * The `__la_symbol_ptr` table is the Mach-O equivalent of ELF's GOT — each entry
     * is a function pointer that the dynamic linker fills in on first call.
     */
    @JvmStatic
    fun findStubEntry(binaryPath: String, symbolName: String): StubEntry? {
        val data = File(binaryPath).readBytes()
        val macho = MachOReader.read(data)

        val laSymbolPtr = macho.sectionByName("__DATA", "__la_symbol_ptr") ?: return null

        // The indirect symbol table maps __la_symbol_ptr entries to symbol indices.
        // For simplicity, match by symbol name in the symbol table and check
        // if the symbol appears as an undefined external import.
        val undefinedSymbols = macho.symbols.filter { it.isUndefined && it.isExternal }
        val symIndex = undefinedSymbols.indexOfFirst { it.name == symbolName || it.name == "_$symbolName" }
        if (symIndex < 0) return null

        // Each __la_symbol_ptr entry is 8 bytes (64-bit pointer)
        val entryOffset = laSymbolPtr.address + symIndex * 8L
        return StubEntry(symbolName, entryOffset)
    }

    /**
     * Overwrite a Mach-O `__la_symbol_ptr` entry to point to [newTarget].
     * Works the same as GOT hooking — overwrites an 8-byte pointer.
     */
    @JvmStatic
    fun hookStub(stubAddress: Long, newTarget: Long): ActiveHook {
        val original = NativeMemory.readBytes(stubAddress, 8)
        NativeMemory.mprotect(stubAddress, 8, NativeMemory.PROT_READ or NativeMemory.PROT_WRITE)
        val seg = java.lang.foreign.MemorySegment.ofAddress(stubAddress).reinterpret(8)
        seg.set(java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED, 0, newTarget)
        return StubHookImpl(stubAddress, original)
    }

    /**
     * Convenience: find and hook a Mach-O stub in one call.
     * Requires the module's base address to compute the absolute stub address.
     */
    @JvmStatic
    fun stub(binaryPath: String, symbolName: String, newTarget: Long, baseAddress: Long = 0): ActiveHook {
        val entry = findStubEntry(binaryPath, symbolName)
            ?: throw IllegalArgumentException("Stub entry not found for '$symbolName' in $binaryPath")
        return hookStub(baseAddress + entry.address, newTarget)
    }

    data class GotEntry(val symbolName: String, val offset: Long, val relocType: Int)
    data class IatEntry(val dllName: String, val functionName: String, val rva: Long)
    data class StubEntry(val symbolName: String, val address: Long)

    private class InlineHookImpl(override val address: Long, override val originalBytes: ByteArray) : ActiveHook {
        override fun unhook() {
            CodePatch.write(address, originalBytes)
        }
    }

    private class GotHookImpl(override val address: Long, override val originalBytes: ByteArray) : ActiveHook {
        override fun unhook() {
            NativeMemory.mprotect(address, 8, NativeMemory.PROT_READ or NativeMemory.PROT_WRITE)
            NativeMemory.writeBytes(address, originalBytes)
        }
    }

    private class IatHookImpl(override val address: Long, override val originalBytes: ByteArray) : ActiveHook {
        override fun unhook() {
            NativeMemory.mprotect(address, 8, NativeMemory.PROT_READ or NativeMemory.PROT_WRITE)
            NativeMemory.writeBytes(address, originalBytes)
        }
    }

    private class StubHookImpl(override val address: Long, override val originalBytes: ByteArray) : ActiveHook {
        override fun unhook() {
            NativeMemory.mprotect(address, 8, NativeMemory.PROT_READ or NativeMemory.PROT_WRITE)
            NativeMemory.writeBytes(address, originalBytes)
        }
    }
}
