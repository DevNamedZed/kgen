package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.Type
import org.kgen.ir.target.Target
import org.kgen.reflect.emit.NativeCodeBuilder

class NativeCodeBuilderTest {

    @Test
    fun toIrTypePrimitives() {
        assertEquals(Type.Void, NativeCodeBuilder.toIrType(TypeRef.VOID))
        assertEquals(Type.I1, NativeCodeBuilder.toIrType(TypeRef.BOOL))
        assertEquals(Type.I8, NativeCodeBuilder.toIrType(TypeRef.I8))
        assertEquals(Type.I16, NativeCodeBuilder.toIrType(TypeRef.I16))
        assertEquals(Type.I32, NativeCodeBuilder.toIrType(TypeRef.I32))
        assertEquals(Type.I64, NativeCodeBuilder.toIrType(TypeRef.I64))
        assertEquals(Type.I8, NativeCodeBuilder.toIrType(TypeRef.U8))
        assertEquals(Type.I16, NativeCodeBuilder.toIrType(TypeRef.U16))
        assertEquals(Type.I32, NativeCodeBuilder.toIrType(TypeRef.U32))
        assertEquals(Type.I64, NativeCodeBuilder.toIrType(TypeRef.U64))
        assertEquals(Type.F32, NativeCodeBuilder.toIrType(TypeRef.F32))
        assertEquals(Type.F64, NativeCodeBuilder.toIrType(TypeRef.F64))
    }

    @Test
    fun toIrTypePointerAndArray() {
        assertEquals(Type.OpaquePointer, NativeCodeBuilder.toIrType(TypeRef.POINTER))
        assertEquals(Type.OpaquePointer, NativeCodeBuilder.toIrType(TypeRef.pointerTo(TypeRef.I32)))
        assertEquals(Type.OpaquePointer, NativeCodeBuilder.toIrType(TypeRef.byRef(TypeRef.I64)))
        assertEquals(Type.OpaquePointer, NativeCodeBuilder.toIrType(TypeRef.arrayOf(TypeRef.F32)))
    }

    @Test
    fun toIrTypeUnknownFallsToI64() {
        assertEquals(Type.I64, NativeCodeBuilder.toIrType(TypeRef.of("SomeStruct")))
    }

    @Test
    fun stubReturnsDefaultI64() {
        val bytes = NativeCodeBuilder.forTarget(Target.x86_64())
            .stub("zero", Signature.returning(TypeRef.I64).build())
            .compileBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun stubVoidFunction() {
        val bytes = NativeCodeBuilder.forTarget(Target.x86_64())
            .stub("noop", Signature.VOID)
            .compileBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun returnConstant() {
        val bytes = NativeCodeBuilder.forTarget(Target.x86_64())
            .function("getFortyTwo", Signature.returning(TypeRef.I64).build())
            .returnConstant(42L)
            .compileBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun returnParam() {
        val sig = Signature.returning(TypeRef.I64).param("x", TypeRef.I64).build()
        val bytes = NativeCodeBuilder.forTarget(Target.x86_64())
            .function("identity", sig)
            .returnParam(0)
            .compileBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun returnParamOutOfRangeFails() {
        val sig = Signature.returning(TypeRef.I64).param(TypeRef.I64).build()
        assertThrows(IllegalArgumentException::class.java) {
            NativeCodeBuilder.forTarget(Target.x86_64())
                .function("bad", sig)
                .returnParam(5)
        }
    }

    @Test
    fun delegateTo() {
        val sig = Signature.of(TypeRef.I64, TypeRef.I64, TypeRef.I64)
        val bytes = NativeCodeBuilder.forTarget(Target.x86_64())
            .function("wrappedAdd", sig)
            .delegateTo("add")
            .compileBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun multipleFunctions() {
        val sig = Signature.returning(TypeRef.I64).build()
        val bytes = NativeCodeBuilder.forTarget(Target.x86_64())
            .function("one", sig).returnConstant(1L)
            .function("two", sig).returnConstant(2L)
            .compileBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun customBody() {
        val sig = Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32)
        val bytes = NativeCodeBuilder.forTarget(Target.x86_64())
            .function("add", sig)
            .body { ir, params ->
                val sum = ir.add(params[0], params[1])
                ir.ret(sum)
            }
            .compileBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun noFunctionsFailsOnCompile() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeCodeBuilder.forTarget(Target.x86_64()).compileBytes()
        }
    }

    @Test
    fun arm64Target() {
        val bytes = NativeCodeBuilder.forTarget(Target.arm64())
            .stub("noop")
            .compileBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun riscvTarget() {
        val bytes = NativeCodeBuilder.forTarget(Target.riscv64())
            .stub("noop")
            .compileBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun wasmTargetNotSupportedForGenerateCode() {
        // WASM doesn't support generateCode() (lightweight JIT path)
        assertThrows(UnsupportedOperationException::class.java) {
            NativeCodeBuilder.forTarget(Target.wasm())
                .stub("noop")
                .compileBytes()
        }
    }
}
