package org.kgen.ir.build.scope

import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScopedInstructionTest {

    @Test
    fun nativeScopeInstructionProxy() {
        val module = ModuleBuilder("test", Target.x86_64())
        val cls = module.createClass(NativeScope::class.java, "MathLib")

        val fn = cls.createStaticFunction("add",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)

        val instructions = fn.instructions
        assertNotNull(instructions)

        val sum = instructions.add(fn.param(0), fn.param(1))
        fn.ret(sum)
        fn.end()
        cls.build()

        val ir = module.build()
        assertTrue(ir.functions.any { it.name == "MathLib_add" })
    }

    @Test
    fun nativeScopeExtensions() {
        val module = ModuleBuilder("test", Target.x86_64())
        val cls = module.createClass(NativeScope::class.java, "Comparisons")

        val fn = cls.createStaticFunction("isPositive",
            listOf(Param("x", Type.I32)), Type.I1)

        val instructions = fn.instructions

        val isGreater = instructions.gt(fn.param(0), Constant.I32(0))
        fn.ret(isGreater)
        fn.end()
        cls.build()

        val ir = module.build()
        val func = ir.functions.find { it.name == "Comparisons_isPositive" }
        assertNotNull(func)
        assertEquals(2, func.blocks.first().instructions.size)
    }

    @Test
    fun nativeScopeMemoryExtensions() {
        val module = ModuleBuilder("test", Target.x86_64())
        val cls = module.createClass(NativeScope::class.java, "Counter")

        val fn = cls.createStaticFunction("count",
            listOf(Param("n", Type.I32)), Type.I32)

        val ins = fn.instructions
        val counter = ins.variable(Constant.I32(0))

        fn.whileLoop(
            condition = { ins.lt(ins.get(counter), fn.param(0)) },
            body = { ins.set(counter, ins.add(ins.get(counter), Constant.I32(1))) }
        )

        fn.ret(ins.get(counter))
        fn.end()
        cls.build()

        val ir = module.build()
        assertTrue(ir.functions.any { it.name == "Counter_count" })
    }

    @Test
    fun nativeScopeArithmeticExtensions() {
        val module = ModuleBuilder("test", Target.x86_64())
        val cls = module.createClass(NativeScope::class.java, "Math")

        val fn = cls.createStaticFunction("divmod",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)

        val ins = fn.instructions
        val quotient = ins.div(fn.param(0), fn.param(1))
        val remainder = ins.rem(fn.param(0), fn.param(1))
        val result = ins.add(quotient, remainder)
        fn.ret(result)
        fn.end()
        cls.build()

        val ir = module.build()
        assertTrue(ir.functions.any { it.name == "Math_divmod" })
    }

    @Test
    fun managedScopeObjectOperations() {
        val module = ModuleBuilder("test", Target.jvm())
        val cls = module.createClass(ManagedScope::class.java, "UserService")

        val fn = cls.createStaticFunction("createUser",
            listOf(Param("name", Type.OpaquePointer)), Type.OpaquePointer)

        val ins = fn.instructions
        val user = ins.newObject("User")
        ins.putField(user, "User", "name", Type.OpaquePointer, fn.param(0))
        fn.ret(user)
        fn.end()
        cls.build()

        val ir = module.build()
        assertTrue(ir.functions.any { it.name == "UserService_createUser" })
    }

    @Test
    fun defineClassBlockStyleWithTypedProxy() {
        val module = ModuleBuilder("test", Target.x86_64())

        module.defineClass(NativeScope::class.java, "Calculator") { calc ->
            calc.field("value", Type.I32)
            calc.defineStaticFunction("add",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                val ins = fn.instructions
                fn.ret(ins.add(fn.param(0), fn.param(1)))
            }
        }

        val ir = module.build()
        assertNotNull(module.findClass("Calculator"))
        assertTrue(ir.functions.any { it.name == "Calculator_add" })
    }
}
