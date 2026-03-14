package org.kgen.integration.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.binary.*
import org.kgen.reflect.Module as ReflectModule
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef

/**
 * Integration tests for the reflect API: IR → codegen → ObjectFile → Module → reflect.
 */
class ModuleReflectTest {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    private fun buildIrModule(block: IrBuilder.() -> Unit): org.kgen.ir.Module {
        val ir = IrBuilder("reflect_test", Target.x86_64())
        if (isWindows) ir.targetTriple = "x86_64-unknown-windows-msvc"
        ir.block()
        return ir.build()
    }

    private fun generateAndReflect(block: IrBuilder.() -> Unit): ReflectModule {
        val irModule = buildIrModule(block)
        val obj = X86CodeGenerator().generateObjectFile(irModule)
        return ReflectModule.fromObjectFile(obj, "test.o")
    }

    @Test
    fun moduleFromGeneratedObjectFile() {
        val module = generateAndReflect {
            createFunction("hello", emptyList(), Type.I64)
            appendBlock("entry")
            ret(Constant.I64(42))
            finalizeFunction()
        }

        assertEquals("test.o", module.name())
        assertTrue(module.hasNativeCode())
        assertFalse(module.hasJvm())
        assertFalse(module.hasClr())
        assertFalse(module.isLoaded())
    }

    @Test
    fun symbolLookupByName() {
        val module = generateAndReflect {
            val params = createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(add(params[0], params[1]))
            finalizeFunction()
        }

        val sym = module.symbol("add")
        assertNotNull(sym, "Should find 'add' symbol. Symbols: ${module.symbols().map { it.name() }}")
        assertTrue(sym!!.isFunction())
        assertEquals("add", sym.name())
    }

    @Test
    fun functionLookup() {
        val module = generateAndReflect {
            val params = createFunction("square", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(mul(params[0], params[0]))
            finalizeFunction()
        }

        val funcs = module.functions()
        assertTrue(funcs.isNotEmpty(), "Should have functions")
        val sq = module.function("square")
        assertNotNull(sq, "Should find 'square' function. Functions: ${funcs.map { it.name() }}")
        assertEquals("square", sq!!.name())
    }

    @Test
    fun multipleFunctionsDiscovery() {
        val module = generateAndReflect {
            val addParams = createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(add(addParams[0], addParams[1]))
            finalizeFunction()

            val subParams = createFunction("sub", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(sub(subParams[0], subParams[1]))
            finalizeFunction()

            val mulParams = createFunction("mul", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(mul(mulParams[0], mulParams[1]))
            finalizeFunction()
        }

        val funcNames = module.functions().map { it.name() }
        assertTrue("add" in funcNames, "Should have add. Functions: $funcNames")
        assertTrue("sub" in funcNames, "Should have sub. Functions: $funcNames")
        assertTrue("mul" in funcNames, "Should have mul. Functions: $funcNames")
    }

    @Test
    fun textSectionPresent() {
        val module = generateAndReflect {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(Constant.I64(0))
            finalizeFunction()
        }

        val sections = module.sections()
        assertTrue(sections.isNotEmpty(), "Should have sections")
        val textSection = module.textSection()
        assertNotNull(textSection, "Should have text section. Sections: ${sections.map { it.name }}")
    }

    @Test
    fun functionWithSignatureAttachment() {
        val module = generateAndReflect {
            val params = createFunction("compute", listOf(
                Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(add(params[0], params[1]))
            finalizeFunction()
        }

        val fn = module.function("compute")
        assertNotNull(fn)

        val sig = Signature.returning(TypeRef.I64)
            .param("a", TypeRef.I64)
            .param("b", TypeRef.I64)
            .build()
        val typed = fn!!.withSignature(sig)

        assertTrue(typed.hasSignature())
        assertEquals(TypeRef.I64, typed.returnType())
        assertEquals(2, typed.parameters().size)
        assertEquals("a", typed.parameters()[0].name)
        assertEquals(TypeRef.I64, typed.parameters()[0].type)
    }

    @Test
    fun symbolBackReferenceToModule() {
        val module = generateAndReflect {
            createFunction("test", emptyList(), Type.I64)
            appendBlock("entry")
            ret(Constant.I64(1))
            finalizeFunction()
        }

        val sym = module.symbols().firstOrNull { it.isFunction() }
        assertNotNull(sym)
        assertEquals(module, sym!!.module())
    }

    @Test
    fun moduleFromElfBytes() {
        val irModule = buildIrModule {
            createFunction("entry", emptyList(), Type.I64)
            appendBlock("entry")
            ret(Constant.I64(0))
            finalizeFunction()
        }

        val obj = X86CodeGenerator().generateObjectFile(irModule)
        val writer = org.kgen.binary.elf.ElfObjectWriter()
        val elfBytes = writer.write(obj)

        val reflected = ReflectModule.fromBytes(elfBytes, "test.elf")
        assertEquals("test.elf", reflected.name())
        assertEquals(ObjectFormat.ELF, reflected.format())
        assertTrue(reflected.hasNativeCode())
        assertFalse(reflected.hasJvm())
    }

    @Test
    fun moduleNotLoadedByDefault() {
        val module = generateAndReflect {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(Constant.I64(0))
            finalizeFunction()
        }

        assertFalse(module.isLoaded())
        assertEquals(0L, module.baseAddress())
    }
}
