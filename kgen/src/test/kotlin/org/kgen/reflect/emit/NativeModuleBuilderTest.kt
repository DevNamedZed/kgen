package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.ir.Constant
import org.kgen.ir.Param
import org.kgen.ir.Type

class NativeModuleBuilderTest {

    @Nested
    inner class Construction {

        @Test
        fun constructsWithName() {
            val mod = NativeModuleBuilder("testmod")
            assertEquals("testmod", mod.name)
        }

        @Test
        fun constructsWithCodeGenerator() {
            val codegen = NativeModuleBuilder.hostCodeGenerator()
            val mod = NativeModuleBuilder("testmod", codegen)
            assertEquals("testmod", mod.name)
        }
    }

    @Nested
    inner class IrBuilder {

        @Test
        fun irBuilderIsNotNull() {
            val mod = NativeModuleBuilder("testmod")
            assertNotNull(mod.irBuilder())
        }

        @Test
        fun irBuilderReturnsSameInstance() {
            val mod = NativeModuleBuilder("testmod")
            assertSame(mod.irBuilder(), mod.irBuilder())
        }
    }

    @Nested
    inner class IrModule {

        @Test
        fun irModuleReturnsModule() {
            val mod = NativeModuleBuilder("testmod")
            val ir = mod.irBuilder()
            ir.createFunction("noop", emptyList(), Type.Void)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret()
            ir.finalizeFunction()
            val module = mod.irModule()
            assertNotNull(module)
        }
    }

    @Nested
    inner class ToBytes {

        @Test
        fun toBytesReturnsNonEmpty() {
            val mod = NativeModuleBuilder("testmod")
            val ir = mod.irBuilder()
            ir.createFunction("noop", emptyList(), Type.Void)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret()
            ir.finalizeFunction()
            assertTrue(mod.toBytes().isNotEmpty())
        }

        @Test
        fun toBytesWithParams() {
            val mod = NativeModuleBuilder("testmod")
            val ir = mod.irBuilder()
            val params = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(ir.add(params[0], params[1]))
            ir.finalizeFunction()
            assertTrue(mod.toBytes().isNotEmpty())
        }
    }

    @Nested
    inner class HostDetection {

        @Test
        fun hostTargetIsNotNull() {
            assertNotNull(NativeModuleBuilder.hostTarget())
        }

        @Test
        fun hostCodeGeneratorIsNotNull() {
            assertNotNull(NativeModuleBuilder.hostCodeGenerator())
        }

        @Test
        fun hostTargetHasValidArch() {
            val target = NativeModuleBuilder.hostTarget()
            val arch = target.arch
            assertNotNull(arch)
        }
    }
}
