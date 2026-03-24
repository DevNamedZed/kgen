package org.kgen.examples.api;

import org.kgen.ir.Constant;
import org.kgen.ir.FunctionRef;
import org.kgen.ir.Linkage;
import org.kgen.ir.Module;
import org.kgen.ir.Param;
import org.kgen.ir.TargetProfile;
import org.kgen.ir.Type;
import org.kgen.ir.Value;
import org.kgen.ir.build.FunctionBuilder;
import org.kgen.ir.build.ModuleBuilder;
import org.kgen.ir.build.scope.NativeScope;
import org.kgen.ir.target.Target;
import org.kgen.ir.text.IrPrinter;

import java.util.Collections;
import java.util.List;

/**
 * Java version of the IR builder examples using the new scoped API.
 *
 * <p>ModuleBuilder → ClassBuilder&lt;T&gt; → FunctionBuilder&lt;T&gt; → instructions: T</p>
 *
 * <p>The scope type parameter (NativeScope, ManagedScope, etc.) determines which
 * instructions are available at compile time through the typed proxy.</p>
 */
public final class ModuleBuilderJavaExample {

    /**
     * Builds a simple add function using defineFunction with NativeScope.
     */
    public static Module buildAddFunction() {
        ModuleBuilder module = new ModuleBuilder("add_example", Target.x86_64());

        module.defineFunction(NativeScope.class, "add",
                List.of(Param.of("a", Type.I32.INSTANCE), Param.of("b", Type.I32.INSTANCE)),
                Type.I32.INSTANCE, fn -> {
                    NativeScope ins = fn.instructions();
                    fn.ret(ins.add(fn.param(0), fn.param(1), false, false));
                });

        return module.build();
    }

    /**
     * Builds an absolute value function using the select (ternary) instruction.
     */
    public static Module buildAbsFunction() {
        ModuleBuilder module = new ModuleBuilder("abs_example", Target.x86_64());

        module.defineFunction(NativeScope.class, "abs",
                List.of(Param.of("x", Type.I32.INSTANCE)), Type.I32.INSTANCE, fn -> {
                    NativeScope ins = fn.instructions();
                    Value isNegative = ins.lt(fn.param(0), new Constant.I32(0));
                    Value negated = ins.neg(fn.param(0));
                    Value result = fn.select(isNegative, negated, fn.param(0));
                    fn.ret(result);
                });

        return module.build();
    }

    /**
     * Builds a global variable example using defineFunction block-style.
     */
    public static Module buildGlobalVariableExample() {
        ModuleBuilder module = new ModuleBuilder("globals_example", Target.x86_64());

        var counter = module.addGlobal("counter", Type.I32.INSTANCE, new Constant.I32(0));

        module.defineFunction(NativeScope.class, "increment",
                Collections.emptyList(), Type.I32.INSTANCE, fn -> {
                    NativeScope ins = fn.instructions();
                    Value current = ins.load(counter, Type.I32.INSTANCE, null, false, null);
                    Value incremented = ins.add(current, new Constant.I32(1), false, false);
                    ins.store(incremented, counter, null, false, null);
                    fn.ret(incremented);
                });

        return module.build();
    }

    /**
     * Builds a class with methods using ClassBuilder.
     */
    public static Module buildClassExample() {
        ModuleBuilder module = new ModuleBuilder("class_example", Target.x86_64());

        module.defineClass(NativeScope.class, "Counter", cls -> {
            cls.field("value", Type.I32.INSTANCE);

            cls.defineStaticFunction("increment",
                    List.of(Param.of("self", Type.OpaquePointer.INSTANCE)),
                    Type.I32.INSTANCE, fn -> {
                        NativeScope ins = fn.instructions();
                        Value current = ins.load(fn.param(0), Type.I32.INSTANCE, null, false, null);
                        Value next = ins.add(current, new Constant.I32(1), false, false);
                        ins.store(next, fn.param(0), null, false, null);
                        fn.ret(next);
                    });
        });

        return module.build();
    }

    /**
     * Builds a loop using the Java-friendly whileLoop overload + typed NativeScope proxy.
     */
    public static Module buildLoopExample() {
        ModuleBuilder module = new ModuleBuilder("loop_example", Target.x86_64());

        module.defineFunction(NativeScope.class, "sum_to_n",
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

        return module.build();
    }

    /**
     * Demonstrates TargetProfile constructor.
     */
    public static Module buildWithProfile() {
        ModuleBuilder module = new ModuleBuilder("profile_example", TargetProfile.NATIVE);

        module.defineFunction(NativeScope.class, "identity",
                List.of(Param.of("x", Type.I32.INSTANCE)), Type.I32.INSTANCE, fn -> {
                    fn.ret(fn.param(0));
                });

        return module.build();
    }

    public static void main(String[] args) {
        IrPrinter printer = new IrPrinter();

        System.out.println("=== Add Function (Java) ===");
        System.out.println(printer.print(buildAddFunction()));

        System.out.println("=== Abs Function (Java) ===");
        System.out.println(printer.print(buildAbsFunction()));

        System.out.println("=== Global Variable (Java) ===");
        System.out.println(printer.print(buildGlobalVariableExample()));

        System.out.println("=== Class Example (Java) ===");
        System.out.println(printer.print(buildClassExample()));

        System.out.println("=== Loop Example (Java) ===");
        System.out.println(printer.print(buildLoopExample()));

        System.out.println("=== Profile Example (Java) ===");
        System.out.println(printer.print(buildWithProfile()));
    }
}
