package org.kgen.ir.text

import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IrTextExtendedTest {

    private fun roundTrip(mod: Module): Module {
        val printed1 = IrPrinter.print(mod)
        val parsed = IrParser.parse(printed1)
        val printed2 = IrPrinter.print(parsed)
        assertEquals(printed1, printed2, "Round-trip mismatch")
        return parsed
    }

    private fun binaryRoundTrip(mod: Module): Module {
        val serializer = IrSerializer()
        return serializer.deserialize(serializer.serialize(mod))
    }

    // ---- Printer: arithmetic instructions ----

    @Test
    fun printSubMulDiv() {
        val mod = module("arith") {
            function("ops", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val s = sub(param(0), param(1))
                    val m = mul(s, param(1))
                    val d = sdiv(m, param(0))
                    ret(d)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("sub i32"))
        assertTrue(text.contains("mul i32"))
        assertTrue(text.contains("sdiv i32"))
    }

    @Test
    fun printUnsignedDiv() {
        val mod = module("udiv") {
            function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val d = udiv(param(0), param(1))
                    ret(d)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("udiv i32"))
    }

    @Test
    fun printRemainder() {
        val mod = module("rem") {
            function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val r = srem(param(0), param(1))
                    ret(r)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("srem i32"))
    }

    // ---- Printer: bitwise instructions ----

    @Test
    fun printBitwiseOps() {
        val mod = module("bits") {
            function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val a = and(param(0), param(1))
                    val o = or(a, param(1))
                    val x = xor(o, param(0))
                    ret(x)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("and i32"))
        assertTrue(text.contains("or i32"))
        assertTrue(text.contains("xor i32"))
    }

    @Test
    fun printShifts() {
        val mod = module("shifts") {
            function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val s = shl(param(0), param(1))
                    val r = lshr(s, param(1))
                    val ar = ashr(r, param(1))
                    ret(ar)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("shl i32"))
        assertTrue(text.contains("lshr i32"))
        assertTrue(text.contains("ashr i32"))
    }

    // ---- Printer: float arithmetic ----

    @Test
    fun printFloatArith() {
        val mod = module("fp") {
            function("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64) {
                block("entry") {
                    val s = fadd(param(0), param(1))
                    val d = fsub(s, param(1))
                    val m = fmul(d, param(0))
                    val q = fdiv(m, param(1))
                    ret(q)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("fadd f64"))
        assertTrue(text.contains("fsub f64"))
        assertTrue(text.contains("fmul f64"))
        assertTrue(text.contains("fdiv f64"))
    }

    @Test
    fun printFrem() {
        val mod = module("frem") {
            function("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64) {
                block("entry") {
                    val r = frem(param(0), param(1))
                    ret(r)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("frem f64"))
    }

    // ---- Printer: comparison predicates ----

    @Test
    fun printAllIcmpPredicates() {
        for (pred in ICmpPredicate.entries) {
            val mod = module("cmp") {
                function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1) {
                    block("entry") {
                        val cmp = icmp(pred, param(0), param(1))
                        ret(cmp)
                    }
                }
            }
            val text = IrPrinter.print(mod)
            assertTrue(text.contains("icmp ${pred.name.lowercase()} i32"), "Missing predicate: ${pred.name}")
        }
    }

    @Test
    fun printFcmpPredicates() {
        val preds = listOf(FCmpPredicate.OEQ, FCmpPredicate.OGT, FCmpPredicate.OLT, FCmpPredicate.UNE)
        for (pred in preds) {
            val mod = module("fcmp") {
                function("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1) {
                    block("entry") {
                        val cmp = fcmp(pred, param(0), param(1))
                        ret(cmp)
                    }
                }
            }
            val text = IrPrinter.print(mod)
            assertTrue(text.contains("fcmp"), "Should have fcmp")
        }
    }

    // ---- Printer: conversions ----

    @Test
    fun printAllConversions() {
        val mod = module("conv") {
            function("f", listOf(Param("i", Type.I32), Param("d", Type.F64)), Type.Void) {
                block("entry") {
                    val ext = sext(param(0), Type.I64)
                    val zext = zext(param(0), Type.I64)
                    val trunc = trunc(ext, Type.I32)
                    val fp = sitofp(param(0), Type.F64)
                    val si = fptosi(param(1), Type.I32)
                    val ui = fptoui(param(1), Type.I32)
                    val fpi = uitofp(param(0), Type.F64)
                    ret()
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("sext i32"))
        assertTrue(text.contains("zext i32"))
        assertTrue(text.contains("trunc i64"))
        assertTrue(text.contains("sitofp i32"))
        assertTrue(text.contains("fptosi f64"))
        assertTrue(text.contains("fptoui f64"))
        assertTrue(text.contains("uitofp i32"))
    }

    @Test
    fun printFpext() {
        val mod = module("fpext") {
            function("f", listOf(Param("x", Type.F32)), Type.F64) {
                block("entry") {
                    val ext = fpext(param(0), Type.F64)
                    ret(ext)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("fpext f32"))
    }

    @Test
    fun printFptrunc() {
        val mod = module("fptrunc") {
            function("f", listOf(Param("x", Type.F64)), Type.F32) {
                block("entry") {
                    val t = fptrunc(param(0), Type.F32)
                    ret(t)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("fptrunc f64"))
    }

    // ---- Printer: linkage/visibility ----

    @Test
    fun printLinkage() {
        val mod = module("link") {
            function("f", emptyList(), Type.Void, linkage = Linkage.INTERNAL) {
                block("entry") { ret() }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("internal"))
    }

    @Test
    fun printVisibility() {
        val mod = module("vis") {
            function("f", emptyList(), Type.Void, visibility = Visibility.HIDDEN) {
                block("entry") { ret() }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("hidden"))
    }

    // ---- Printer: vararg function type ----

    @Test
    fun printVarArgType() {
        val t = Type.Function(listOf(Type.OpaquePointer), Type.I32, vararg = true)
        val s = IrPrinter.typeStr(t)
        assertTrue(s.contains("..."), "Vararg should contain ...: $s")
    }

    // ---- Parser: round-trip arithmetic ----

    @Test
    fun roundTripArithmetic() {
        val mod = module("arith") {
            function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val s = sub(param(0), param(1))
                    val m = mul(s, i32(2))
                    ret(m)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun roundTripBitwise() {
        val mod = module("bits") {
            function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val r = and(param(0), param(1))
                    val o = or(r, i32(0xFF))
                    ret(o)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun roundTripFloatOps() {
        val mod = module("fp") {
            function("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64) {
                block("entry") {
                    val s = fadd(param(0), param(1))
                    val m = fmul(s, param(1))
                    ret(m)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun roundTripConversions() {
        val mod = module("conv") {
            function("f", listOf(Param("x", Type.I32)), Type.I64) {
                block("entry") {
                    val ext = sext(param(0), Type.I64)
                    ret(ext)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun roundTripSwitch() {
        val mod = module("sw") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    switch(param(0), "default", listOf(i32(0) to "c0", i32(1) to "c1"))
                }
                block("c0") { ret(i32(100)) }
                block("c1") { ret(i32(200)) }
                block("default") { ret(i32(-1)) }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun roundTripSelect() {
        val mod = module("sel") {
            function("f", listOf(Param("c", Type.I1), Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val r = select(param(0), param(1), param(2))
                    ret(r)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    @org.junit.jupiter.api.Disabled("Parser does not yet support struct definitions")
    fun roundTripStructDef() {
        val mod = module("struct") {
            struct("Pair", listOf(Param("first", Type.I32), Param("second", Type.I64)))
        }
        val restored = roundTrip(mod)
        assertEquals(1, restored.structs.size)
        assertEquals("Pair", restored.structs[0].name)
    }

    @Test
    fun roundTripMultipleGlobals() {
        val mod = module("globals") {
            global("a", Type.I32, i32(1))
            global("b", Type.I64, i64(2L))
            global("c", Type.F32, f32(3.0f))
            global("d", Type.F64, f64(4.0))
            global("e", Type.I8, i8(5))
        }
        val restored = roundTrip(mod)
        assertEquals(5, restored.globals.size)
    }

    @Test
    fun roundTripLinkageAndVisibility() {
        val mod = module("lv") {
            function("f", emptyList(), Type.Void, linkage = Linkage.INTERNAL, visibility = Visibility.HIDDEN) {
                block("entry") { ret() }
            }
        }
        val restored = roundTrip(mod)
        assertEquals(Linkage.INTERNAL, restored.functions[0].linkage)
        assertEquals(Visibility.HIDDEN, restored.functions[0].visibility)
    }

    @Test
    fun roundTripModuleMetadata() {
        val mod = module("meta") {
            targetTriple("aarch64-unknown-linux-gnu")
            sourceFile("main.c")
        }
        val restored = roundTrip(mod)
        assertEquals("aarch64-unknown-linux-gnu", restored.targetTriple)
        assertEquals("main.c", restored.sourceFile)
    }

    // ---- Serializer: additional round-trips ----

    @Test
    fun serializerRoundTripBranches() {
        val mod = module("branches") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val cmp = icmp(ICmpPredicate.SGT, param(0), i32(0))
                    condBr(cmp, "pos", "neg")
                }
                block("pos") { ret(param(0)) }
                block("neg") {
                    val neg = sub(i32(0), param(0))
                    ret(neg)
                }
            }
        }
        val restored = binaryRoundTrip(mod)
        assertEquals(3, restored.functions[0].blocks.size)
    }

    @Test
    fun serializerRoundTripPhi() {
        val mod = module("phi") {
            function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val cmp = icmp(ICmpPredicate.SGT, param(0), param(1))
                    condBr(cmp, "then", "else")
                }
                block("then") { br("merge") }
                block("else") { br("merge") }
                block("merge") {
                    val result = phi(Type.I32, listOf(param(0) to "then", param(1) to "else"))
                    ret(result)
                }
            }
        }
        val restored = binaryRoundTrip(mod)
        val phi = restored.functions[0].blocks[3].instructions[0]
        assertTrue(phi is Instruction.Phi)
    }

    @Test
    fun serializerRoundTripSelect() {
        val mod = module("sel") {
            function("f", listOf(Param("c", Type.I1), Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val r = select(param(0), param(1), param(2))
                    ret(r)
                }
            }
        }
        val restored = binaryRoundTrip(mod)
        assertEquals(2, restored.functions[0].blocks[0].instructions.size)
    }

    @Test
    fun serializerRoundTripFloats() {
        val mod = module("fp") {
            function("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32) {
                block("entry") {
                    val r = fadd(param(0), param(1))
                    ret(r)
                }
            }
        }
        val restored = binaryRoundTrip(mod)
        assertEquals(Type.F32, restored.functions[0].returnType)
    }

    @Test
    fun serializerRoundTripVarArg() {
        val mod = module("va") {
            function("printf", listOf(Param("fmt", Type.OpaquePointer)), Type.I32, isExternal = true, isVarArg = true)
        }
        val restored = binaryRoundTrip(mod)
        assertTrue(restored.functions[0].isVarArg)
    }

    @Test
    fun serializerRoundTripCallingConv() {
        val mod = module("cc") {
            function("f", emptyList(), Type.Void, callingConv = CallingConvention.FAST) {
                block("entry") { ret() }
            }
        }
        val restored = binaryRoundTrip(mod)
        assertEquals(CallingConvention.FAST, restored.functions[0].callingConv)
    }

    @Test
    fun serializerRoundTripNestedTypes() {
        val mod = module("nested") {
            global("arr", Type.Array(Type.Array(Type.I32, 10), 5))
            global("vec", Type.Vector(Type.F32, 4))
        }
        val restored = binaryRoundTrip(mod)
        val arrType = restored.globals[0].type as Type.Array
        assertTrue(arrType.element is Type.Array)
    }

    @Test
    fun serializerEmptyModule() {
        val mod = module("empty") {}
        val restored = binaryRoundTrip(mod)
        assertEquals("empty", restored.name)
        assertTrue(restored.functions.isEmpty())
        assertTrue(restored.globals.isEmpty())
    }

    // ---- Printer: type string edge cases ----

    @Test
    fun printNestedPointer() {
        val t = Type.Pointer(Type.Pointer(Type.I32))
        assertEquals("ptr", IrPrinter.typeStr(t))
    }

    @Test
    fun printScalableVector() {
        val t = Type.Vector(Type.F32, 4, scalable = true)
        assertTrue(IrPrinter.typeStr(t).contains("vscale"))
    }

    @Test
    fun printPackedStruct() {
        val t = Type.Struct(null, listOf(Type.I8, Type.I32), packed = true)
        assertTrue(IrPrinter.typeStr(t).contains("<{") || IrPrinter.typeStr(t).contains("packed"))
    }

    @Test
    fun printFunctionType() {
        val t = Type.Function(listOf(Type.I32, Type.F64), Type.Void)
        val s = IrPrinter.typeStr(t)
        assertTrue(s.contains("void"))
        assertTrue(s.contains("i32"))
        assertTrue(s.contains("f64"))
    }

    @Test
    fun printClassRef() {
        val t = Type.ClassRef("MyClass")
        val s = IrPrinter.typeStr(t)
        assertTrue(s.contains("MyClass"), "ClassRef should contain name: $s")
    }

    @Test
    fun printNullable() {
        val t = Type.Nullable(Type.I32)
        assertTrue(IrPrinter.typeStr(t).contains("i32"))
    }

    @Test
    fun printWeakRef() {
        val t = Type.WeakReference(Type.I32)
        assertTrue(IrPrinter.typeStr(t).contains("i32"))
    }

    @Test
    fun printIntN() {
        val t = Type.IntN(24)
        assertEquals("i24", IrPrinter.typeStr(t))
    }

    // ---- Parser: parse specific patterns ----

    @Test
    fun parseEmptyModule() {
        val text = """module "empty"
"""
        val mod = IrParser.parse(text)
        assertEquals("empty", mod.name)
    }

    @Test
    fun parseModuleWithTriple() {
        val text = """module "test"
target triple = "x86_64-unknown-linux-gnu"
"""
        val mod = IrParser.parse(text)
        assertEquals("x86_64-unknown-linux-gnu", mod.targetTriple)
    }

    @Test
    fun parseConstantGlobal() {
        val text = """module "test"
@x = constant i32 = 42
"""
        val mod = IrParser.parse(text)
        assertEquals(1, mod.globals.size)
        assertTrue(mod.globals[0].isConstant)
        assertEquals(42, (mod.globals[0].initializer as Constant.I32).value)
    }
}
