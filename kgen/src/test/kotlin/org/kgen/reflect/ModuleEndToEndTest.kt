package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.binary.elf.ElfObjectWriter
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

/**
 * End-to-end tests: IR → CodeGen → ELF bytes → Module.fromBytes() → reflect API.
 */
class ModuleEndToEndTest {

    private fun buildElfObjectFile(block: IrBuilder.() -> Unit): ObjectFile {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        val module = ir.build()
        return X86CodeGenerator().generateObjectFile(module)
    }

    private fun buildElfBytes(block: IrBuilder.() -> Unit): ByteArray {
        val obj = buildElfObjectFile(block)
        return ElfObjectWriter().write(obj)
    }

    private fun addFunction(): ByteArray = buildElfBytes {
        val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        appendBlock("entry")
        val sum = add(params[0], params[1])
        ret(sum)
        finalizeFunction()
    }

    @Test
    fun moduleFromElfBytes() {
        val bytes = addFunction()
        val module = Module.fromBytes(bytes, "test.o")
        assertEquals("test.o", module.name())
        assertEquals(ObjectFormat.ELF, module.format())
        assertEquals(ArchType.X86_64, module.arch().arch)
    }

    @Test
    fun moduleSymbolLookup() {
        val bytes = addFunction()
        val module = Module.fromBytes(bytes, "test.o")
        val sym = module.symbol("add")
        assertNotNull(sym)
        assertEquals("add", sym!!.name())
        assertTrue(sym.isFunction())
    }

    @Test
    fun moduleFunctionLookup() {
        val bytes = addFunction()
        val module = Module.fromBytes(bytes, "test.o")
        val func = module.function("add")
        assertNotNull(func)
        assertEquals("add", func!!.name())
    }

    @Test
    fun multipleFunctions() {
        val bytes = buildElfBytes {
            val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val sum = add(params[0], params[1])
            ret(sum)
            finalizeFunction()

            val params2 = createFunction("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val diff = sub(params2[0], params2[1])
            ret(diff)
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        assertNotNull(module.function("add"))
        assertNotNull(module.function("sub"))
        assertTrue(module.functions().size >= 2)
    }

    @Test
    fun moduleHasTextSection() {
        val bytes = buildElfBytes {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val textSection = module.section(".text")
        assertNotNull(textSection)
        assertTrue(textSection!!.data.isNotEmpty())
    }

    @Test
    fun moduleFromObjectFile() {
        val obj = buildElfObjectFile {
            val params = createFunction("mul", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val prod = mul(params[0], params[1])
            ret(prod)
            finalizeFunction()
        }
        val module = Module.fromObjectFile(obj, "mul.o")
        assertEquals("mul.o", module.name())
        assertNotNull(module.function("mul"))
    }

    @Test
    fun functionWithSignature() {
        val bytes = buildElfBytes {
            val params = createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val sum = add(params[0], params[1])
            ret(sum)
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val func = module.function("add")!!
            .withSignature(
                Signature.returning(TypeRef.I64)
                    .param("a", TypeRef.I64)
                    .param("b", TypeRef.I64)
                    .build()
            )
        assertTrue(func.hasSignature())
        assertEquals(TypeRef.I64, func.returnType())
        assertEquals(2, func.parameterTypes().size)
        assertEquals("a", func.parameters()[0].name)
    }

    @Test
    fun symbolBackReference() {
        val bytes = buildElfBytes {
            createFunction("test_func", emptyList(), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val sym = module.symbol("test_func")!!
        assertSame(module, sym.module())
        val func = sym.function()!!
        assertSame(sym, func.symbol())
        assertSame(module, func.module())
    }

    @Test
    fun elfFormatDetection() {
        val bytes = addFunction()
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }

    @Test
    fun moduleNotLoaded() {
        val bytes = buildElfBytes {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        assertFalse(module.isLoaded())
        assertEquals(0L, module.baseAddress())
        assertTrue(module.hasNativeCode())
        assertFalse(module.hasClr())
        assertFalse(module.hasJvm())
        assertFalse(module.hasWasm())
    }
}
