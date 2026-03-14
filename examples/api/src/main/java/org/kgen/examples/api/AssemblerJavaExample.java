package org.kgen.examples.api;

import org.kgen.codegen.CompiledCode;
import org.kgen.codegen.DisassembledInstruction;
import org.kgen.ir.Module;
import org.kgen.ir.Param;
import org.kgen.ir.Type;
import org.kgen.ir.Value;
import org.kgen.ir.build.IrBuilder;
import org.kgen.ir.target.Target;
import org.kgen.target.x86.X86Memory;
import org.kgen.target.x86.X86Register;
import org.kgen.target.x86.asm.X86Assembler;
import org.kgen.target.x86.codegen.X86CodeGenerator;
import org.kgen.target.x86.disasm.X86Disassembler;

import java.util.List;

/**
 * Java version of the assembler and codegen examples.
 *
 * <p>Demonstrates the three-layer code generation API from Java:
 * assembler (raw emit), code generator (IR to machine code),
 * and disassembler (machine code to instructions).</p>
 */
public final class AssemblerJavaExample {

    /**
     * Uses the x86-64 assembler directly to emit a function that adds two integers.
     *
     * <pre>{@code
     * add_ints:
     *   lea eax, [rdi + rsi]
     *   ret
     * }</pre>
     */
    public static byte[] assembleAddFunction() {
        X86Assembler assembler = new X86Assembler();

        assembler.lea(X86Register.EAX,
            X86Memory.base(X86Register.RDI).index(X86Register.RSI, 1).build());
        assembler.ret();

        return assembler.toByteArray();
    }

    /**
     * Uses the x86-64 assembler to emit a loop that sums integers from 1 to N.
     *
     * <pre>{@code
     * sum_to_n:
     *   xor eax, eax
     *   mov ecx, edi
     * .loop:
     *   add eax, ecx
     *   dec ecx
     *   jnz .loop
     *   ret
     * }</pre>
     */
    public static byte[] assembleSumLoop() {
        X86Assembler assembler = new X86Assembler();

        assembler.xor_(X86Register.EAX, X86Register.EAX);
        assembler.mov(X86Register.ECX, X86Register.EDI);

        assembler.label("loop");
        assembler.add(X86Register.EAX, X86Register.ECX);
        assembler.dec(X86Register.ECX);
        assembler.jccLabel(0x05, "loop"); // JNZ

        assembler.ret();

        return assembler.toByteArray();
    }

    /**
     * Compiles an IR module to x86-64 machine code using the CodeGenerator.
     */
    public static CompiledCode compileIrToMachineCode() {
        IrBuilder builder = new IrBuilder("codegen_example", Target.x86_64());

        builder.createFunction(
            "multiply",
            List.of(Param.of("a", Type.I32.INSTANCE), Param.of("b", Type.I32.INSTANCE)),
            Type.I32.INSTANCE
        );
        builder.appendBlock("entry");
        Value product = builder.mul(builder.param(0), builder.param(1));
        builder.ret(product);
        builder.finalizeFunction();

        Module module = builder.build();

        X86CodeGenerator codeGenerator = new X86CodeGenerator();
        return codeGenerator.generateCode(module);
    }

    /**
     * Disassembles machine code bytes and prints each instruction.
     */
    public static void disassembleAndPrint(byte[] machineCode) {
        X86Disassembler disassembler = new X86Disassembler();
        var instructions = disassembler.disassemble(machineCode, 0);

        for (var instruction : instructions) {
            String text = instruction.getMnemonic();
            if (!instruction.getOperands().isEmpty()) {
                text += " " + instruction.getOperands();
            }
            System.out.printf("  0x%04x: %s%n", instruction.getAddress(), text);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Direct Assembly: add(a, b) [Java] ===");
        byte[] addCode = assembleAddFunction();
        System.out.printf("  %d bytes of machine code%n", addCode.length);
        disassembleAndPrint(addCode);

        System.out.println();
        System.out.println("=== Direct Assembly: sum(1..n) [Java] ===");
        byte[] sumCode = assembleSumLoop();
        System.out.printf("  %d bytes of machine code%n", sumCode.length);
        disassembleAndPrint(sumCode);

        System.out.println();
        System.out.println("=== IR CodeGen: multiply(a, b) [Java] ===");
        CompiledCode compiled = compileIrToMachineCode();
        System.out.printf("  %d bytes of text section%n", compiled.getTextBytes().length);
        System.out.printf("  %d symbols defined%n", compiled.getSymbols().size());
        disassembleAndPrint(compiled.getTextBytes());
    }
}
