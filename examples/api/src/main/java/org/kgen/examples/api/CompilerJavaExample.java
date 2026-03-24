package org.kgen.examples.api;

import org.kgen.codegen.CodeGenOptions;
import org.kgen.codegen.OptLevel;
import org.kgen.codegen.OutputFormat;
import org.kgen.compile.Compiler;
import org.kgen.ir.Constant;
import org.kgen.ir.Module;
import org.kgen.ir.Param;
import org.kgen.ir.TargetProfile;
import org.kgen.ir.Type;
import org.kgen.ir.build.ModuleBuilder;
import org.kgen.ir.build.scope.NativeScope;
import org.kgen.ir.target.Target;
import org.kgen.ir.types.Operator;

import java.util.List;

/**
 * Java version of the Compiler examples.
 *
 * <p>Shows how to use the Compiler API from Java — the top-level orchestrator
 * for producing complete output files.</p>
 */
public final class CompilerJavaExample {

    /**
     * Single module with a function, compiled to object file.
     */
    public static byte[] singleFunction() {
        var compiler = new Compiler("simple");

        compiler.defineModule("main", TargetProfile.NATIVE, module -> {
            module.defineFunction(NativeScope.class, "add",
                    List.of(Param.of("a", Type.I32.INSTANCE), Param.of("b", Type.I32.INSTANCE)),
                    Type.I32.INSTANCE, fn -> {
                        NativeScope ins = fn.instructions();
                        fn.ret(ins.add(fn.param(0), fn.param(1), false, false));
                    });
        });

        return compiler.compile(Target.x86_64(), OptLevel.O0,
                new CodeGenOptions(null, OptLevel.O0, false,
                        org.kgen.codegen.PICMode.STATIC, org.kgen.codegen.RelocationModel.STATIC,
                        OutputFormat.OBJECT, null, null));
    }

    /**
     * Class with methods and operators.
     */
    public static byte[] classWithMethods() {
        var compiler = new Compiler("math");

        compiler.defineModule("math", TargetProfile.NATIVE, module -> {
            module.defineClass(NativeScope.class, "Calculator", cls -> {
                cls.field("value", Type.I32.INSTANCE);

                cls.defineStaticFunction("add",
                        List.of(Param.of("a", Type.I32.INSTANCE), Param.of("b", Type.I32.INSTANCE)),
                        Type.I32.INSTANCE, fn -> {
                            NativeScope ins = fn.instructions();
                            fn.ret(ins.add(fn.param(0), fn.param(1), false, false));
                        });

                cls.defineStaticFunction("negate",
                        List.of(Param.of("x", Type.I32.INSTANCE)),
                        Type.I32.INSTANCE, fn -> {
                            NativeScope ins = fn.instructions();
                            fn.ret(ins.neg(fn.param(0)));
                        });
            });
        });

        return compiler.compile(Target.x86_64(), OptLevel.O2,
                new CodeGenOptions(null, OptLevel.O2, false,
                        org.kgen.codegen.PICMode.STATIC, org.kgen.codegen.RelocationModel.STATIC,
                        OutputFormat.OBJECT, null, null));
    }

    /**
     * Loop using the Java-friendly whileLoop overload.
     */
    public static byte[] loopExample() {
        var compiler = new Compiler("loop");

        compiler.defineModule("main", TargetProfile.NATIVE, module -> {
            module.defineFunction(NativeScope.class, "sumToN",
                    List.of(Param.of("n", Type.I32.INSTANCE)), Type.I32.INSTANCE, fn -> {
                        NativeScope ins = fn.instructions();

                        var sum = ins.variable(new Constant.I32(0));
                        var i = ins.variable(new Constant.I32(1));

                        fn.whileLoop(
                                () -> ins.le(ins.get(i), fn.param(0)),
                                () -> {
                                    ins.set(sum, ins.add(ins.get(sum), ins.get(i), false, false));
                                    ins.set(i, ins.add(ins.get(i), new Constant.I32(1), false, false));
                                });

                        fn.ret(ins.get(sum));
                    });
        });

        return compiler.compile(Target.x86_64(), OptLevel.O0,
                new CodeGenOptions(null, OptLevel.O0, false,
                        org.kgen.codegen.PICMode.STATIC, org.kgen.codegen.RelocationModel.STATIC,
                        OutputFormat.OBJECT, null, null));
    }

    /**
     * Multiple modules with resources.
     */
    public static byte[] multiModuleWithResources() {
        var compiler = new Compiler("app");

        compiler.defineModule("math", TargetProfile.NATIVE, module -> {
            module.defineFunction(NativeScope.class, "square",
                    List.of(Param.of("x", Type.I32.INSTANCE)), Type.I32.INSTANCE, fn -> {
                        NativeScope ins = fn.instructions();
                        fn.ret(ins.mul(fn.param(0), fn.param(0), false, false));
                    });
        });

        compiler.defineModule("main", TargetProfile.NATIVE, module -> {
            module.defineFunction(NativeScope.class, "main",
                    List.of(), Type.I32.INSTANCE, fn -> {
                        fn.ret(new Constant.I32(0));
                    });
        });

        compiler.addResource("version.txt", "1.0.0".getBytes());
        compiler.setEntryPoint("main");

        return compiler.compile(Target.x86_64());
    }

    public static void main(String[] args) {
        System.out.println("=== Single Function (Java) ===");
        System.out.printf("  %d bytes%n", singleFunction().length);

        System.out.println("=== Class With Methods (Java) ===");
        System.out.printf("  %d bytes%n", classWithMethods().length);

        System.out.println("=== Loop Example (Java) ===");
        System.out.printf("  %d bytes%n", loopExample().length);

        System.out.println("=== Multi-Module With Resources (Java) ===");
        System.out.printf("  %d bytes%n", multiModuleWithResources().length);
    }
}
