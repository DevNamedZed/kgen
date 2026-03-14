package org.kgen.examples.api

import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.build.sets.NativeScope
import org.kgen.ir.target.Target
import org.kgen.ir.text.IrPrinter

/**
 * Demonstrates building IR modules using the IrBuilder + InstructionBuilder API.
 *
 * IrBuilder handles module structure: functions, blocks, globals, types.
 * InstructionBuilder handles instruction emission with compile-time scope constraints.
 */
object IrBuilderExample {

    /**
     * Builds a simple function that adds two i32 parameters and returns the result.
     *
     * ```
     * define i32 @add(i32 %a, i32 %b) {
     * entry:
     *   %0 = add i32 %a, %b
     *   ret i32 %0
     * }
     * ```
     */
    @JvmStatic
    fun buildAddFunction(): Module {
        val ir = IrBuilder("add_example", Target.x86_64())
        val b = ir.createInstructionBuilder<NativeScope>()

        ir.defineFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32).use { fn ->
            ir.appendBlock("entry")
            val sum = b.add(fn.param("a"), fn.param("b"))
            b.ret(sum)
        }

        return ir.build()
    }

    /**
     * Builds a function with control flow: returns the absolute value of an i32.
     *
     * ```
     * define i32 @abs(i32 %x) {
     * entry:
     *   %cmp = icmp slt i32 %x, 0
     *   condbr i1 %cmp, negative, positive
     * negative:
     *   %neg = neg i32 %x
     *   br merge
     * positive:
     *   br merge
     * merge:
     *   %result = phi i32 [%neg, negative], [%x, positive]
     *   ret i32 %result
     * }
     * ```
     */
    @JvmStatic
    fun buildAbsFunction(): Module {
        val ir = IrBuilder("abs_example", Target.x86_64())
        val b = ir.createInstructionBuilder<NativeScope>()

        ir.defineFunction("abs", listOf(Param("x", Type.I32)), Type.I32).use { fn ->
            val negative = ir.createBlock("negative")
            val positive = ir.createBlock("positive")
            val merge = ir.createBlock("merge")

            ir.appendBlock("entry")
            val isNegative = b.icmp(ICmpPredicate.SLT, fn.param("x"), Constant.I32(0))
            b.condBr(isNegative, negative, positive)

            ir.appendBlock(negative)
            val negated = b.neg(fn.param("x"))
            b.br(merge)

            ir.appendBlock(positive)
            b.br(merge)

            ir.appendBlock(merge)
            val result = b.phi(Type.I32, listOf(negated to negative, fn.param("x") to positive))
            b.ret(result)
        }

        return ir.build()
    }

    /**
     * Builds a module with a global variable and a function that reads/writes it.
     */
    @JvmStatic
    fun buildGlobalVariableExample(): Module {
        val ir = IrBuilder("globals_example", Target.x86_64())
        val b = ir.createInstructionBuilder<NativeScope>()

        val counter = ir.addGlobal("counter", Type.I32, Constant.I32(0))

        ir.defineFunction("increment", emptyList(), Type.I32).use {
            ir.appendBlock("entry")
            val current = b.load(Type.I32, counter)
            val incremented = b.add(current, Constant.I32(1))
            b.store(incremented, counter)
            b.ret(incremented)
        }

        return ir.build()
    }

    /**
     * Builds a module with multiple functions that call each other.
     * Demonstrates how DefinedFunction implements Value — pass it directly to call().
     */
    @JvmStatic
    fun buildFunctionCallExample(): Module {
        val ir = IrBuilder("call_example", Target.x86_64())
        val b = ir.createInstructionBuilder<NativeScope>()

        // square(x) = x * x
        val square = ir.defineFunction("square", listOf(Param("x", Type.I32)), Type.I32)
        square.use { fn ->
            ir.appendBlock("entry")
            val squared = b.mul(fn.param("x"), fn.param("x"))
            b.ret(squared)
        }

        // sum_of_squares(a, b) = square(a) + square(b)
        ir.defineFunction("sum_of_squares", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32).use { fn ->
            ir.appendBlock("entry")
            val squareA = b.call(square, listOf(fn.param("a")), Type.I32)
            val squareB = b.call(square, listOf(fn.param("b")), Type.I32)
            val sum = b.add(squareA!!, squareB!!)
            b.ret(sum)
        }

        return ir.build()
    }

    /**
     * Builds a function calling an external function (declared, not defined).
     */
    @JvmStatic
    fun buildExternalCallExample(): Module {
        val ir = IrBuilder("external_example", Target.x86_64())
        val b = ir.createInstructionBuilder<NativeScope>()

        val puts = ir.declareFunction("puts", listOf(Param("str", Type.OpaquePointer)), Type.I32)

        ir.defineFunction("main", emptyList(), Type.I32).use {
            ir.appendBlock("entry")
            val str = b.globalStringPtr("hello world")
            b.call(puts, listOf(str), Type.I32)
            b.ret(Constant.I32(0))
        }

        return ir.build()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=== Add Function ===")
        println(IrPrinter.print(buildAddFunction()))

        println("=== Abs Function ===")
        println(IrPrinter.print(buildAbsFunction()))

        println("=== Global Variable ===")
        println(IrPrinter.print(buildGlobalVariableExample()))

        println("=== Function Call ===")
        println(IrPrinter.print(buildFunctionCallExample()))

        println("=== External Call ===")
        println(IrPrinter.print(buildExternalCallExample()))
    }
}
