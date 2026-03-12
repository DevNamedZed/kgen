package org.kgen.reflect.emit

import org.kgen.target.jvm.ClassFileBuilder
import java.lang.reflect.Method

/**
 * Module builder that produces JVM class files.
 *
 * ```java
 * JvmModuleBuilder mod = ModuleBuilder.jvm("com/example/Calculator");
 * mod.classBuilder().method("add", "(II)I",
 *     AccessFlags.PUBLIC | AccessFlags.STATIC, code -> {
 *         code.iload(0);
 *         code.iload(1);
 *         code.iadd();
 *         code.ireturn();
 *     });
 *
 * // Load and call immediately
 * Class<?> cls = mod.load();
 * int result = (int) cls.getMethod("add", int.class, int.class).invoke(null, 3, 4);
 *
 * // Or use the convenience method
 * Object result = mod.invoke("add", 3, 4);
 * ```
 */
class JvmModuleBuilder(name: String) : ModuleBuilder(name) {

    private val builder = ClassFileBuilder(name)
    private var loadedClass: Class<*>? = null

    /** Access the underlying class file builder for method/field definitions. */
    fun classBuilder(): ClassFileBuilder = builder

    override fun toBytes(): ByteArray = builder.toBytes()

    /**
     * Compile and load the class into the JVM, returning the loaded [Class].
     * The class can be used with standard reflection to invoke methods.
     *
     * The class is cached — subsequent calls return the same instance.
     */
    @JvmOverloads
    fun load(parentClassLoader: ClassLoader = Thread.currentThread().contextClassLoader): Class<*> {
        loadedClass?.let { return it }
        val bytes = toBytes()
        val javaName = name.replace('/', '.')
        val loader = ByteArrayClassLoader(parentClassLoader, javaName, bytes)
        val cls = loader.loadClass(javaName)
        loadedClass = cls
        return cls
    }

    /**
     * Load the class and invoke a static method by name.
     * Arguments are matched to the first method with the given name.
     */
    fun invoke(methodName: String, vararg args: Any?): Any? {
        val cls = load()
        val method = findMethod(cls, methodName, args)
        return method.invoke(null, *args)
    }

    private fun findMethod(cls: Class<*>, name: String, args: Array<out Any?>): Method {
        val candidates = cls.declaredMethods.filter { it.name == name }
        if (candidates.size == 1) return candidates[0]
        return candidates.firstOrNull { it.parameterCount == args.size }
            ?: throw NoSuchMethodException("$name with ${args.size} args in ${cls.name}")
    }

    private class ByteArrayClassLoader(
        parent: ClassLoader,
        private val className: String,
        private val classBytes: ByteArray,
    ) : ClassLoader(parent) {
        override fun findClass(name: String): Class<*> {
            if (name == className) {
                return defineClass(name, classBytes, 0, classBytes.size)
            }
            throw ClassNotFoundException(name)
        }
    }
}
