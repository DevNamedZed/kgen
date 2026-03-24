package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.ir.types.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ModuleBuilderModuleLevelTest {

    @Nested
    inner class GlobalCtorsAndDtors {

        @Test
        fun `add global constructor with default priority`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addGlobalCtor("init")

            val mod = ir.build()
            assertEquals(1, mod.globalCtors.size)
            assertEquals("init", mod.globalCtors[0].function)
            assertEquals(65535, mod.globalCtors[0].priority)
        }

        @Test
        fun `add global constructor with custom priority`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addGlobalCtor("early_init", priority = 100)

            val mod = ir.build()
            assertEquals(100, mod.globalCtors[0].priority)
        }

        @Test
        fun `add global destructor`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addGlobalDtor("cleanup")

            val mod = ir.build()
            assertEquals(1, mod.globalDtors.size)
            assertEquals("cleanup", mod.globalDtors[0].function)
        }

        @Test
        fun `multiple constructors preserve order`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addGlobalCtor("first", 100)
            ir.addGlobalCtor("second", 200)
            ir.addGlobalCtor("third", 300)

            val mod = ir.build()
            assertEquals(3, mod.globalCtors.size)
            assertEquals("first", mod.globalCtors[0].function)
            assertEquals("second", mod.globalCtors[1].function)
            assertEquals("third", mod.globalCtors[2].function)
        }
    }

    @Nested
    inner class Comdats {

        @Test
        fun `add comdat`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addComdat("group1", ComdatSelectionKind.ANY)

            val mod = ir.build()
            assertEquals(1, mod.comdats.size)
            assertEquals("group1", mod.comdats[0].name)
            assertEquals(ComdatSelectionKind.ANY, mod.comdats[0].selectionKind)
        }
    }

    @Nested
    inner class ModuleFlags {

        @Test
        fun `add module flag`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addModuleFlag("pic-level", ModuleFlagValue.IntFlag(2L))

            val mod = ir.build()
            assertTrue(mod.moduleFlags.containsKey("pic-level"))
            assertEquals(ModuleFlagValue.IntFlag(2L), mod.moduleFlags["pic-level"])
        }
    }

    @Nested
    inner class ModuleProperties {

        @Test
        fun `data layout`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.dataLayout = "e-m:e-p270:32:32"

            val mod = ir.build()
            assertEquals("e-m:e-p270:32:32", mod.dataLayout)
        }

        @Test
        fun `module inline asm`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.moduleInlineAsm = ".globl _start"

            val mod = ir.build()
            assertEquals(".globl _start", mod.moduleInlineAsm)
        }

        @Test
        fun `module name can be changed`() {
            val ir = ModuleBuilder("original", Target.x86_64())
            ir.moduleName = "renamed"

            val mod = ir.build()
            assertEquals("renamed", mod.name)
        }

        @Test
        fun `target features`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addTargetFeature("+sse4.2")
            ir.addTargetFeature("+avx2")

            val mod = ir.build()
            assertTrue(mod.targetFeatures.contains("+sse4.2"))
            assertTrue(mod.targetFeatures.contains("+avx2"))
        }

        @Test
        fun `constraints stored in module`() {
            val cats = setOf(IrCategory.ARITHMETIC, IrCategory.TERMINATOR)
            val ir = ModuleBuilder("test", Target.x86_64(), allowedCategories = cats)

            val mod = ir.build()
            assertEquals(cats, mod.constraints)
        }
    }

    @Nested
    inner class Globals {

        @Test
        fun `global with thread local mode`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addGlobal("tls_var", Type.I32, Constant.I32(0),
                threadLocal = ThreadLocalMode.LOCAL_EXEC)

            val mod = ir.build()
            assertEquals(ThreadLocalMode.LOCAL_EXEC, mod.globals[0].threadLocal)
        }

        @Test
        fun `global with section and alignment`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addGlobal("special", Type.I64, Constant.I64(0L),
                section = ".data.special", align = 16)

            val mod = ir.build()
            assertEquals(".data.special", mod.globals[0].section)
            assertEquals(16, mod.globals[0].align)
        }

        @Test
        fun `global with address space`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addGlobal("gpu_data", Type.I32, addressSpace = 3)

            val mod = ir.build()
            assertEquals(3, mod.globals[0].addressSpace)
        }

        @Test
        fun `global with linkage and visibility`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addGlobal("internal_val", Type.I32, Constant.I32(42),
                linkage = Linkage.INTERNAL, visibility = Visibility.HIDDEN)

            val mod = ir.build()
            assertEquals(Linkage.INTERNAL, mod.globals[0].linkage)
            assertEquals(Visibility.HIDDEN, mod.globals[0].visibility)
        }

        @Test
        fun `addGlobal returns GlobalRef`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val ref = ir.addGlobal("x", Type.I32)

            assertEquals("x", ref.name)
            assertEquals(Type.I32, ref.type)
        }
    }

    @Nested
    inner class FunctionMetadata {

        @Test
        fun `function with calling convention`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.createFunction("fast_fn", emptyList(), Type.Void,
                callingConv = CallingConvention.FAST)
            ir.appendBlock("entry")
            ir.ret()
            ir.finalizeFunction()

            val mod = ir.build()
            assertEquals(CallingConvention.FAST, mod.functions[0].callingConv)
        }

        @Test
        fun `function with attributes`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.createFunction("pure_fn", emptyList(), Type.I32,
                attributes = setOf(FnAttribute.NOUNWIND, FnAttribute.READONLY))
            ir.appendBlock("entry")
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()

            val mod = ir.build()
            assertTrue(mod.functions[0].attributes.contains(FnAttribute.NOUNWIND))
            assertTrue(mod.functions[0].attributes.contains(FnAttribute.READONLY))
        }

        @Test
        fun `function with vararg`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.createFunction("printf_like", listOf(Param("fmt", Type.OpaquePointer)), Type.I32,
                isVarArg = true)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()

            val mod = ir.build()
            assertTrue(mod.functions[0].isVarArg)
        }

        @Test
        fun `function with section and alignment`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.createFunction("hot_fn", emptyList(), Type.Void,
                section = ".text.hot", align = 32)
            ir.appendBlock("entry")
            ir.ret()
            ir.finalizeFunction()

            val mod = ir.build()
            assertEquals(".text.hot", mod.functions[0].section)
            assertEquals(32, mod.functions[0].align)
        }

        @Test
        fun `function with gc`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.createFunction("gc_fn", emptyList(), Type.Void, gc = "shadow-stack")
            ir.appendBlock("entry")
            ir.ret()
            ir.finalizeFunction()

            val mod = ir.build()
            assertEquals("shadow-stack", mod.functions[0].gc)
        }

        @Test
        fun `function with personality`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val personalityRef = FunctionRef("__gxx_personality_v0",
                Type.Function(emptyList(), Type.I32))
            ir.createFunction("eh_fn", emptyList(), Type.Void,
                personality = personalityRef)
            ir.appendBlock("entry")
            ir.ret()
            ir.finalizeFunction()

            val mod = ir.build()
            assertNotNull(mod.functions[0].personality)
            assertEquals("__gxx_personality_v0", mod.functions[0].personality!!.name)
        }

        @Test
        fun `declare external function with calling convention`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val ref = ir.declareFunction("ext_fn", emptyList(), Type.Void,
                callingConv = CallingConvention.FAST)

            val mod = ir.build()
            assertEquals(CallingConvention.FAST, mod.functions[0].callingConv)
            assertTrue(mod.functions[0].isExternal)
        }

        @Test
        fun `declare external vararg function`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.declareFunction("printf", listOf(Param("fmt", Type.OpaquePointer)), Type.I32,
                isVarArg = true)

            val mod = ir.build()
            assertTrue(mod.functions[0].isVarArg)
        }

        @Test
        fun `functionRef creates valid reference`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fnType = Type.Function(listOf(Type.I32), Type.I32)
            val ref = ir.functionRef("myFunc", fnType)

            assertEquals("myFunc", ref.name)
            assertEquals(fnType, ref.type)
        }
    }

    @Nested
    inner class TypeDefinitions {

        @Test
        fun `packed struct`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addStruct("Packed", listOf(Param("a", Type.I8), Param("b", Type.I32)), packed = true)

            val mod = ir.build()
            assertTrue(mod.structs[0].packed)
        }

        @Test
        fun `struct with alignment`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addStruct("Aligned", listOf(Param("x", Type.F64)), align = 16)

            val mod = ir.build()
            assertEquals(16, mod.structs[0].align)
        }
    }
}
