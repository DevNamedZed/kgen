package org.kgen.ir.build.scope

import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModuleBuilderScopeTest {

    @Test
    fun createFunctionWithScope() {
        val module = ModuleBuilder("test", Target.x86_64())

        val fn = module.createFunction(NativeScope::class.java, "add",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)

        val ins = fn.instructions
        fn.ret(ins.add(fn.param(0), fn.param(1)))
        fn.end()

        val ir = module.build()
        assertTrue(ir.functions.any { it.name == "add" })
    }

    @Test
    fun defineFunctionBlockStyle() {
        val module = ModuleBuilder("test", Target.x86_64())

        module.defineFunction(NativeScope::class.java, "negate",
            listOf(Param("x", Type.I32)), Type.I32) { fn ->
            val ins = fn.instructions
            fn.ret(ins.neg(fn.param(0)))
        }

        val ir = module.build()
        assertTrue(ir.functions.any { it.name == "negate" })
    }

    @Test
    fun scopeExtensionsWorkThroughProxy() {
        val module = ModuleBuilder("test", Target.x86_64())

        module.defineFunction(NativeScope::class.java, "compute",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
            val ins = fn.instructions

            val sum = ins.add(fn.param(0), fn.param(1))
            val isPositive = ins.gt(sum, Constant.I32(0))
            val quotient = ins.div(fn.param(0), fn.param(1))
            val shifted = ins.shr(sum, Constant.I32(1))

            fn.ret(shifted)
        }

        val ir = module.build()
        val func = ir.functions.find { it.name == "compute" }
        assertNotNull(func)
    }

    @Test
    fun memoryExtensionsWorkThroughProxy() {
        val module = ModuleBuilder("test", Target.x86_64())

        module.defineFunction(NativeScope::class.java, "counter",
            listOf(Param("n", Type.I32)), Type.I32) { fn ->
            val ins = fn.instructions

            val counter = ins.variable(Constant.I32(0))
            fn.whileLoop(
                condition = { ins.lt(ins.get(counter), fn.param(0)) },
                body = { ins.set(counter, ins.add(ins.get(counter), Constant.I32(1))) },
            )
            fn.ret(ins.get(counter))
        }

        val ir = module.build()
        assertTrue(ir.functions.any { it.name == "counter" })
    }

    @Test
    fun managedScopeFunction() {
        val module = ModuleBuilder("test", Target.jvm())

        module.defineFunction(ManagedScope::class.java, "createUser",
            listOf(Param("name", Type.OpaquePointer)), Type.OpaquePointer) { fn ->
            val ins = fn.instructions

            val user = ins.newObject("User")
            ins.putField(user, "User", "name", Type.OpaquePointer, fn.param(0))
            fn.ret(user)
        }

        val ir = module.build()
        assertTrue(ir.functions.any { it.name == "createUser" })
    }

    @Test
    fun targetProfileConstructor() {
        val module = ModuleBuilder("test", TargetProfile.NATIVE)
        val fn = module.function("identity", listOf(Param("x", Type.I32)), Type.I32)
        fn.ret(fn.param(0))
        fn.end()

        val ir = module.build()
        assertTrue(ir.functions.any { it.name == "identity" })
    }

    @Test
    fun fullFlowWithClassAndScopedFunctions() {
        val module = ModuleBuilder("test", TargetProfile.NATIVE)

        module.defineClass(NativeScope::class.java, "MathLib") { cls ->
            cls.defineStaticFunction("add",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                val ins = fn.instructions
                fn.ret(ins.add(fn.param(0), fn.param(1)))
            }

            cls.defineStaticFunction("isPositive",
                listOf(Param("x", Type.I32)), Type.I1) { fn ->
                val ins = fn.instructions
                fn.ret(ins.gt(fn.param(0), Constant.I32(0)))
            }
        }

        module.defineFunction(NativeScope::class.java, "main",
            listOf(Param("argc", Type.I32)), Type.I32) { fn ->
            fn.ret(fn.param(0))
        }

        val ir = module.build()
        assertTrue(ir.functions.any { it.name == "MathLib_add" })
        assertTrue(ir.functions.any { it.name == "MathLib_isPositive" })
        assertTrue(ir.functions.any { it.name == "main" })
        assertNotNull(module.findClass("MathLib"))
    }
}
