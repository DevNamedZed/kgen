package org.kgen.examples.api

import org.kgen.ir.Module
import org.kgen.ir.target.Target
import org.kgen.ir.text.IrPrinter
import org.kgen.runtime.compile.NativeCompiler
import org.kgen.runtime.compile.OutputPlatform
import org.kgen.target.jvm.AccessFlags
import org.kgen.target.jvm.ClassFileBuilder

/**
 * Demonstrates compiling JVM class files to native executables.
 *
 * The NativeCompiler takes JVM .class files as input, lowers the bytecode
 * through kgen's IR, and produces a standalone native executable (ELF, PE, or Mach-O)
 * with no JVM dependency at runtime.
 *
 * Pipeline: .class bytes -> bytecode lowering -> IR -> optimization passes ->
 * code generation -> linking -> executable
 */
object JavaToNativeExample {

    /**
     * Builds a minimal Java class with static methods, entirely in memory.
     * No .java source files or javac needed.
     */
    @JvmStatic
    fun buildCalculatorClass(): ByteArray {
        val builder = ClassFileBuilder("com/example/Calculator")

        // static int compute(int a, int b) { return (a + b) * 2; }
        builder.method("compute", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.maxLocals = 2
            code.iload(0)
            code.iload(1)
            code.iadd()
            code.iconst(2)
            code.imul()
            code.ireturn()
        }

        // static void main(String[] args) { compute(3, 4); }
        builder.method("main", "([Ljava/lang/String;)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.maxLocals = 1
            code.iconst(3)
            code.iconst(4)
            code.invokestatic("com/example/Calculator", "compute", "(II)I")
            code.pop()
            code.return_()
        }

        return builder.toBytes()
    }

    /**
     * Compiles the calculator class to a native Linux ELF executable.
     */
    @JvmStatic
    fun compileToNative(classBytes: ByteArray): ByteArray {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        return compiler.compile(listOf(classBytes), "com/example/Calculator")
    }

    /**
     * Shows the intermediate IR that the NativeCompiler produces from class files.
     * This is useful for understanding what the bytecode lowering does.
     */
    @JvmStatic
    fun showIntermediateIr(classBytes: ByteArray): List<Module> {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        return compiler.compileToModules(listOf(classBytes))
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=== Building Calculator.class ===")
        val classBytes = buildCalculatorClass()
        println("  Class file: ${classBytes.size} bytes")

        println()
        println("=== Intermediate IR ===")
        val modules = showIntermediateIr(classBytes)
        for (module in modules) {
            println(IrPrinter.print(module))
        }

        println()
        println("=== Compiling to native ELF executable ===")
        val executable = compileToNative(classBytes)
        println("  Executable: ${executable.size} bytes")
    }
}
