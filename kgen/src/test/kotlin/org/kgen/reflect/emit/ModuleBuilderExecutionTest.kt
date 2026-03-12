package org.kgen.reflect.emit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.Constant
import org.kgen.ir.Param
import org.kgen.ir.Type
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef
import org.kgen.target.jvm.AccessFlags

class ModuleBuilderExecutionTest {

    // --- JvmModuleBuilder: load() and invoke() ---

    @Test
    fun jvmModuleBuilderLoadReturnsClass() {
        val mod = ModuleBuilder.jvm("org/kgen/test/Generated")
        mod.classBuilder().method("answer", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.iconst(42)
            code.ireturn()
        }
        val cls = mod.load()
        assertEquals("org.kgen.test.Generated", cls.name)
    }

    @Test
    fun jvmModuleBuilderInvoke() {
        val mod = ModuleBuilder.jvm("org/kgen/test/Calculator")
        mod.classBuilder().method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.iload(0)
            code.iload(1)
            code.iadd()
            code.ireturn()
        }
        assertEquals(7, mod.invoke("add", 3, 4))
    }

    @Test
    fun jvmModuleBuilderFibonacci() {
        val mod = ModuleBuilder.jvm("org/kgen/test/Fibonacci")
        mod.classBuilder().method("fib", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.iload(0)
            code.iconst(2)
            code.ifIcmpge("recurse")
            code.iload(0)
            code.ireturn()
            code.label("recurse")
            code.iload(0)
            code.iconst(1)
            code.isub()
            code.invokestatic("org/kgen/test/Fibonacci", "fib", "(I)I")
            code.iload(0)
            code.iconst(2)
            code.isub()
            code.invokestatic("org/kgen/test/Fibonacci", "fib", "(I)I")
            code.iadd()
            code.ireturn()
        }
        assertEquals(55, mod.invoke("fib", 10))
    }

    @Test
    fun jvmModuleBuilderMultipleMethods() {
        val mod = ModuleBuilder.jvm("org/kgen/test/Multi")
        mod.classBuilder().method("square", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.iload(0)
            code.iload(0)
            code.imul()
            code.ireturn()
        }
        mod.classBuilder().method("negate", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.iload(0)
            code.ineg()
            code.ireturn()
        }
        assertEquals(25, mod.invoke("square", 5))
        assertEquals(-7, mod.invoke("negate", 7))
    }

    // --- JvmDynamicMethod: define method, invoke directly ---

    @Test
    fun jvmDynamicMethodInvoke() {
        val add = DynamicMethod.jvm("add", Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32))
        add.jvmBody { code ->
            code.iload(0)
            code.iload(1)
            code.iadd()
            code.ireturn()
        }
        assertEquals(7, add.invoke(3, 4))
    }

    @Test
    fun jvmDynamicMethodNoArgs() {
        val method = DynamicMethod.jvm("answer", Signature.of(TypeRef.I32))
        method.jvmBody { code ->
            code.iconst(42)
            code.ireturn()
        }
        assertEquals(42, method.invoke())
    }

    @Test
    fun jvmDynamicMethodLong() {
        val method = DynamicMethod.jvm("doubleIt", Signature.of(TypeRef.I64, TypeRef.I64))
        method.jvmBody { code ->
            code.lload(0)
            code.lload(0)
            code.ladd()
            code.lreturn()
        }
        assertEquals(84L, method.invoke(42L))
    }

    @Test
    fun jvmDynamicMethodCached() {
        val method = DynamicMethod.jvm("get", Signature.of(TypeRef.I32))
        method.jvmBody { code ->
            code.iconst(99)
            code.ireturn()
        }
        assertEquals(99, method.invoke())
        assertEquals(99, method.invoke())
    }

    // --- NativeModuleBuilder: compile() ---

    @EnabledOnOs(OS.WINDOWS, OS.LINUX)
    @Test
    fun nativeModuleBuilderCompileAndCall() {
        val mod = ModuleBuilder.native_("testmod")
        val ir = mod.irBuilder()
        ir.createFunction("answer", emptyList(), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I64(42))
        ir.finalizeFunction()

        mod.compile().use { code ->
            assertEquals(42L, code.call("answer"))
        }
    }

    @EnabledOnOs(OS.WINDOWS, OS.LINUX)
    @Test
    fun nativeModuleBuilderCompileWithParams() {
        val mod = ModuleBuilder.native_("testmod")
        val ir = mod.irBuilder()
        val params = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.add(params[0], params[1]))
        ir.finalizeFunction()

        mod.compile().use { code ->
            assertEquals(7L, code.call("add", 3, 4))
        }
    }

    @Test
    fun nativeModuleBuilderToBytesReturnsRawMachineCode() {
        val mod = ModuleBuilder.native_("testmod")
        val ir = mod.irBuilder()
        ir.createFunction("noop", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret()
        ir.finalizeFunction()
        assertTrue(mod.toBytes().isNotEmpty())
    }

    // --- NativeDynamicMethod: define method, invoke directly ---

    @EnabledOnOs(OS.WINDOWS, OS.LINUX)
    @Test
    fun nativeDynamicMethodInvoke() {
        val add = DynamicMethod.native_("add", Signature.of(TypeRef.I64, TypeRef.I64, TypeRef.I64))
        add.body { ir, params ->
            ir.ret(ir.add(params[0], params[1]))
        }
        add.use {
            assertEquals(7L, it.invoke(3L, 4L))
            assertEquals(100L, it.invoke(60L, 40L))
        }
    }

    @EnabledOnOs(OS.WINDOWS, OS.LINUX)
    @Test
    fun nativeDynamicMethodInvokeLong() {
        val method = DynamicMethod.native_("double", Signature.of(TypeRef.I64, TypeRef.I64))
        method.body { ir, params ->
            ir.ret(ir.add(params[0], params[0]))
        }
        method.use {
            assertEquals(10L, it.invokeLong(5))
            assertEquals(42L, it.invokeLong(21))
        }
    }

    @EnabledOnOs(OS.WINDOWS, OS.LINUX)
    @Test
    fun nativeDynamicMethodDefaultReturnsZero() {
        val method = DynamicMethod.native_("zero", Signature.returning(TypeRef.I64).build())
        method.use {
            assertEquals(0L, it.invoke())
        }
    }

    @EnabledOnOs(OS.WINDOWS, OS.LINUX)
    @Test
    fun nativeDynamicMethodThreeParams() {
        val sig = Signature.of(TypeRef.I64, TypeRef.I64, TypeRef.I64, TypeRef.I64)
        val method = DynamicMethod.native_("sum3", sig)
        method.body { ir, params ->
            val ab = ir.add(params[0], params[1])
            ir.ret(ir.add(ab, params[2]))
        }
        method.use {
            assertEquals(6L, it.invoke(1L, 2L, 3L))
        }
    }

    // --- NativeCodeBuilder: still works ---

    @EnabledOnOs(OS.WINDOWS, OS.LINUX)
    @Test
    fun nativeCodeBuilderCompileAndCall() {
        NativeCodeBuilder.create()
            .function("getFortyTwo", Signature.returning(TypeRef.I64).build())
            .returnConstant(42L)
            .compile()
            .use { code ->
                assertEquals(42L, code.call("getFortyTwo"))
            }
    }

    @EnabledOnOs(OS.WINDOWS, OS.LINUX)
    @Test
    fun nativeCodeBuilderAddFunction() {
        NativeCodeBuilder.create()
            .function("add", Signature.of(TypeRef.I64, TypeRef.I64, TypeRef.I64))
            .body { ir, params ->
                ir.ret(ir.add(params[0], params[1]))
            }
            .compile()
            .use { code ->
                assertEquals(7L, code.call("add", 3, 4))
            }
    }
}
