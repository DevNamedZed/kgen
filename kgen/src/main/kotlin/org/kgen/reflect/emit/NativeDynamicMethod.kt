package org.kgen.reflect.emit

import org.kgen.ir.Parameter
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.reflect.NativeCode
import org.kgen.reflect.Signature

/**
 * A dynamic method that compiles to native machine code and is directly callable.
 *
 * ```java
 * var method = DynamicMethod.native_("multiply",
 *     Signature.of(TypeRef.I64, TypeRef.I64, TypeRef.I64));
 * method.body((ir, params) -> {
 *     ir.ret(ir.mul(params.get(0), params.get(1)));
 * });
 * long result = (long) method.invoke(6, 7);  // 42
 * method.close();  // free executable memory
 * ```
 */
class NativeDynamicMethod(
    name: String,
    signature: Signature,
    private val target: Target,
) : DynamicMethod(name, signature) {

    private var bodyBlock: ((IrBuilder, List<Parameter>) -> Unit)? = null
    private var compiled: NativeCode? = null

    /**
     * Define the method body using IR instructions.
     * The lambda receives the IR builder positioned at the entry block
     * and the list of function parameters.
     */
    fun body(block: (IrBuilder, List<Parameter>) -> Unit): NativeDynamicMethod {
        this.bodyBlock = block
        return this
    }

    /**
     * Call the method. Compiles on first invocation, cached after that.
     * Arguments and return value are longs (pointers and integers).
     */
    override fun invoke(vararg args: Any?): Any? {
        val code = ensureCompiled()
        val longArgs = LongArray(args.size) { (args[it] as Number).toLong() }
        return code.call(name, *longArgs)
    }

    /** Call returning long explicitly. */
    fun invokeLong(vararg args: Long): Long {
        return ensureCompiled().call(name, *args)
    }

    /** Call returning int explicitly. */
    fun invokeInt(vararg args: Long): Int {
        return ensureCompiled().callInt(name, *args)
    }

    /** Call returning void. */
    fun invokeVoid(vararg args: Long) {
        ensureCompiled().callVoid(name, *args)
    }

    override fun close() {
        compiled?.close()
        compiled = null
    }

    private fun ensureCompiled(): NativeCode {
        compiled?.let { return it }
        val code = NativeCodeBuilder.forTarget(target)
            .function(name, signature)
            .let { fb ->
                val block = bodyBlock
                if (block != null) fb.body(block) else fb.returnDefault()
            }
            .compile()
        compiled = code
        return code
    }
}
