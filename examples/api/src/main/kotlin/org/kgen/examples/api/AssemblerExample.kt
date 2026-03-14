package org.kgen.examples.api

import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.CompiledCode
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.X86Memory
import org.kgen.target.x86.X86Register
import org.kgen.target.x86.asm.X86Assembler
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.x86.disasm.X86Disassembler

/**
 * Demonstrates the three-layer code generation API:
 *
 * 1. **Assembler** — emit raw machine code instructions
 * 2. **CodeGenerator** — compile IR modules to machine code
 * 3. **Disassembler** — decode machine code back to instructions
 */
object AssemblerExample {

    /**
     * Uses the x86-64 assembler directly to emit a function that adds two integers.
     * This is the lowest-level API — you control every byte.
     *
     * ```asm
     * add_ints:
     *   lea eax, [rdi + rsi]    ; result = first + second (System V ABI)
     *   ret
     * ```
     */
    @JvmStatic
    fun assembleAddFunction(): ByteArray {
        val assembler = X86Assembler()

        // System V AMD64 ABI: first arg in EDI, second in ESI, return in EAX
        assembler.lea(X86Register.EAX,
            X86Memory.base(X86Register.RDI).index(X86Register.RSI).build())
        assembler.ret()

        return assembler.toByteArray()
    }

    /**
     * Uses the x86-64 assembler to emit a loop that sums integers from 1 to N.
     *
     * ```asm
     * sum_to_n:
     *   xor eax, eax        ; accumulator = 0
     *   mov ecx, edi         ; counter = n
     * .loop:
     *   add eax, ecx         ; accumulator += counter
     *   dec ecx              ; counter--
     *   jnz .loop            ; if counter != 0, loop
     *   ret
     * ```
     */
    @JvmStatic
    fun assembleSumLoop(): ByteArray {
        val assembler = X86Assembler()

        assembler.xor_(X86Register.EAX, X86Register.EAX)
        assembler.mov(X86Register.ECX, X86Register.EDI)

        assembler.label("loop")
        assembler.add(X86Register.EAX, X86Register.ECX)
        assembler.dec(X86Register.ECX)
        assembler.jccLabel(0x05, "loop") // JNZ

        assembler.ret()

        return assembler.toByteArray()
    }

    /**
     * Compiles an IR module to x86-64 machine code using the CodeGenerator.
     * This is the mid-level API — you build IR, and the code generator handles
     * register allocation, instruction selection, and encoding.
     */
    @JvmStatic
    fun compileIrToMachineCode(): CompiledCode {
        val builder = IrBuilder("codegen_example", Target.x86_64())

        builder.createFunction(
            "multiply",
            listOf(Param("a", Type.I32), Param("b", Type.I32)),
            Type.I32
        )
        builder.appendBlock("entry")
        val product = builder.mul(builder.param(0), builder.param(1))
        builder.ret(product)
        builder.finalizeFunction()

        val module = builder.build()

        val codeGenerator = X86CodeGenerator()
        return codeGenerator.generateCode(module)
    }

    /**
     * Disassembles machine code bytes back into human-readable instructions.
     */
    @JvmStatic
    fun disassembleAndPrint(machineCode: ByteArray) {
        val disassembler = X86Disassembler()
        val instructions = disassembler.disassemble(machineCode, 0)

        for (instruction in instructions) {
            val text = if (instruction.operands.isNotEmpty()) {
                "${instruction.mnemonic} ${instruction.operands}"
            } else {
                instruction.mnemonic
            }
            println("  0x%04x: %s".format(instruction.address, text))
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=== Direct Assembly: add(a, b) ===")
        val addCode = assembleAddFunction()
        println("  ${addCode.size} bytes of machine code")
        disassembleAndPrint(addCode)

        println()
        println("=== Direct Assembly: sum(1..n) ===")
        val sumCode = assembleSumLoop()
        println("  ${sumCode.size} bytes of machine code")
        disassembleAndPrint(sumCode)

        println()
        println("=== IR CodeGen: multiply(a, b) ===")
        val compiled = compileIrToMachineCode()
        println("  ${compiled.textBytes.size} bytes of text section")
        println("  ${compiled.symbols.size} symbols defined")
        disassembleAndPrint(compiled.textBytes)
    }
}
