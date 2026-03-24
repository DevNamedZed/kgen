package org.kgen.examples.api;

import org.kgen.codegen.CompiledCode;
import org.kgen.ir.Module;
import org.kgen.ir.Param;
import org.kgen.ir.Type;
import org.kgen.ir.build.ModuleBuilder;
import org.kgen.ir.build.scope.NativeScope;
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
 * <p>Java can't use the Kotlin DSL block syntax ({@code x86 { ... }}), but the
 * typed label API ({@code label()}, {@code mark()}) works from Java.</p>
 */
public final class AssemblerJavaExample {

    /**
     * Uses the x86-64 assembler with typed labels.
     */
    public static byte[] assembleAddFunction() {
        X86Assembler asm = new X86Assembler();

        asm.lea(X86Register.EAX,
            X86Memory.base(X86Register.RDI).index(X86Register.RSI, 1).build());
        asm.ret();

        return asm.toByteArray();
    }

    /**
     * Uses typed labels for the loop — mark() for backward, label() for forward.
     */
    public static byte[] assembleSumLoop() {
        X86Assembler asm = new X86Assembler();

        asm.xor_(X86Register.EAX, X86Register.EAX);
        asm.mov(X86Register.ECX, X86Register.EDI);

        X86Assembler.Label loop = asm.mark();
        asm.add(X86Register.EAX, X86Register.ECX);
        asm.dec(X86Register.ECX);
        asm.jnz(loop);

        asm.ret();

        return asm.toByteArray();
    }

    /**
     * Forward reference — allocate label, bind later.
     */
    public static byte[] assembleForwardJump() {
        X86Assembler asm = new X86Assembler();

        X86Assembler.Label skip = asm.label();
        asm.test(X86Register.ESI, X86Register.ESI);
        asm.jz(skip);
        asm.add(X86Register.EAX, X86Register.ESI);
        asm.mark(skip);
        asm.ret();

        return asm.toByteArray();
    }

    /**
     * Compiles IR to x86 machine code using the new scoped API.
     */
    public static CompiledCode compileIrToMachineCode() {
        ModuleBuilder module = new ModuleBuilder("codegen_example", Target.x86_64());

        module.defineFunction(NativeScope.class, "multiply",
                List.of(Param.of("a", Type.I32.INSTANCE), Param.of("b", Type.I32.INSTANCE)),
                Type.I32.INSTANCE, fn -> {
                    NativeScope ins = fn.instructions();
                    fn.ret(ins.mul(fn.param(0), fn.param(1), false, false));
                });

        return new X86CodeGenerator().generateCode(module.build());
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
        System.out.println("=== Direct Assembly: forward jump [Java] ===");
        byte[] fwdCode = assembleForwardJump();
        System.out.printf("  %d bytes of machine code%n", fwdCode.length);
        disassembleAndPrint(fwdCode);

        System.out.println();
        System.out.println("=== IR CodeGen: multiply(a, b) [Java] ===");
        CompiledCode compiled = compileIrToMachineCode();
        System.out.printf("  %d bytes of text section%n", compiled.getTextBytes().length);
        System.out.printf("  %d symbols defined%n", compiled.getSymbols().size());
        disassembleAndPrint(compiled.getTextBytes());
    }
}
