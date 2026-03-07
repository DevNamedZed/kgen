package org.kgen.reflect

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.pe.PeReader
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
 * // Redirect puts() to our custom function
 * var hook = Hook.gotHook(binaryPath, "puts", myPutsAddress);
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
     * with a jump to [replacement]. x86-64 only.
     *
     * Returns an [ActiveHook] that can restore the original code.
     * The original bytes are saved so you can call the original function via a trampoline.
     */
    @JvmStatic
    fun inlineHook(target: Long, replacement: Long): ActiveHook {
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

    /**
     * Create a trampoline that executes the original code displaced by an inline hook,
     * then jumps back to the hooked function after the hook site.
     *
     * This lets you call the original function even while the hook is active.
     * Returns a [NativeCode] whose base address is the trampoline entry point.
     */
    @JvmStatic
    fun createTrampoline(originalBytes: ByteArray, continueAddress: Long): NativeCode {
        // Trampoline: execute the saved original instructions, then jump back
        val jumpBack = ByteArray(14)
        jumpBack[0] = 0xFF.toByte()
        jumpBack[1] = 0x25
        for (i in 0..7) jumpBack[6 + i] = ((continueAddress shr (i * 8)) and 0xFF).toByte()

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

    data class GotEntry(val symbolName: String, val offset: Long, val relocType: Int)
    data class IatEntry(val dllName: String, val functionName: String, val rva: Long)

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
}
