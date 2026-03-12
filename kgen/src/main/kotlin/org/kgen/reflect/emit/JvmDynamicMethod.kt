package org.kgen.reflect.emit

import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef
import org.kgen.target.jvm.AccessFlags
import org.kgen.target.jvm.ClassFileBuilder
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

/**
 * A dynamic method that compiles to JVM bytecode and is directly callable.
 *
 * ```java
 * var add = DynamicMethod.jvm("add",
 *     Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32));
 * add.jvmBody(code -> {
 *     code.iload(0);
 *     code.iload(1);
 *     code.iadd();
 *     code.ireturn();
 * });
 * int result = (int) add.invoke(3, 4);  // 7
 * ```
 */
class JvmDynamicMethod(
    name: String,
    signature: Signature,
) : DynamicMethod(name, signature) {

    private var bodyBlock: ((ClassFileBuilder.CodeEmitter) -> Unit)? = null
    private var loadedMethod: Method? = null

    /**
     * Define the method body using JVM bytecode instructions.
     */
    fun jvmBody(block: (ClassFileBuilder.CodeEmitter) -> Unit): JvmDynamicMethod {
        this.bodyBlock = block
        return this
    }

    /**
     * Call the method. Compiles and loads on first invocation, cached after that.
     */
    override fun invoke(vararg args: Any?): Any? {
        val method = ensureLoaded()
        return method.invoke(null, *args)
    }

    private fun ensureLoaded(): Method {
        loadedMethod?.let { return it }

        val descriptor = toJvmDescriptor(signature)
        val className = "org/kgen/generated/DynMethod_${counter.incrementAndGet()}"
        val javaName = className.replace('/', '.')

        val builder = ClassFileBuilder(className)
        builder.method(name, descriptor, AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            bodyBlock?.invoke(code) ?: throw IllegalStateException("No body defined for method '$name'")
        }
        val classBytes = builder.toBytes()

        val loader = object : ClassLoader(Thread.currentThread().contextClassLoader) {
            override fun findClass(n: String): Class<*> {
                if (n == javaName) return defineClass(n, classBytes, 0, classBytes.size)
                throw ClassNotFoundException(n)
            }
        }
        val cls = loader.loadClass(javaName)
        val paramTypes = signature.parameters().map { toJavaClass(it.type) }.toTypedArray()
        val method = cls.getMethod(name, *paramTypes)
        loadedMethod = method
        return method
    }

    companion object {
        private val counter = AtomicLong(0)

        internal fun toJvmDescriptor(sig: Signature): String {
            val sb = StringBuilder("(")
            for (p in sig.parameters()) {
                sb.append(toJvmType(p.type))
            }
            sb.append(")")
            sb.append(toJvmType(sig.returnType()))
            return sb.toString()
        }

        private fun toJvmType(ref: TypeRef): String = when (ref) {
            TypeRef.VOID -> "V"
            TypeRef.BOOL -> "Z"
            TypeRef.I8, TypeRef.U8 -> "B"
            TypeRef.I16, TypeRef.U16 -> "S"
            TypeRef.I32, TypeRef.U32 -> "I"
            TypeRef.I64, TypeRef.U64 -> "J"
            TypeRef.F32 -> "F"
            TypeRef.F64 -> "D"
            else -> "J" // default to long
        }

        private fun toJavaClass(ref: TypeRef): Class<*> = when (ref) {
            TypeRef.VOID -> Void.TYPE
            TypeRef.BOOL -> java.lang.Boolean.TYPE
            TypeRef.I8, TypeRef.U8 -> java.lang.Byte.TYPE
            TypeRef.I16, TypeRef.U16 -> java.lang.Short.TYPE
            TypeRef.I32, TypeRef.U32 -> java.lang.Integer.TYPE
            TypeRef.I64, TypeRef.U64 -> java.lang.Long.TYPE
            TypeRef.F32 -> java.lang.Float.TYPE
            TypeRef.F64 -> java.lang.Double.TYPE
            else -> java.lang.Long.TYPE
        }
    }
}
