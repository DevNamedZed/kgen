package org.kgen.ir.text

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.types.*

class IrTextNestedTest {

    @Nested
    inner class StructPrinting {

        @Test
        fun simpleStructPrintsCorrectly() {
            val module = Module("test", structs = listOf(
                StructDef("Point", listOf(Param("x", Type.F64), Param("y", Type.F64)))
            ))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("struct %Point"))
            assertTrue(text.contains("x: f64"))
            assertTrue(text.contains("y: f64"))
        }

        @Test
        fun packedStructIncludesKeyword() {
            val module = Module("test", structs = listOf(
                StructDef("Compact", listOf(Param("a", Type.I8), Param("b", Type.I32)), packed = true)
            ))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("packed"))
        }
    }

    @Nested
    inner class ClassPrinting {

        @Test
        fun classWithFieldsAndMethods() {
            val cls = ClassDef(
                name = "Animal",
                fields = listOf(FieldDef("name", Type.OpaquePointer, MemberVisibility.PRIVATE)),
                methods = listOf(MethodDef("speak", emptyList(), Type.Void, visibility = MemberVisibility.PUBLIC)),
            )
            val module = Module("test", classes = listOf(cls))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("class Animal"))
            assertTrue(text.contains("field private"))
            assertTrue(text.contains("method public"))
        }

        @Test
        fun classWithInheritance() {
            val cls = ClassDef(
                name = "Dog",
                superClass = "Animal",
                interfaces = listOf("Runnable"),
            )
            val module = Module("test", classes = listOf(cls))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("extends Animal"))
            assertTrue(text.contains("implements Runnable"))
        }

        @Test
        fun abstractFinalClass() {
            val absCls = ClassDef(name = "Base", isAbstract = true)
            val finCls = ClassDef(name = "Leaf", isFinal = true)
            val module = Module("test", classes = listOf(absCls, finCls))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("abstract class Base"))
            assertTrue(text.contains("final class Leaf"))
        }
    }

    @Nested
    inner class InterfacePrinting {

        @Test
        fun interfaceWithMethods() {
            val iface = InterfaceDef(
                name = "Drawable",
                methods = listOf(MethodDef("draw", emptyList(), Type.Void, visibility = MemberVisibility.PUBLIC, isAbstract = true)),
            )
            val module = Module("test", interfaces = listOf(iface))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("interface Drawable"))
            assertTrue(text.contains("method public abstract"))
        }

        @Test
        fun interfaceWithSuperInterfaces() {
            val iface = InterfaceDef(name = "ReadWritable", superInterfaces = listOf("Readable", "Writable"))
            val module = Module("test", interfaces = listOf(iface))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("extends Readable, Writable"))
        }
    }

    @Nested
    inner class EnumPrinting {

        @Test
        fun enumWithVariants() {
            val enumDef = EnumDef(
                name = "Color",
                variants = listOf(
                    EnumVariant("RED", 0),
                    EnumVariant("GREEN", 1),
                    EnumVariant("BLUE", 2),
                ),
            )
            val module = Module("test", enums = listOf(enumDef))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("enum Color"))
            assertTrue(text.contains("RED"))
            assertTrue(text.contains("GREEN"))
            assertTrue(text.contains("BLUE"))
        }
    }

    @Nested
    inner class FunctionWithExceptionHandling {

        @Test
        fun invokeWithLandingPadPrintsCorrectly() {
            val personality = FunctionRef("__gxx_personality_v0", Type.Function(emptyList(), Type.I32))
            val func = FunctionRef("may_throw", Type.Function(emptyList(), Type.Void))
            val landingPadType = Type.Struct(null, listOf(Type.OpaquePointer, Type.I32))
            val fn = IrFunction(
                "caller", emptyList(), Type.Void,
                listOf(
                    BasicBlock("entry", listOf(
                        Invoke(null, func, emptyList(), Type.Void, BlockRef("cont"), BlockRef("lpad")),
                    )),
                    BasicBlock("cont", listOf(
                        Ret(null),
                    )),
                    BasicBlock("lpad", listOf(
                        LandingPad(InstructionRef("lp", landingPadType), landingPadType, emptyList(), cleanup = true),
                        Resume(InstructionRef("lp", landingPadType)),
                    )),
                ),
                personality = personality,
            )
            val module = Module("test", functions = listOf(fn))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("invoke"))
            assertTrue(text.contains("to label %cont"))
            assertTrue(text.contains("unwind label %lpad"))
            assertTrue(text.contains("landingpad"))
            assertTrue(text.contains("cleanup"))
            assertTrue(text.contains("resume"))
        }
    }

    @Nested
    inner class GlobalPrinting {

        @Test
        fun globalConstantPrintsCorrectly() {
            val global = Global("pi", Type.F64, Constant.F64(3.14159), isConstant = true)
            val module = Module("test", globals = listOf(global))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("constant"))
            assertTrue(text.contains("f64"))
            assertTrue(text.contains("3.14159"))
        }

        @Test
        fun globalMutablePrintsCorrectly() {
            val global = Global("counter", Type.I32, Constant.I32(0), isConstant = false)
            val module = Module("test", globals = listOf(global))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("global"))
        }

        @Test
        fun globalWithAlignment() {
            val global = Global("aligned", Type.I64, Constant.I64(0), align = 8)
            val module = Module("test", globals = listOf(global))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("align 8"))
        }
    }

    @Nested
    inner class TypeStringConversion {

        @Test
        fun integerTypes() {
            assertEquals("i1", IrPrinter.typeStr(Type.I1))
            assertEquals("i8", IrPrinter.typeStr(Type.I8))
            assertEquals("i16", IrPrinter.typeStr(Type.I16))
            assertEquals("i32", IrPrinter.typeStr(Type.I32))
            assertEquals("i64", IrPrinter.typeStr(Type.I64))
            assertEquals("i128", IrPrinter.typeStr(Type.I128))
        }

        @Test
        fun floatTypes() {
            assertEquals("f16", IrPrinter.typeStr(Type.F16))
            assertEquals("f32", IrPrinter.typeStr(Type.F32))
            assertEquals("f64", IrPrinter.typeStr(Type.F64))
            assertEquals("f128", IrPrinter.typeStr(Type.F128))
        }

        @Test
        fun pointerType() {
            assertEquals("ptr", IrPrinter.typeStr(Type.OpaquePointer))
        }

        @Test
        fun pointerWithAddressSpace() {
            assertEquals("ptr addrspace(1)", IrPrinter.typeStr(Type.Pointer(Type.I8, addressSpace = 1)))
        }

        @Test
        fun arrayType() {
            assertEquals("[10 x i32]", IrPrinter.typeStr(Type.Array(Type.I32, 10)))
        }

        @Test
        fun vectorType() {
            assertEquals("<4 x f32>", IrPrinter.typeStr(Type.Vector(Type.F32, 4)))
        }

        @Test
        fun scalableVectorType() {
            assertEquals("<vscale x 4 x i32>", IrPrinter.typeStr(Type.Vector(Type.I32, 4, scalable = true)))
        }

        @Test
        fun functionType() {
            val fnType = Type.Function(listOf(Type.I32, Type.I64), Type.I32)
            assertEquals("i32 (i32, i64)", IrPrinter.typeStr(fnType))
        }

        @Test
        fun voidType() {
            assertEquals("void", IrPrinter.typeStr(Type.Void))
        }
    }

    @Nested
    inner class ValueStringConversion {

        @Test
        fun parameterValue() {
            val param = Parameter("x", Type.I32, 0)
            assertEquals("%x", IrPrinter.valStr(param))
        }

        @Test
        fun instructionRefValue() {
            val ref = InstructionRef("sum", Type.I32)
            assertEquals("sum", IrPrinter.valStr(ref))
        }

        @Test
        fun globalRefValue() {
            val gref = GlobalRef("counter", Type.I32)
            assertEquals("@counter", IrPrinter.valStr(gref))
        }

        @Test
        fun functionRefValue() {
            val fref = FunctionRef("foo", Type.Function(emptyList(), Type.Void))
            assertEquals("@foo", IrPrinter.valStr(fref))
        }

        @Test
        fun constantValues() {
            assertEquals("42", IrPrinter.constStr(Constant.I32(42)))
            assertEquals("3.14", IrPrinter.constStr(Constant.F64(3.14)))
            assertEquals("null", IrPrinter.constStr(Constant.NullPtr))
            assertEquals("undef", IrPrinter.constStr(Constant.Undef(Type.I32)))
            assertEquals("poison", IrPrinter.constStr(Constant.Poison(Type.I32)))
            assertEquals("zeroinitializer", IrPrinter.constStr(Constant.ZeroInitializer(Type.I32)))
        }
    }

    @Nested
    inner class RoundTrip {

        @Test
        @org.junit.jupiter.api.Disabled("IrParser does not support full round-trip yet")
        fun simpleFunctionRoundTrips() {
            val fn = IrFunction("add", listOf(Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1)), Type.I32,
                listOf(BasicBlock("entry", listOf(
                    Add(InstructionRef("sum", Type.I32), Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1)),
                    Ret(InstructionRef("sum", Type.I32)),
                ))))
            val module = Module("test", functions = listOf(fn))
            val text = IrPrinter.print(module)
            val parsed = IrParser.parse(text)
            assertEquals(1, parsed.functions.size)
            assertEquals("add", parsed.functions[0].name)
            assertEquals(2, parsed.functions[0].params.size)
            assertEquals(Type.I32, parsed.functions[0].returnType)
        }

        @Test
        @org.junit.jupiter.api.Disabled("IrParser does not support full round-trip yet")
        fun moduleWithGlobalsRoundTrips() {
            val module = Module("test",
                globals = listOf(
                    Global("x", Type.I32, Constant.I32(42)),
                    Global("msg", Type.Array(Type.I8, 6), Constant.StringConst("hello")),
                ))
            val text = IrPrinter.print(module)
            val parsed = IrParser.parse(text)
            assertEquals(2, parsed.globals.size)
        }

        @Test
        @org.junit.jupiter.api.Disabled("IrParser does not support full round-trip yet")
        fun moduleMetadataRoundTrips() {
            val module = Module("test",
                targetTriple = "x86_64-unknown-linux-gnu",
                dataLayout = "e-m:e-p270:32:32-p271:32:32",
                sourceFile = "main.c",
            )
            val text = IrPrinter.print(module)
            val parsed = IrParser.parse(text)
            assertEquals("x86_64-unknown-linux-gnu", parsed.targetTriple)
        }
    }

    @Nested
    inner class ModuleLevelConstructs {

        @Test
        fun globalCtorsPrint() {
            val module = Module("test",
                globalCtors = listOf(GlobalCtor("init_func", 65535)))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("llvm.global_ctors"))
            assertTrue(text.contains("65535"))
            assertTrue(text.contains("init_func"))
        }

        @Test
        fun comdatPrints() {
            val module = Module("test",
                comdats = listOf(ComdatDef("grp", ComdatSelectionKind.ANY)))
            val text = IrPrinter.print(module)
            assertTrue(text.contains("comdat @grp"))
            assertTrue(text.contains("any"))
        }

        @Test
        fun moduleInlineAsmPrints() {
            val module = Module("test", moduleInlineAsm = ".globl _start")
            val text = IrPrinter.print(module)
            assertTrue(text.contains("module asm"))
            assertTrue(text.contains(".globl _start"))
        }
    }
}
