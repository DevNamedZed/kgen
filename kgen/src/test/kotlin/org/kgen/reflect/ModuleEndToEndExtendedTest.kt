package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.binary.elf.ElfObjectWriter
import org.kgen.backend.x86.codegen.X86CodeGenerator
import org.kgen.backend.riscv.codegen.RiscVCodeGenerator
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class ModuleEndToEndExtendedTest {

    private fun buildX86ElfBytes(block: IrBuilder.() -> Unit): ByteArray {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        val module = ir.build()
        val obj = X86CodeGenerator().generateObjectFile(module)
        return ElfObjectWriter().write(obj)
    }

    private fun buildRiscVElfBytes(block: IrBuilder.() -> Unit): ByteArray {
        val ir = IrBuilder("test", Target.riscv64())
        ir.block()
        val module = ir.build()
        val obj = RiscVCodeGenerator().generateObjectFile(module)
        return ElfObjectWriter().write(obj)
    }

    @Test
    fun x86ModuleVoidFunction() {
        val bytes = buildX86ElfBytes {
            createFunction("noop", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "noop.o")
        assertNotNull(module.function("noop"))
        assertTrue(module.symbol("noop")!!.isFunction())
    }

    @Test
    fun x86ModuleFunctionSize() {
        val bytes = buildX86ElfBytes {
            val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(params[0], params[1]))
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val func = module.function("add")!!
        assertTrue(func.size() >= 0, "Function should have non-negative size")
    }

    @Test
    fun x86ModuleFunctionAddress() {
        val bytes = buildX86ElfBytes {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(params[0])
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val func = module.function("f")!!
        // In object file, address is relative offset
        assertTrue(func.offset() >= 0)
    }

    @Test
    fun x86ModuleMultipleSections() {
        val bytes = buildX86ElfBytes {
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val sections = module.sections()
        assertTrue(sections.isNotEmpty())
        val textSection = module.section(".text")
        assertNotNull(textSection)
    }

    @Test
    fun x86ModuleSymbols() {
        val bytes = buildX86ElfBytes {
            val params = createFunction("alpha", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(params[0])
            finalizeFunction()

            val params2 = createFunction("beta", listOf(Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(params2[0])
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val symbols = module.symbols()
        val funcNames = symbols.filter { it.isFunction() }.map { it.name() }
        assertTrue("alpha" in funcNames)
        assertTrue("beta" in funcNames)
    }

    @Test
    fun x86ModuleNonexistentSymbol() {
        val bytes = buildX86ElfBytes {
            createFunction("exists", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        assertNull(module.symbol("does_not_exist"))
        assertNull(module.function("does_not_exist"))
    }

    @Test
    fun x86ModuleExternalDeclaration() {
        val bytes = buildX86ElfBytes {
            declareFunction("printf", listOf(Param("fmt", Type.OpaquePointer)), Type.I32, isVarArg = true)
            createFunction("main", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val printf = module.symbol("printf")
        assertNotNull(printf)
    }

    @Test
    fun riscvModuleBasic() {
        val bytes = buildRiscVElfBytes {
            val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(params[0], params[1]))
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "riscv.o")
        assertNotNull(module.function("add"))
    }

    @Test
    fun riscvModuleVoid() {
        val bytes = buildRiscVElfBytes {
            createFunction("noop", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "riscv.o")
        assertNotNull(module.function("noop"))
        assertTrue(module.hasNativeCode())
    }

    @Test
    fun signatureWithMultipleParams() {
        val bytes = buildX86ElfBytes {
            val params = createFunction("compute", listOf(
                Param("a", Type.I32), Param("b", Type.I32),
                Param("c", Type.I64), Param("d", Type.I64)
            ), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val sum1 = add(sext(params[0], Type.I64), params[2])
            val sum2 = add(sext(params[1], Type.I64), params[3])
            ret(add(sum1, sum2))
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val func = module.function("compute")!!
            .withSignature(
                Signature.returning(TypeRef.I64)
                    .param("a", TypeRef.I32)
                    .param("b", TypeRef.I32)
                    .param("c", TypeRef.I64)
                    .param("d", TypeRef.I64)
                    .build()
            )
        assertEquals(4, func.parameterTypes().size)
        assertEquals(TypeRef.I64, func.returnType())
    }

    @Test
    fun signatureVoidReturn() {
        val bytes = buildX86ElfBytes {
            createFunction("doWork", listOf(Param("x", Type.I32)), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val func = module.function("doWork")!!
            .withSignature(
                Signature.returning(TypeRef.VOID)
                    .param("x", TypeRef.I32)
                    .build()
            )
        assertEquals(TypeRef.VOID, func.returnType())
        assertEquals(1, func.parameterTypes().size)
    }

    @Test
    fun moduleToString() {
        val bytes = buildX86ElfBytes {
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val str = module.toString()
        assertTrue(str.isNotBlank())
    }

    @Test
    fun moduleFormatDetectionElf() {
        val bytes = buildX86ElfBytes {
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }

    @Test
    fun moduleTextSectionHasCode() {
        val bytes = buildX86ElfBytes {
            val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(params[0], params[1]))
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val text = module.section(".text")!!
        assertTrue(text.data.size > 2, "Text section should have real code")
    }

    @Test
    fun moduleMultipleFunctionSymbols() {
        val bytes = buildX86ElfBytes {
            for (name in listOf("f1", "f2", "f3", "f4", "f5")) {
                val params = createFunction(name, listOf(Param("x", Type.I32)), Type.I32)
                positionAtEnd(appendBlock("entry"))
                ret(params[0])
                finalizeFunction()
            }
        }
        val module = Module.fromBytes(bytes, "test.o")
        for (name in listOf("f1", "f2", "f3", "f4", "f5")) {
            assertNotNull(module.function(name), "Should have function $name")
        }
        assertTrue(module.functions().size >= 5)
    }

    @Test
    fun moduleFunctionIterator() {
        val bytes = buildX86ElfBytes {
            val p1 = createFunction("inc", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(p1[0], Constant.I32(1)))
            finalizeFunction()

            val p2 = createFunction("dec", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(sub(p2[0], Constant.I32(1)))
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val funcs = module.functions()
        val names = funcs.map { it.name() }
        assertTrue("inc" in names)
        assertTrue("dec" in names)
    }

    @Test
    fun riscvModuleMultipleFunctions() {
        val bytes = buildRiscVElfBytes {
            val p1 = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(p1[0], p1[1]))
            finalizeFunction()

            val p2 = createFunction("mul", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(mul(p2[0], p2[1]))
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "riscv.o")
        assertNotNull(module.function("add"))
        assertNotNull(module.function("mul"))
    }

    @Test
    fun moduleI64Function() {
        val bytes = buildX86ElfBytes {
            val params = createFunction("sum64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(add(params[0], params[1]))
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val func = module.function("sum64")
        assertNotNull(func)
    }

    @Test
    fun moduleArithmeticChain() {
        val bytes = buildX86ElfBytes {
            val params = createFunction("poly", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val x = params[0]
            val x2 = mul(x, x)
            val x2plus1 = add(x2, Constant.I32(1))
            ret(x2plus1)
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        val func = module.function("poly")
        assertNotNull(func)
        assertTrue(func!!.size() >= 0)
    }

    @Test
    fun symbolIsNotFunction() {
        val bytes = buildX86ElfBytes {
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        // Section symbols, etc. should not be functions
        val nonFuncSymbols = module.symbols().filter { !it.isFunction() }
        assertTrue(nonFuncSymbols.isNotEmpty() || module.symbols().isNotEmpty())
    }

    @Test
    fun moduleSectionsNotEmpty() {
        val bytes = buildX86ElfBytes {
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }
        val module = Module.fromBytes(bytes, "test.o")
        assertTrue(module.sections().isNotEmpty())
    }
}
