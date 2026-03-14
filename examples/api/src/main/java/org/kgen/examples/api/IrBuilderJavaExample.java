package org.kgen.examples.api;

import org.kgen.ir.Constant;
import org.kgen.ir.FunctionRef;
import org.kgen.ir.GlobalRef;
import org.kgen.ir.ICmpPredicate;
import org.kgen.ir.Module;
import org.kgen.ir.Param;
import org.kgen.ir.BlockRef;
import org.kgen.ir.Type;
import org.kgen.ir.Value;
import org.kgen.ir.DefinedFunction;
import org.kgen.ir.build.IrBuilder;
import org.kgen.ir.build.sets.NativeScope;
import org.kgen.ir.target.Target;
import org.kgen.ir.text.IrPrinter;

import java.util.Collections;
import java.util.List;

/**
 * Java version of the IR builder examples.
 *
 * <p>Shows the separated API: {@link IrBuilder} for module structure,
 * {@link NativeScope} (via {@code createInstructionBuilder}) for instruction emission.
 * {@link DefinedFunction} provides parameter access and implements {@link Value}
 * for use in call instructions.</p>
 */
public final class IrBuilderJavaExample {

    /**
     * Builds a simple function that adds two i32 parameters and returns the result.
     */
    public static Module buildAddFunction() {
        IrBuilder ir = new IrBuilder("add_example", Target.x86_64());
        NativeScope b = ir.createInstructionBuilder(NativeScope.class);

        try (DefinedFunction fn = ir.defineFunction("add",
                List.of(Param.of("a", Type.I32.INSTANCE), Param.of("b", Type.I32.INSTANCE)),
                Type.I32.INSTANCE)) {
            ir.appendBlock("entry");
            Value sum = b.add(fn.param("a"), fn.param("b"));
            b.ret(sum);
        }

        return ir.build();
    }

    /**
     * Builds a function with control flow: returns the absolute value of an i32.
     * Demonstrates createBlock for forward references, condBr, phi, and neg.
     */
    public static Module buildAbsFunction() {
        IrBuilder ir = new IrBuilder("abs_example", Target.x86_64());
        NativeScope b = ir.createInstructionBuilder(NativeScope.class);

        try (DefinedFunction fn = ir.defineFunction("abs",
                List.of(Param.of("x", Type.I32.INSTANCE)),
                Type.I32.INSTANCE)) {

            BlockRef negative = ir.createBlock("negative");
            BlockRef positive = ir.createBlock("positive");
            BlockRef merge = ir.createBlock("merge");

            ir.appendBlock("entry");
            Value isNegative = b.icmp(ICmpPredicate.SLT, fn.param("x"), new Constant.I32(0));
            b.condBr(isNegative, negative, positive);

            ir.appendBlock(negative);
            Value negated = b.neg(fn.param("x"));
            b.br(merge);

            ir.appendBlock(positive);
            b.br(merge);

            ir.appendBlock(merge);
            Value result = b.phi(Type.I32.INSTANCE, List.of(
                    new kotlin.Pair<>(negated, negative),
                    new kotlin.Pair<>((Value) fn.param("x"), positive)
            ));
            b.ret(result);
        }

        return ir.build();
    }

    /**
     * Builds a module with a global variable and a function that loads, increments,
     * stores, and returns the new value.
     */
    public static Module buildGlobalVariableExample() {
        IrBuilder ir = new IrBuilder("globals_example", Target.x86_64());
        NativeScope b = ir.createInstructionBuilder(NativeScope.class);

        GlobalRef counter = ir.addGlobal("counter", Type.I32.INSTANCE, new Constant.I32(0));

        try (DefinedFunction fn = ir.defineFunction("increment", Collections.emptyList(), Type.I32.INSTANCE)) {
            ir.appendBlock("entry");
            Value current = b.load(Type.I32.INSTANCE, counter);
            Value incremented = b.add(current, new Constant.I32(1));
            b.store(incremented, counter);
            b.ret(incremented);
        }

        return ir.build();
    }

    /**
     * Builds a module with two functions where one calls the other.
     * Demonstrates DefinedFunction as a Value — pass it directly to call().
     */
    public static Module buildFunctionCallExample() {
        IrBuilder ir = new IrBuilder("call_example", Target.x86_64());
        NativeScope b = ir.createInstructionBuilder(NativeScope.class);

        // square(x) = x * x
        DefinedFunction square = ir.defineFunction("square",
                List.of(Param.of("x", Type.I32.INSTANCE)),
                Type.I32.INSTANCE);
        try (square) {
            ir.appendBlock("entry");
            Value squared = b.mul(square.param("x"), square.param("x"));
            b.ret(squared);
        }

        // sum_of_squares(a, b) = square(a) + square(b)
        try (DefinedFunction fn = ir.defineFunction("sum_of_squares",
                List.of(Param.of("a", Type.I32.INSTANCE), Param.of("b", Type.I32.INSTANCE)),
                Type.I32.INSTANCE)) {
            ir.appendBlock("entry");
            Value squareA = b.call(square, List.of(fn.param("a")), Type.I32.INSTANCE);
            Value squareB = b.call(square, List.of(fn.param("b")), Type.I32.INSTANCE);
            Value sum = b.add(squareA, squareB);
            b.ret(sum);
        }

        return ir.build();
    }

    /**
     * Builds a function that calls an external function (declared, not defined).
     */
    public static Module buildExternalCallExample() {
        IrBuilder ir = new IrBuilder("external_example", Target.x86_64());
        NativeScope b = ir.createInstructionBuilder(NativeScope.class);

        FunctionRef puts = ir.declareFunction("puts",
                List.of(Param.of("str", Type.OpaquePointer.INSTANCE)),
                Type.I32.INSTANCE);

        try (DefinedFunction fn = ir.defineFunction("main", Collections.emptyList(), Type.I32.INSTANCE)) {
            ir.appendBlock("entry");
            Value str = b.globalStringPtr("hello world");
            b.call(puts, List.of(str), Type.I32.INSTANCE);
            b.ret(new Constant.I32(0));
        }

        return ir.build();
    }

    public static void main(String[] args) {
        IrPrinter printer = new IrPrinter();

        System.out.println("=== Add Function (Java) ===");
        System.out.println(printer.print(buildAddFunction()));

        System.out.println("=== Abs Function (Java) ===");
        System.out.println(printer.print(buildAbsFunction()));

        System.out.println("=== Global Variable (Java) ===");
        System.out.println(printer.print(buildGlobalVariableExample()));

        System.out.println("=== Function Call (Java) ===");
        System.out.println(printer.print(buildFunctionCallExample()));

        System.out.println("=== External Call (Java) ===");
        System.out.println(printer.print(buildExternalCallExample()));
    }
}
