package org.kgen.jit

import org.kgen.codegen.CompiledCode
import org.kgen.ir.Module
import org.kgen.ir.text.IrPrinter
import org.kgen.target.x86.disasm.X86Disassembler

/**
 * Debugging and inspection tools for JIT-compiled code.
 *
 * Provides human-readable dumps of IR, native assembly, symbol tables,
 * and relocation info for JIT modules. Essential for debugging correctness
 * of compiled code before execution.
 *
 * ```java
 * var inspector = new JitInspector(engine);
 *
 * // Dump everything for a function
 * inspector.dumpFunction("initGame");
 *
 * // Compare IR vs native assembly
 * inspector.dumpIr("initGame");
 * inspector.dumpAsm("initGame");
 *
 * // List all symbols with addresses
 * inspector.dumpSymbols();
 *
 * // Dump raw bytes at a symbol
 * inspector.dumpHex("initGame", 64);
 * ```
 */
class JitInspector(
    private val engine: JitEngine,
) {
    private var irModule: Module? = null
    private var compiledCode: CompiledCode? = null

    /**
     * Set the IR module for IR-level inspection (before codegen).
     */
    fun setIrModule(module: Module) {
        this.irModule = module
    }

    /**
     * Set the compiled code for assembly-level inspection.
     */
    fun setCompiledCode(code: CompiledCode) {
        this.compiledCode = code
    }

    /**
     * Dump IR for a specific function.
     */
    fun dumpIr(functionName: String): String {
        val module = irModule ?: return "No IR module set"
        val function = module.functions.firstOrNull { it.name == functionName }
            ?: return "Function '$functionName' not found in IR"

        val singleFunctionModule = module.copy(functions = listOf(function))
        return IrPrinter.print(singleFunctionModule)
    }

    /**
     * Dump native x86 disassembly for a specific function.
     */
    fun dumpAsm(functionName: String): String {
        val symbol = engine.lookup(functionName)
            ?: return "Symbol '$functionName' not found in JIT"

        val code = compiledCode
        if (code != null) {
            return dumpAsmFromCompiledCode(functionName, code)
        }

        return "No compiled code available for disassembly"
    }

    /**
     * Dump both IR and ASM side by side for a function.
     */
    fun dumpFunction(functionName: String): String {
        val builder = StringBuilder()

        builder.appendLine("=== $functionName ===")
        builder.appendLine()

        val ir = dumpIr(functionName)
        if (!ir.startsWith("No ") && !ir.startsWith("Function")) {
            builder.appendLine("--- IR ---")
            builder.appendLine(ir)
        }

        val asm = dumpAsm(functionName)
        if (!asm.startsWith("No ") && !asm.startsWith("Symbol")) {
            builder.appendLine("--- Assembly (x86-64) ---")
            builder.appendLine(asm)
        }

        return builder.toString()
    }

    /**
     * List all symbols with their addresses.
     */
    fun dumpSymbols(): String {
        val builder = StringBuilder()
        builder.appendLine("=== JIT Symbols ===")

        val code = compiledCode
        if (code != null) {
            for (sym in code.symbols.sortedBy { it.offset }) {
                val section = when {
                    sym.dataOffset >= 0 -> ".data+${sym.dataOffset}"
                    sym.rodataOffset >= 0 -> ".rodata+${sym.rodataOffset}"
                    sym.tdataOffset >= 0 -> ".tdata+${sym.tdataOffset}"
                    else -> ".text+${sym.offset}"
                }
                builder.appendLine("  ${sym.name}: $section (${sym.kind})")
            }
        }

        return builder.toString()
    }

    /**
     * Dump relocations for the compiled code.
     */
    fun dumpRelocations(): String {
        val code = compiledCode ?: return "No compiled code"
        val builder = StringBuilder()
        builder.appendLine("=== Relocations (${code.relocations.size}) ===")

        for (rel in code.relocations.take(50)) {
            builder.appendLine("  offset=${rel.offset} symbol=${rel.symbol} type=${rel.type} addend=${rel.addend}")
        }
        if (code.relocations.size > 50) {
            builder.appendLine("  ... and ${code.relocations.size - 50} more")
        }

        return builder.toString()
    }

    /**
     * Dump external symbols (unresolved references).
     */
    fun dumpExternals(): String {
        val code = compiledCode ?: return "No compiled code"
        val builder = StringBuilder()
        builder.appendLine("=== External Symbols (${code.externalSymbols.size}) ===")
        for (name in code.externalSymbols.sorted()) {
            val resolved = engine.lookup(name)
            val status = if (resolved != null) { "resolved → 0x${resolved.address.toString(16)}" } else { "UNRESOLVED" }
            builder.appendLine("  $name: $status")
        }
        return builder.toString()
    }

    /**
     * Dump summary statistics.
     */
    fun dumpSummary(): String {
        val code = compiledCode ?: return "No compiled code"
        val module = irModule

        val builder = StringBuilder()
        builder.appendLine("=== JIT Module Summary ===")
        builder.appendLine("  Text section: ${code.textBytes.size} bytes")
        builder.appendLine("  Rodata section: ${code.rodataBytes.size} bytes")
        builder.appendLine("  Data section: ${code.dataBytes.size} bytes")
        builder.appendLine("  Symbols: ${code.symbols.size}")
        builder.appendLine("  Relocations: ${code.relocations.size}")
        builder.appendLine("  External refs: ${code.externalSymbols.size}")

        if (module != null) {
            val funcCount = module.functions.count { !it.isExternal }
            val externCount = module.functions.count { it.isExternal }
            builder.appendLine("  IR functions: $funcCount defined, $externCount external")
            builder.appendLine("  IR globals: ${module.globals.size}")
        }

        return builder.toString()
    }

    private fun dumpAsmFromCompiledCode(functionName: String, code: CompiledCode): String {
        val symbol = code.symbols.firstOrNull { it.name == functionName }
            ?: return "Symbol '$functionName' not found in compiled code"

        if (symbol.rodataOffset >= 0 || symbol.dataOffset >= 0 || symbol.tdataOffset >= 0) {
            return "Symbol '$functionName' is data, not code"
        }

        val startOffset = symbol.offset.toInt()
        val nextSymbol = code.symbols
            .filter { it.offset > symbol.offset && it.rodataOffset < 0 && it.dataOffset < 0 && it.tdataOffset < 0 }
            .minByOrNull { it.offset }
        val endOffset = nextSymbol?.offset?.toInt() ?: code.textBytes.size
        val functionBytes = code.textBytes.copyOfRange(startOffset, endOffset)

        if (functionBytes.isEmpty()) {
            return "Function '$functionName' has no code bytes"
        }

        val disassembler = X86Disassembler()
        val instructions = disassembler.disassemble(functionBytes, startOffset.toLong())

        val builder = StringBuilder()
        builder.appendLine("$functionName: (${functionBytes.size} bytes)")

        for (instruction in instructions) {
            val hexBytes = instruction.bytes.joinToString(" ") { "%02x".format(it) }
            val address = "%08x".format(instruction.address)
            builder.appendLine("  $address: ${hexBytes.padEnd(24)} ${instruction.mnemonic} ${instruction.operands}")
        }

        return builder.toString()
    }
}
