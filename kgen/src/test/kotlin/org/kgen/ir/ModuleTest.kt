package org.kgen.ir

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.ir.types.StructDef

class ModuleTest {

    @Nested
    inner class Defaults {

        @Test
        fun moduleWithNameOnlyHasEmptyDefaults() {
            val module = Module(name = "test")
            assertEquals("test", module.name)
            assertNull(module.targetTriple)
            assertNull(module.dataLayout)
            assertTrue(module.functions.isEmpty())
            assertTrue(module.globals.isEmpty())
            assertTrue(module.structs.isEmpty())
            assertTrue(module.classes.isEmpty())
            assertTrue(module.interfaces.isEmpty())
            assertTrue(module.enums.isEmpty())
            assertTrue(module.aliases.isEmpty())
            assertTrue(module.metadata.isEmpty())
            assertNull(module.sourceFile)
            assertTrue(module.targetFeatures.isEmpty())
            assertTrue(module.globalCtors.isEmpty())
            assertTrue(module.globalDtors.isEmpty())
            assertTrue(module.ifuncs.isEmpty())
            assertTrue(module.comdats.isEmpty())
            assertNull(module.moduleInlineAsm)
            assertTrue(module.moduleFlags.isEmpty())
            assertNull(module.constraints)
            assertTrue(module.submodules.isEmpty())
        }
    }

    @Nested
    inner class FullyPopulated {

        @Test
        fun moduleWithAllFieldsPopulated() {
            val fn = IrFunction(
                name = "main",
                params = emptyList(),
                returnType = Type.I32,
                blocks = listOf(BasicBlock("entry", emptyList())),
            )
            val global = Global(name = "counter", type = Type.I32)
            val structDef = StructDef(name = "Point", fields = listOf(Param("x", Type.F64), Param("y", Type.F64)))
            val alias = TypeAlias(name = "Int", type = Type.I32)
            val ctor = GlobalCtor(function = "init", priority = 100)
            val dtor = GlobalCtor(function = "cleanup", priority = 200)
            val ifunc = IFunc(
                name = "resolve_memcpy",
                resolverFunction = "memcpy_resolver",
                type = Type.Function(listOf(Type.OpaquePointer), Type.OpaquePointer),
            )
            val comdat = ComdatDef(name = "group1", selectionKind = ComdatSelectionKind.ANY)
            val submodule = Submodule(
                name = "gc",
                constraints = setOf(IrCategory.MEMORY, IrCategory.RUNTIME),
                functions = listOf("gc_collect"),
            )

            val module = Module(
                name = "full",
                targetTriple = "x86_64-unknown-linux-gnu",
                dataLayout = "e-m:e-p:64:64",
                functions = listOf(fn),
                globals = listOf(global),
                structs = listOf(structDef),
                aliases = listOf(alias),
                metadata = mapOf("dbg" to MetadataValue.StringMD("test.c")),
                sourceFile = "test.c",
                targetFeatures = setOf("+sse4.2", "+avx2"),
                globalCtors = listOf(ctor),
                globalDtors = listOf(dtor),
                ifuncs = listOf(ifunc),
                comdats = listOf(comdat),
                moduleInlineAsm = "nop",
                moduleFlags = mapOf("pic-level" to ModuleFlagValue.IntFlag(2)),
                constraints = setOf(IrCategory.ARITHMETIC, IrCategory.MEMORY),
                submodules = listOf(submodule),
            )

            assertEquals("full", module.name)
            assertEquals("x86_64-unknown-linux-gnu", module.targetTriple)
            assertEquals("e-m:e-p:64:64", module.dataLayout)
            assertEquals(1, module.functions.size)
            assertEquals("main", module.functions[0].name)
            assertEquals(1, module.globals.size)
            assertEquals("counter", module.globals[0].name)
            assertEquals(1, module.structs.size)
            assertEquals("Point", module.structs[0].name)
            assertEquals(1, module.aliases.size)
            assertEquals("Int", module.aliases[0].name)
            assertEquals("test.c", module.sourceFile)
            assertEquals(setOf("+sse4.2", "+avx2"), module.targetFeatures)
            assertEquals(1, module.globalCtors.size)
            assertEquals(100, module.globalCtors[0].priority)
            assertEquals(1, module.globalDtors.size)
            assertEquals(200, module.globalDtors[0].priority)
            assertEquals(1, module.ifuncs.size)
            assertEquals("resolve_memcpy", module.ifuncs[0].name)
            assertEquals(1, module.comdats.size)
            assertEquals("group1", module.comdats[0].name)
            assertEquals("nop", module.moduleInlineAsm)
            assertEquals(1, module.moduleFlags.size)
            assertEquals(ModuleFlagValue.IntFlag(2), module.moduleFlags["pic-level"])
            assertEquals(setOf(IrCategory.ARITHMETIC, IrCategory.MEMORY), module.constraints)
            assertEquals(1, module.submodules.size)
            assertEquals("gc", module.submodules[0].name)
        }
    }

    @Nested
    inner class CopyAndEquality {

        @Test
        fun copyWithModifiedName() {
            val original = Module(name = "original", targetTriple = "wasm32")
            val copy = original.copy(name = "copy")
            assertEquals("copy", copy.name)
            assertEquals("wasm32", copy.targetTriple)
        }

        @Test
        fun copyWithModifiedFunctions() {
            val original = Module(name = "mod")
            val fn = IrFunction(name = "foo", params = emptyList(), returnType = Type.Void, blocks = emptyList())
            val copy = original.copy(functions = listOf(fn))
            assertTrue(original.functions.isEmpty())
            assertEquals(1, copy.functions.size)
        }

        @Test
        fun copyWithModifiedGlobals() {
            val original = Module(name = "mod")
            val global = Global(name = "g", type = Type.I64)
            val copy = original.copy(globals = listOf(global))
            assertTrue(original.globals.isEmpty())
            assertEquals(1, copy.globals.size)
        }

        @Test
        fun equalModulesAreEqual() {
            val a = Module(name = "test", targetTriple = "x86_64")
            val b = Module(name = "test", targetTriple = "x86_64")
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun differentModulesAreNotEqual() {
            val a = Module(name = "a")
            val b = Module(name = "b")
            assertNotEquals(a, b)
        }

        @Test
        fun moduleWithDifferentTargetTripleNotEqual() {
            val a = Module(name = "mod", targetTriple = "x86_64")
            val b = Module(name = "mod", targetTriple = "aarch64")
            assertNotEquals(a, b)
        }
    }

    @Nested
    inner class MetadataTests {

        @Test
        fun moduleMetadataVariants() {
            val module = Module(
                name = "meta",
                metadata = mapOf(
                    "str" to MetadataValue.StringMD("hello"),
                    "int" to MetadataValue.IntMD(42),
                    "ref" to MetadataValue.RefMD("other"),
                    "node" to MetadataValue.NodeMD(listOf(MetadataValue.StringMD("a"), MetadataValue.IntMD(1))),
                ),
            )
            assertEquals(4, module.metadata.size)
            assertEquals(MetadataValue.StringMD("hello"), module.metadata["str"])
            assertEquals(MetadataValue.IntMD(42), module.metadata["int"])
            assertEquals(MetadataValue.RefMD("other"), module.metadata["ref"])
            val node = module.metadata["node"] as MetadataValue.NodeMD
            assertEquals(2, node.values.size)
        }
    }

    @Nested
    inner class ModuleFlagTests {

        @Test
        fun intFlag() {
            val flag = ModuleFlagValue.IntFlag(2)
            assertEquals(2, flag.value)
        }

        @Test
        fun stringFlag() {
            val flag = ModuleFlagValue.StringFlag("frame-pointer")
            assertEquals("frame-pointer", flag.value)
        }

        @Test
        fun metadataFlag() {
            val inner = MetadataValue.StringMD("value")
            val flag = ModuleFlagValue.MetadataFlag(inner)
            assertEquals(inner, flag.value)
        }
    }

    @Nested
    inner class SupportingTypes {

        @Test
        fun globalCtorDefaults() {
            val ctor = GlobalCtor(function = "init")
            assertEquals("init", ctor.function)
            assertEquals(65535, ctor.priority)
            assertNull(ctor.associatedData)
        }

        @Test
        fun globalCtorAllFields() {
            val ctor = GlobalCtor(function = "init", priority = 10, associatedData = "@data")
            assertEquals("init", ctor.function)
            assertEquals(10, ctor.priority)
            assertEquals("@data", ctor.associatedData)
        }

        @Test
        fun ifuncDefaults() {
            val fnType = Type.Function(listOf(Type.I32), Type.I64)
            val ifunc = IFunc(name = "my_ifunc", resolverFunction = "resolver", type = fnType)
            assertEquals("my_ifunc", ifunc.name)
            assertEquals("resolver", ifunc.resolverFunction)
            assertEquals(Linkage.EXTERNAL, ifunc.linkage)
            assertEquals(Visibility.DEFAULT, ifunc.visibility)
        }

        @Test
        fun comdatDef() {
            val comdat = ComdatDef(name = "group", selectionKind = ComdatSelectionKind.EXACT_MATCH)
            assertEquals("group", comdat.name)
            assertEquals(ComdatSelectionKind.EXACT_MATCH, comdat.selectionKind)
        }

        @Test
        fun typeAlias() {
            val alias = TypeAlias(name = "MyInt", type = Type.I32)
            assertEquals("MyInt", alias.name)
            assertEquals(Type.I32, alias.type)
        }

        @Test
        fun submoduleDefaults() {
            val sub = Submodule(name = "core", constraints = setOf(IrCategory.ARITHMETIC))
            assertEquals("core", sub.name)
            assertEquals(setOf(IrCategory.ARITHMETIC), sub.constraints)
            assertTrue(sub.functions.isEmpty())
            assertTrue(sub.globals.isEmpty())
        }

        @Test
        fun submoduleAllFields() {
            val sub = Submodule(
                name = "native",
                constraints = setOf(IrCategory.MEMORY, IrCategory.ARITHMETIC),
                functions = listOf("alloc", "free"),
                globals = listOf("heap_ptr"),
            )
            assertEquals(2, sub.functions.size)
            assertEquals(1, sub.globals.size)
        }

        @Test
        fun comdatSelectionKindValues() {
            val values = ComdatSelectionKind.entries
            assertEquals(5, values.size)
            assertTrue(values.contains(ComdatSelectionKind.ANY))
            assertTrue(values.contains(ComdatSelectionKind.EXACT_MATCH))
            assertTrue(values.contains(ComdatSelectionKind.LARGEST))
            assertTrue(values.contains(ComdatSelectionKind.NO_DUPLICATES))
            assertTrue(values.contains(ComdatSelectionKind.SAME_SIZE))
        }
    }
}
