package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.build.sets.InstructionBuilder
import org.kgen.ir.build.sets.InstructionSet
import org.kgen.ir.instructions.Instruction

/**
 * High-level function builder with structured control flow. No SSA knowledge needed.
 *
 * FunctionBuilder owns per-function state (basic blocks, SSA counters, insertion point)
 * via [FunctionContext]. All instruction emission goes through the [instructions] proxy,
 * which is typed by the scope [T]. On [end], the function is finalized and registered
 * with the parent [ModuleBuilder].
 *
 * ```kotlin
 * val module = ModuleBuilder("example", Target.x86_64())
 * val fn = module.createFunction(NativeScope::class.java, "factorial",
 *     listOf(Param("n", Type.I32)), Type.I32)
 * val ins = fn.instructions
 * val result = ins.variable(Type.i32(1))
 * val i = ins.variable(Type.i32(1))
 * fn.whileLoop(
 *     condition = { ins.le(ins.get(i), fn.param(0)) },
 *     body = {
 *         ins.set(result, ins.mul(ins.get(result), ins.get(i)))
 *         ins.set(i, ins.add(ins.get(i), Type.i32(1)))
 *     },
 * )
 * fn.ret(ins.get(result))
 * fn.end()
 * ```
 *
 * @param T the scope type — determines which instructions and extensions are available
 */
class FunctionBuilder<T : InstructionSet> internal constructor(
    private val moduleBuilder: ModuleBuilder,
    internal val context: FunctionContext,
    scopeClass: Class<T>,
) : AutoCloseable {

    /**
     * The scope-typed instruction builder proxy. All scope-specific instructions
     * and extensions are accessed through this property.
     *
     * The type [T] determines what's visible at compile time — NativeScope shows
     * memory/bitwise/arithmetic ops, ManagedScope shows object ops, etc.
     *
     * ```java
     * NativeScope ins = fn.instructions();
     * Value sum = ins.add(a, b);
     * VarRef counter = ins.variable(Type.i32(0));
     * ```
     */
    val instructions: T = InstructionBuilder.create(scopeClass, context)

    /**
     * Java accessor for the typed instruction proxy.
     */
    fun instructions(): T = instructions

    @PublishedApi internal val emitter = object : InstructionEmitter() {
        override fun emit(instruction: Instruction) = context.emit(instruction)
        override fun nextRef(type: Type) = context.nextRef(type)
    }

    private var blockId = 0
    internal var terminated = false
    private var ended = false
    private val loopStack = ArrayDeque<LoopContext>()

    private data class LoopContext(val condBlock: BlockRef, val exitBlock: BlockRef)

    private fun nextLabel(prefix: String) = BlockRef("$prefix.${blockId++}")
    private fun switchToBlock(label: BlockRef) { context.appendBlock(label.label); terminated = false }

    /** Finalize this function. Must be called when you're done emitting code. */
    fun end() {
        if (!ended) {
            if (!terminated) { emitter.unreachable() }
            val irFunction = context.finalize()
            moduleBuilder.addFunction(irFunction)
            ended = true
        }
    }

    override fun close() {
        end()
    }

    // --- Parameters ---

    fun param(index: Int): Value = context.params[index]
    val paramCount: Int get() = context.params.size

    /**
     * Returns a [FunctionRef] for this function, usable as a call target.
     *
     * ```kotlin
     * val square = module.createFunction(NativeScope::class.java, "square", params, Type.I32)
     * // ... build square body ...
     * square.end()
     *
     * // Later, call it:
     * ins.call(square.ref(), listOf(arg), Type.I32)
     * ```
     */
    fun ref(): FunctionRef = FunctionRef(context.name, Type.Function(context.params.map { it.type }, context.returnType))

    // --- Select (ternary — useful in control flow patterns) ---

    fun select(condition: Value, ifTrue: Value, ifFalse: Value): Value = emitter.select(condition, ifTrue, ifFalse)

    // --- Structured control flow ---

    // Java-friendly overloads (Runnable/Supplier — no receiver type needed)

    /** Java-friendly ifThen — condition is a Value, body is a Runnable. */
    fun ifThen(condition: Value, body: Runnable) {
        ifThen(condition) { body.run() }
    }

    /** Java-friendly ifElse — condition is a Value, then/else are Runnables. */
    fun ifElse(condition: Value, thenBody: Runnable, elseBody: Runnable) {
        ifElse(condition, thenBody = { thenBody.run() }, elseBody = { elseBody.run() })
    }

    /** Java-friendly whileLoop. */
    fun whileLoop(condition: java.util.function.Supplier<Value>, body: Runnable) {
        whileLoop(condition = { condition.get() }, body = { body.run() })
    }

    /** Java-friendly doWhile. */
    fun doWhile(body: Runnable, condition: java.util.function.Supplier<Value>) {
        doWhile(body = { body.run() }, condition = { condition.get() })
    }

    /** Java-friendly forLoop. */
    fun forLoop(
        init: Runnable,
        condition: java.util.function.Supplier<Value>,
        update: Runnable,
        body: Runnable,
    ) {
        forLoop(
            init = { init.run() },
            condition = { condition.get() },
            update = { update.run() },
            body = { body.run() },
        )
    }

    // Kotlin receiver-lambda overloads

    fun ifThen(condition: Value, body: FunctionBuilder<T>.() -> Unit) {
        val thenBb = nextLabel("if.then")
        val mergeBb = nextLabel("if.merge")
        emitter.condBr(condition, thenBb, mergeBb)

        switchToBlock(thenBb)
        body()
        if (!terminated) { emitter.br(mergeBb) }

        switchToBlock(mergeBb)
    }

    fun ifElse(condition: Value, thenBody: FunctionBuilder<T>.() -> Unit, elseBody: FunctionBuilder<T>.() -> Unit) {
        val thenBb = nextLabel("if.then")
        val elseBb = nextLabel("if.else")
        val mergeBb = nextLabel("if.merge")
        emitter.condBr(condition, thenBb, elseBb)

        switchToBlock(thenBb)
        thenBody()
        val thenTerminated = terminated
        if (!thenTerminated) { emitter.br(mergeBb) }

        switchToBlock(elseBb)
        elseBody()
        val elseTerminated = terminated
        if (!elseTerminated) { emitter.br(mergeBb) }

        if (thenTerminated && elseTerminated) {
            terminated = true
        } else {
            switchToBlock(mergeBb)
        }
    }

    fun whileLoop(condition: FunctionBuilder<T>.() -> Value, body: FunctionBuilder<T>.() -> Unit) {
        val condBb = nextLabel("while.cond")
        val bodyBb = nextLabel("while.body")
        val exitBb = nextLabel("while.exit")

        emitter.br(condBb)
        switchToBlock(condBb)
        val cmp = condition()
        emitter.condBr(cmp, bodyBb, exitBb)

        loopStack.addLast(LoopContext(condBb, exitBb))
        switchToBlock(bodyBb)
        body()
        if (!terminated) { emitter.br(condBb) }
        loopStack.removeLast()

        switchToBlock(exitBb)
    }

    fun doWhile(body: FunctionBuilder<T>.() -> Unit, condition: FunctionBuilder<T>.() -> Value) {
        val bodyBb = nextLabel("do.body")
        val condBb = nextLabel("do.cond")
        val exitBb = nextLabel("do.exit")

        emitter.br(bodyBb)
        loopStack.addLast(LoopContext(condBb, exitBb))
        switchToBlock(bodyBb)
        body()
        if (!terminated) { emitter.br(condBb) }
        loopStack.removeLast()

        switchToBlock(condBb)
        val cmp = condition()
        emitter.condBr(cmp, bodyBb, exitBb)

        switchToBlock(exitBb)
    }

    fun forLoop(
        init: FunctionBuilder<T>.() -> Unit,
        condition: FunctionBuilder<T>.() -> Value,
        update: FunctionBuilder<T>.() -> Unit,
        body: FunctionBuilder<T>.() -> Unit,
    ) {
        init()
        val condBb = nextLabel("for.cond")
        val bodyBb = nextLabel("for.body")
        val updateBb = nextLabel("for.update")
        val exitBb = nextLabel("for.exit")

        emitter.br(condBb)
        switchToBlock(condBb)
        val cmp = condition()
        emitter.condBr(cmp, bodyBb, exitBb)

        loopStack.addLast(LoopContext(updateBb, exitBb))
        switchToBlock(bodyBb)
        body()
        if (!terminated) { emitter.br(updateBb) }
        loopStack.removeLast()

        switchToBlock(updateBb)
        update()
        emitter.br(condBb)

        switchToBlock(exitBb)
    }

    fun breakOut() {
        val loop = loopStack.lastOrNull() ?: error("breakOut() called outside of a loop")
        emitter.br(loop.exitBlock)
        terminated = true
    }

    fun continueOn() {
        val loop = loopStack.lastOrNull() ?: error("continueOn() called outside of a loop")
        emitter.br(loop.condBlock)
        terminated = true
    }

    // --- Return ---

    fun ret(value: Value) { emitter.ret(value); terminated = true }
    fun retVoid() { emitter.ret(); terminated = true }

    // --- Utility ---

    fun functionRef(name: String, paramTypes: List<Type>, returnType: Type): FunctionRef =
        FunctionRef(name, Type.Function(paramTypes, returnType))

    // --- Escape hatch ---

    /** Access the raw [InstructionEmitter] for low-level ops (phi, alloca, GEP, atomics, vectors, inline asm, etc.). */
    fun raw(): InstructionEmitter = emitter

    /** Execute a block with the raw [InstructionEmitter] as receiver. */
    inline fun raw(block: InstructionEmitter.() -> Unit) { emitter.block() }

}
