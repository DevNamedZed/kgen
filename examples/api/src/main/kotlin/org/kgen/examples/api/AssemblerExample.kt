package org.kgen.examples.api

import org.kgen.codegen.CompiledCode
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.build.scope.NativeScope
import org.kgen.ir.target.Target
import org.kgen.target.x86.X86Memory
import org.kgen.target.x86.X86Register
import org.kgen.target.x86.asm.X86Assembler
import org.kgen.target.x86.asm.x86
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.x86.disasm.X86Disassembler

/**
 * Demonstrates the three-layer code generation API:
 *
 * 1. **Assembler** — emit raw machine code instructions (DSL block syntax)
 * 2. **CodeGenerator** — compile IR modules to machine code
 * 3. **Disassembler** — decode machine code back to instructions
 */
object AssemblerExample {

    /**
     * Uses the x86-64 assembler DSL to emit a function that adds two integers.
     * No `assembler.` prefix — instructions are called directly inside the block.
     *
     * ```asm
     * add_ints:
     *   lea eax, [rdi + rsi]    ; result = first + second (System V ABI)
     *   ret
     * ```
     */
    @JvmStatic
    fun assembleAddFunction(): ByteArray = x86 {
        lea(X86Register.EAX, X86Memory.base(X86Register.RDI).index(X86Register.RSI).build())
        ret()
    }

    /**
     * Uses the x86-64 assembler DSL with typed labels for a loop.
     * `mark()` creates a backward label, `label()` creates a forward reference.
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
    fun assembleSumLoop(): ByteArray = x86 {
        xor_(X86Register.EAX, X86Register.EAX)
        mov(X86Register.ECX, X86Register.EDI)

        val loop = mark()
        add(X86Register.EAX, X86Register.ECX)
        dec(X86Register.ECX)
        jnz(loop)

        ret()
    }

    /**
     * Forward reference example — jump over a section of code.
     *
     * ```asm
     *   test esi, esi
     *   jz .skip               ; forward jump — label allocated, bound later
     *   add eax, esi
     * .skip:
     *   ret
     * ```
     */
    @JvmStatic
    fun assembleForwardJump(): ByteArray = x86 {
        val skip = label()
        test(X86Register.ESI, X86Register.ESI)
        jz(skip)
        add(X86Register.EAX, X86Register.ESI)
        mark(skip)
        ret()
    }

    /**
     * Compiles an IR module to x86-64 machine code using the CodeGenerator.
     * Uses the new scoped API with FunctionBuilder + typed proxy.
     */
    @JvmStatic
    fun compileIrToMachineCode(): CompiledCode {
        val module = ModuleBuilder("codegen_example", Target.x86_64())

        module.defineFunction(NativeScope::class.java, "multiply",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
            val ins = fn.instructions
            fn.ret(ins.mul(fn.param(0), fn.param(1)))
        }

        return X86CodeGenerator().generateCode(module.build())
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
        println("=== Direct Assembly: forward jump ===")
        val fwdCode = assembleForwardJump()
        println("  ${fwdCode.size} bytes of machine code")
        disassembleAndPrint(fwdCode)

        println()
        println("=== IR CodeGen: multiply(a, b) ===")
        val compiled = compileIrToMachineCode()
        println("  ${compiled.textBytes.size} bytes of text section")
        println("  ${compiled.symbols.size} symbols defined")
        disassembleAndPrint(compiled.textBytes)
    }
}
