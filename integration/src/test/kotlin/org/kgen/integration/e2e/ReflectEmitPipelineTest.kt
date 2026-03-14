package org.kgen.integration.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.binary.ObjectFormat
import org.kgen.binary.SectionKind
import org.kgen.binary.SymbolKind
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.reflect.Module as ReflectModule
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef
import org.kgen.target.x86.codegen.X86CodeGenerator

/**
 * Reflect.emit pipeline: build IR → compile → reflect → query.
 * Also: NativeModuleBuilder → compile → invoke (when on supported platform).
 */
class ReflectEmitPipelineTest {

    // -- Reflect from ObjectFile --

    @Test
    fun reflectFromObjectFile() {
        val module = buildModule()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val reflected = ReflectModule.fromObjectFile(obj, "test.o")

        assertEquals("test.o", reflected.name())
        assertTrue(reflected.hasNativeCode())
        assertFalse(reflected.isLoaded())
        assertEquals(ObjectFormat.ELF, reflected.format())
    }

    @Test
    fun reflectSymbolDiscovery() {
        val module = buildMultiModule()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val reflected = ReflectModule.fromObjectFile(obj, "multi.o")

        val symbols = reflected.symbols()
        assertTrue(symbols.isNotEmpty())

        val addSym = reflected.symbol("add")
        assertNotNull(addSym, "Should find 'add' symbol")
        assertTrue(addSym!!.isFunction())

        val mulSym = reflected.symbol("mul")
        assertNotNull(mulSym, "Should find 'mul' symbol")
    }

    @Test
    fun reflectFunctionDiscovery() {
        val module = buildMultiModule()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val reflected = ReflectModule.fromObjectFile(obj, "multi.o")

        val funcs = reflected.functions()
        assertTrue(funcs.size >= 2, "Should have at least 2 functions: ${funcs.map { it.name() }}")

        val addFn = reflected.function("add")
        assertNotNull(addFn)
    }

    @Test
    fun reflectWithSignature() {
        val module = buildModule()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val reflected = ReflectModule.fromObjectFile(obj, "sig_test.o")

        val fn = reflected.function("compute")
        assertNotNull(fn)

        val sig = Signature.returning(TypeRef.I64)
            .param("x", TypeRef.I64)
            .build()
        val typed = fn!!.withSignature(sig)
        assertTrue(typed.hasSignature())
        assertEquals(TypeRef.I64, typed.returnType())
    }

    @Test
    fun reflectSections() {
        val module = buildModule()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val reflected = ReflectModule.fromObjectFile(obj, "sections.o")

        val sections = reflected.sections()
        assertTrue(sections.isNotEmpty())

        val textSection = reflected.textSection()
        assertNotNull(textSection, "Should have a text section")
    }

    // -- Reflect from compiled Windows module --

    @Test
    fun reflectFromWindowsBinary() {
        val ir = IrBuilder("pe_reflect", Target.x86_64())
        ir.targetTriple = "x86_64-unknown-windows-msvc"
        val p = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.add(p[0], p[1]))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val reflected = ReflectModule.fromObjectFile(obj, "pe_test.o")

        assertTrue(reflected.hasNativeCode())
        assertNotNull(reflected.function("add"))
    }

    // -- Full pipeline: build → optimize → compile → reflect → verify --

    @Test
    fun fullReflectPipeline() {
        // 1. Build IR
        val ir = IrBuilder("pipeline", Target.x86_64())

        val addP = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.add(addP[0], addP[1]))
        ir.finalizeFunction()

        val absP = ir.createFunction("abs_val", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val cond = ir.icmp(ICmpPredicate.SGE, absP[0], Constant.I64(0))
        ir.condBr(cond, BlockRef("pos"), BlockRef("neg"))
        ir.appendBlock("pos")
        ir.ret(absP[0])
        ir.appendBlock("neg")
        ir.ret(ir.sub(Constant.I64(0), absP[0]))
        ir.finalizeFunction()

        // 2. Compile
        val obj = X86CodeGenerator().generateObjectFile(ir.build())

        // 3. Reflect
        val reflected = ReflectModule.fromObjectFile(obj, "full_pipeline.o")

        // 4. Verify
        assertTrue(reflected.hasNativeCode())
        assertEquals(ObjectFormat.ELF, reflected.format())

        val funcs = reflected.functions()
        val funcNames = funcs.map { it.name() }
        assertTrue("add" in funcNames, "Missing 'add': $funcNames")
        assertTrue("abs_val" in funcNames, "Missing 'abs_val': $funcNames")

        // Attach signatures
        val addFn = reflected.function("add")!!
        val addSig = Signature.returning(TypeRef.I64).param("a", TypeRef.I64).param("b", TypeRef.I64).build()
        val typedAdd = addFn.withSignature(addSig)
        assertEquals(2, typedAdd.parameters().size)
        assertEquals(TypeRef.I64, typedAdd.returnType())
    }

    // -- Helpers --

    private fun buildModule(): Module {
        val ir = IrBuilder("reflect_test", Target.x86_64())
        val p = ir.createFunction("compute", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.mul(p[0], Constant.I64(2)))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildMultiModule(): Module {
        val ir = IrBuilder("multi_reflect", Target.x86_64())

        val addP = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.add(addP[0], addP[1]))
        ir.finalizeFunction()

        val mulP = ir.createFunction("mul", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.mul(mulP[0], mulP[1]))
        ir.finalizeFunction()

        return ir.build()
    }
}
