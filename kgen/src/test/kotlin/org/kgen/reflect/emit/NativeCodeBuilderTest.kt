package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.ir.Type
import org.kgen.ir.target.Target
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef

class NativeCodeBuilderTest {

    @Nested
    inner class FactoryMethods {

        @Test
        fun createReturnsBuilder() {
            val builder = NativeCodeBuilder.create()
            assertNotNull(builder)
        }

        @Test
        fun forTargetReturnsBuilder() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            assertNotNull(builder)
        }

        @Test
        fun forTargetArm64ReturnsBuilder() {
            val builder = NativeCodeBuilder.forTarget(Target.arm64())
            assertNotNull(builder)
        }

        @Test
        fun forTargetRiscvReturnsBuilder() {
            val builder = NativeCodeBuilder.forTarget(Target.riscv64())
            assertNotNull(builder)
        }
    }

    @Nested
    inner class FunctionBuilder {

        @Test
        fun functionReturnsBuilder() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val fb = builder.function("test", Signature.of(TypeRef.I64))
            assertNotNull(fb)
        }

        @Test
        fun returnConstantReturnsParentBuilder() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val result = builder
                .function("test", Signature.of(TypeRef.I64))
                .returnConstant(42L)
            assertSame(builder, result)
        }

        @Test
        fun returnDefaultReturnsParentBuilder() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val result = builder
                .function("test", Signature.of(TypeRef.I64))
                .returnDefault()
            assertSame(builder, result)
        }

        @Test
        fun delegateToReturnsParentBuilder() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val result = builder
                .function("wrapper", Signature.of(TypeRef.I64, TypeRef.I64))
                .delegateTo("target")
            assertSame(builder, result)
        }

        @Test
        fun returnParamReturnsParentBuilder() {
            val sig = Signature.of(TypeRef.I64, TypeRef.I64)
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val result = builder
                .function("identity", sig)
                .returnParam(0)
            assertSame(builder, result)
        }

        @Test
        fun returnParamDefaultIndex() {
            val sig = Signature.of(TypeRef.I64, TypeRef.I64)
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val result = builder
                .function("identity", sig)
                .returnParam()
            assertSame(builder, result)
        }

        @Test
        fun returnParamOutOfRangeThrows() {
            val sig = Signature.of(TypeRef.I64, TypeRef.I64)
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val fb = builder.function("test", sig)
            assertThrows(IllegalArgumentException::class.java) {
                fb.returnParam(1)
            }
        }

        @Test
        fun returnParamNegativeIndexThrows() {
            val sig = Signature.of(TypeRef.I64, TypeRef.I64)
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val fb = builder.function("test", sig)
            assertThrows(IllegalArgumentException::class.java) {
                fb.returnParam(-1)
            }
        }

        @Test
        fun bodyReturnsParentBuilder() {
            val sig = Signature.of(TypeRef.I64, TypeRef.I64)
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val result = builder
                .function("custom", sig)
                .body { ir, params ->
                    ir.ret(params[0])
                }
            assertSame(builder, result)
        }
    }

    @Nested
    inner class StubMethod {

        @Test
        fun stubReturnsBuilder() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val result = builder.stub("myStub", Signature.of(TypeRef.I64))
            assertSame(builder, result)
        }

        @Test
        fun stubDefaultSignature() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val result = builder.stub("myStub")
            assertSame(builder, result)
        }
    }

    @Nested
    inner class CompileValidation {

        @Test
        fun compileWithNoFunctionsThrows() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            assertThrows(IllegalArgumentException::class.java) {
                builder.compile()
            }
        }

        @Test
        fun compileBytesWithNoFunctionsThrows() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            assertThrows(IllegalArgumentException::class.java) {
                builder.compileBytes()
            }
        }
    }

    @Nested
    inner class CompileBytes {

        @Test
        fun compileBytesReturnsMachineCode() {
            val bytes = NativeCodeBuilder.forTarget(Target.x86_64())
                .stub("noop")
                .compileBytes()
            assertTrue(bytes.isNotEmpty())
        }

        @Test
        fun compileBytesMultipleFunctions() {
            val bytes = NativeCodeBuilder.forTarget(Target.x86_64())
                .stub("a", Signature.of(TypeRef.I64))
                .stub("b", Signature.of(TypeRef.I32))
                .compileBytes()
            assertTrue(bytes.isNotEmpty())
        }

        @Test
        fun compileBytesReturnConstant() {
            val bytes = NativeCodeBuilder.forTarget(Target.x86_64())
                .function("getVal", Signature.of(TypeRef.I64))
                .returnConstant(100L)
                .compileBytes()
            assertTrue(bytes.isNotEmpty())
        }
    }

    @Nested
    inner class Mangling {

        @Test
        fun withManglingReturnsSelf() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val result = builder.withMangling(org.kgen.binary.mangling.ManglingScheme.ITANIUM)
            assertSame(builder, result)
        }
    }

    @Nested
    inner class ToIrType {

        @Test
        fun voidMapsToVoid() {
            assertEquals(Type.Void, NativeCodeBuilder.toIrType(TypeRef.VOID))
        }

        @Test
        fun boolMapsToI1() {
            assertEquals(Type.I1, NativeCodeBuilder.toIrType(TypeRef.BOOL))
        }

        @Test
        fun i8MapsToI8() {
            assertEquals(Type.I8, NativeCodeBuilder.toIrType(TypeRef.I8))
        }

        @Test
        fun u8MapsToI8() {
            assertEquals(Type.I8, NativeCodeBuilder.toIrType(TypeRef.U8))
        }

        @Test
        fun i16MapsToI16() {
            assertEquals(Type.I16, NativeCodeBuilder.toIrType(TypeRef.I16))
        }

        @Test
        fun u16MapsToI16() {
            assertEquals(Type.I16, NativeCodeBuilder.toIrType(TypeRef.U16))
        }

        @Test
        fun i32MapsToI32() {
            assertEquals(Type.I32, NativeCodeBuilder.toIrType(TypeRef.I32))
        }

        @Test
        fun u32MapsToI32() {
            assertEquals(Type.I32, NativeCodeBuilder.toIrType(TypeRef.U32))
        }

        @Test
        fun i64MapsToI64() {
            assertEquals(Type.I64, NativeCodeBuilder.toIrType(TypeRef.I64))
        }

        @Test
        fun u64MapsToI64() {
            assertEquals(Type.I64, NativeCodeBuilder.toIrType(TypeRef.U64))
        }

        @Test
        fun f32MapsToF32() {
            assertEquals(Type.F32, NativeCodeBuilder.toIrType(TypeRef.F32))
        }

        @Test
        fun f64MapsToF64() {
            assertEquals(Type.F64, NativeCodeBuilder.toIrType(TypeRef.F64))
        }

        @Test
        fun pointerMapsToOpaquePointer() {
            assertEquals(Type.OpaquePointer, NativeCodeBuilder.toIrType(TypeRef.POINTER))
        }

        @Test
        fun pointerTypeMapsToOpaquePointer() {
            val ptrType = TypeRef.pointerTo(TypeRef.I32)
            assertEquals(Type.OpaquePointer, NativeCodeBuilder.toIrType(ptrType))
        }

        @Test
        fun arrayTypeMapsToOpaquePointer() {
            val arrType = TypeRef.arrayOf(TypeRef.I32)
            assertEquals(Type.OpaquePointer, NativeCodeBuilder.toIrType(arrType))
        }

        @Test
        fun byRefTypeMapsToOpaquePointer() {
            val byRef = TypeRef.byRef(TypeRef.I64)
            assertEquals(Type.OpaquePointer, NativeCodeBuilder.toIrType(byRef))
        }

        @Test
        fun unknownTypeFallsToI64() {
            val named = TypeRef.of("SomeStruct")
            assertEquals(Type.I64, NativeCodeBuilder.toIrType(named))
        }
    }

    @Nested
    inner class FluentChaining {

        @Test
        fun multipleFunctionsCanBeChained() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val result = builder
                .function("a", Signature.of(TypeRef.I64))
                .returnConstant(1L)
                .function("b", Signature.of(TypeRef.I64))
                .returnConstant(2L)
                .stub("c")
            assertSame(builder, result)
        }

        @Test
        fun stubThenFunctionChaining() {
            val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            val result = builder
                .stub("first")
                .function("second", Signature.of(TypeRef.I64))
                .returnDefault()
            assertSame(builder, result)
        }
    }
}
