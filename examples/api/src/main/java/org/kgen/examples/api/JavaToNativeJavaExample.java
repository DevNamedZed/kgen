package org.kgen.examples.api;

import kotlin.Unit;
import org.kgen.codegen.CodeGenOptions;
import org.kgen.ir.Module;
import org.kgen.ir.target.Target;
import org.kgen.ir.text.IrPrinter;
import org.kgen.runtime.compile.NativeCompiler;
import org.kgen.runtime.compile.OutputPlatform;
import org.kgen.target.jvm.AccessFlags;
import org.kgen.target.jvm.ClassFileBuilder;

import java.util.List;

/**
 * Java version of the Java-to-native compilation example.
 *
 * <p>Demonstrates compiling JVM class files to native executables from Java.
 * The {@link NativeCompiler} lowers JVM bytecode through kgen's IR and produces
 * a standalone native executable.</p>
 */
public final class JavaToNativeJavaExample {

    /**
     * Builds a minimal Java class with static methods, entirely in memory.
     */
    public static byte[] buildCalculatorClass() {
        ClassFileBuilder builder = new ClassFileBuilder("com/example/Calculator");

        // static int compute(int a, int b) { return (a + b) * 2; }
        builder.method("compute", "(II)I", AccessFlags.PUBLIC | AccessFlags.STATIC, code -> {
            code.setMaxLocals(2);
            code.iload(0);
            code.iload(1);
            code.iadd();
            code.iconst(2);
            code.imul();
            code.ireturn();
            return Unit.INSTANCE;
        });

        // static void main(String[] args) { compute(3, 4); }
        builder.method("main", "([Ljava/lang/String;)V", AccessFlags.PUBLIC | AccessFlags.STATIC, code -> {
            code.setMaxLocals(1);
            code.iconst(3);
            code.iconst(4);
            code.invokestatic("com/example/Calculator", "compute", "(II)I");
            code.pop();
            code.return_();
            return Unit.INSTANCE;
        });

        return builder.toBytes();
    }

    /**
     * Compiles the calculator class to a native Linux ELF executable.
     */
    public static byte[] compileToNative(byte[] classBytes) {
        NativeCompiler compiler = new NativeCompiler(
            Target.x86_64(), OutputPlatform.LINUX, null, new CodeGenOptions()
        );
        return compiler.compile(List.of(classBytes), "com/example/Calculator");
    }

    /**
     * Shows the intermediate IR that the NativeCompiler produces from class files.
     */
    public static void showIntermediateIr(byte[] classBytes) {
        NativeCompiler compiler = new NativeCompiler(
            Target.x86_64(), OutputPlatform.LINUX, null, new CodeGenOptions()
        );
        List<Module> modules = compiler.compileToModules(List.of(classBytes));
        IrPrinter printer = new IrPrinter();
        for (Module module : modules) {
            System.out.println(printer.print(module));
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Building Calculator.class (Java) ===");
        byte[] classBytes = buildCalculatorClass();
        System.out.printf("  Class file: %d bytes%n", classBytes.length);

        System.out.println();
        System.out.println("=== Intermediate IR (Java) ===");
        showIntermediateIr(classBytes);

        System.out.println();
        System.out.println("=== Compiling to native ELF executable (Java) ===");
        byte[] executable = compileToNative(classBytes);
        System.out.printf("  Executable: %d bytes%n", executable.length);
    }
}
