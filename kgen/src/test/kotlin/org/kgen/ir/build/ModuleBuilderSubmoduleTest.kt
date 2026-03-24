package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ModuleBuilderSubmoduleTest {

    @Nested
    inner class SubmoduleLifecycle {

        @Test
        fun `basic submodule creates submodule in built module`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.beginSubmodule("math", setOf(IrCategory.ARITHMETIC, IrCategory.TERMINATOR))

            ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.add(ir.param(0), ir.param(1)))
            ir.finalizeFunction()

            ir.endSubmodule()

            val mod = ir.build()
            assertEquals(1, mod.submodules.size)
            assertEquals("math", mod.submodules[0].name)
            assertTrue(mod.submodules[0].functions.contains("add"))
        }

        @Test
        fun `submodule tracks globals`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.beginSubmodule("data", setOf(IrCategory.ARITHMETIC, IrCategory.TERMINATOR))
            ir.addGlobal("counter", Type.I32, Constant.I32(0))
            ir.endSubmodule()

            val mod = ir.build()
            assertEquals(1, mod.submodules[0].globals.size)
            assertTrue(mod.submodules[0].globals.contains("counter"))
        }

        @Test
        fun `multiple submodules`() {
            val ir = ModuleBuilder("test", Target.x86_64())

            ir.beginSubmodule("sub1", setOf(IrCategory.ARITHMETIC, IrCategory.TERMINATOR))
            ir.createFunction("f1", emptyList(), Type.Void)
            ir.appendBlock("entry")
            ir.ret()
            ir.finalizeFunction()
            ir.endSubmodule()

            ir.beginSubmodule("sub2", setOf(IrCategory.ARITHMETIC, IrCategory.TERMINATOR))
            ir.createFunction("f2", emptyList(), Type.Void)
            ir.appendBlock("entry")
            ir.ret()
            ir.finalizeFunction()
            ir.endSubmodule()

            val mod = ir.build()
            assertEquals(2, mod.submodules.size)
            assertEquals("sub1", mod.submodules[0].name)
            assertEquals("sub2", mod.submodules[1].name)
        }

        @Test
        fun `functions outside submodule are not tracked`() {
            val ir = ModuleBuilder("test", Target.x86_64())

            ir.createFunction("outside", emptyList(), Type.Void)
            ir.appendBlock("entry")
            ir.ret()
            ir.finalizeFunction()

            ir.beginSubmodule("sub", setOf(IrCategory.ARITHMETIC, IrCategory.TERMINATOR))
            ir.createFunction("inside", emptyList(), Type.Void)
            ir.appendBlock("entry")
            ir.ret()
            ir.finalizeFunction()
            ir.endSubmodule()

            val mod = ir.build()
            assertEquals(1, mod.submodules.size)
            assertEquals(listOf("inside"), mod.submodules[0].functions)
        }
    }

    @Nested
    inner class SubmoduleErrors {

        @Test
        fun `cannot nest submodules`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.beginSubmodule("outer", setOf(IrCategory.ARITHMETIC))
            assertThrows(IllegalStateException::class.java) {
                ir.beginSubmodule("inner", setOf(IrCategory.BITWISE))
            }
        }

        @Test
        fun `cannot end submodule without begin`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            assertThrows(IllegalStateException::class.java) {
                ir.endSubmodule()
            }
        }

        @Test
        fun `cannot begin submodule while building function`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.createFunction("f", emptyList(), Type.Void)
            assertThrows(IllegalStateException::class.java) {
                ir.beginSubmodule("sub", setOf(IrCategory.ARITHMETIC))
            }
        }

        @Test
        fun `cannot build module with open submodule`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.beginSubmodule("open", setOf(IrCategory.ARITHMETIC))
            assertThrows(IllegalStateException::class.java) {
                ir.build()
            }
        }
    }

    @Nested
    inner class ConstraintEnforcement {

        @Test
        fun `submodule constraints restrict allowed instructions`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.beginSubmodule("termonly", setOf(IrCategory.TERMINATOR))

            ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")

            assertThrows(IllegalStateException::class.java) {
                ir.add(ir.param(0), ir.param(1))
            }
        }

        @Test
        fun `submodule constraints allow matching categories`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.beginSubmodule("math", setOf(IrCategory.ARITHMETIC, IrCategory.TERMINATOR))

            ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val sum = ir.add(ir.param(0), ir.param(1))
            ir.ret(sum)
            ir.finalizeFunction()
            ir.endSubmodule()

            val mod = ir.build()
            assertEquals(1, mod.functions.size)
        }

        @Test
        fun `module-level allowedCategories restrict instructions`() {
            val ir = ModuleBuilder("test", Target.x86_64(), allowedCategories = setOf(IrCategory.TERMINATOR))

            ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")

            assertThrows(IllegalStateException::class.java) {
                ir.add(ir.param(0), ir.param(1))
            }
        }

        @Test
        fun `module-level allowedCategories allow matching instructions`() {
            val ir = ModuleBuilder("test", Target.x86_64(),
                allowedCategories = setOf(IrCategory.ARITHMETIC, IrCategory.TERMINATOR))

            ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val sum = ir.add(ir.param(0), ir.param(1))
            ir.ret(sum)
            ir.finalizeFunction()

            val mod = ir.build()
            assertEquals(1, mod.functions.size)
        }

        @Test
        fun `null allowedCategories permits all instructions`() {
            val ir = ModuleBuilder("test", Target.x86_64(), allowedCategories = null)

            ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val sum = ir.add(ir.param(0), ir.param(1))
            ir.ret(sum)
            ir.finalizeFunction()

            val mod = ir.build()
            assertEquals(1, mod.functions.size)
        }

        @Test
        fun `submodule constraints override module-level constraints`() {
            val ir = ModuleBuilder("test", Target.x86_64(),
                allowedCategories = setOf(IrCategory.TERMINATOR))

            ir.beginSubmodule("math", setOf(IrCategory.ARITHMETIC, IrCategory.TERMINATOR))

            ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val sum = ir.add(ir.param(0), ir.param(1))
            ir.ret(sum)
            ir.finalizeFunction()
            ir.endSubmodule()

            val mod = ir.build()
            assertEquals(1, mod.functions.size)
        }
    }

    @Nested
    inner class MaxTier {

        @Test
        fun `maxTier defaults to OBJECT when no constraints`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            assertEquals(IrTier.OBJECT, ir.maxTier)
        }

        @Test
        fun `maxTier reflects highest category tier`() {
            val ir = ModuleBuilder("test", Target.x86_64(),
                allowedCategories = setOf(IrCategory.ARITHMETIC, IrCategory.TERMINATOR))
            assertEquals(IrTier.MACHINE, ir.maxTier)
        }

        @Test
        fun `maxTier with only structural categories`() {
            val ir = ModuleBuilder("test", Target.x86_64(),
                allowedCategories = setOf(IrCategory.TERMINATOR, IrCategory.CALL))
            assertEquals(IrTier.STRUCTURAL, ir.maxTier)
        }
    }
}
