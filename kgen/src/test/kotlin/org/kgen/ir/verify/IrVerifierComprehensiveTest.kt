package org.kgen.ir.verify

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.*
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IrVerifierComprehensiveTest {

    private fun verify(mod: Module): VerificationResult = IrVerifier.verify(mod)

    private fun assertValid(mod: Module) {
        val result = verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    private fun assertInvalid(mod: Module, vararg fragments: String) {
        val result = verify(mod)
        assertFalse(result.isValid, "Expected verification to fail")
        for (frag in fragments) {
            assertTrue(result.errors.any { it.message.contains(frag) },
                "Expected error containing '$frag', got: $result")
        }
    }

    private fun simpleFunc(
        name: String = "f",
        params: List<Parameter> = emptyList(),
        returnType: Type = Type.Void,
        blocks: List<BasicBlock> = listOf(BasicBlock("entry", listOf(Ret(null)))),
        isExternal: Boolean = false,
    ) = IrFunction(name, params, returnType, blocks, isExternal)

    private fun singleInstrFunc(vararg instructions: Instruction, returnType: Type = Type.Void): Module {
        val instList = instructions.toList() + Ret(null)
        return Module(name = "test", functions = listOf(
            IrFunction("f", emptyList(), returnType, listOf(BasicBlock("entry", instList)))
        ))
    }

    private fun ref(name: String = "%0", type: Type = Type.I32) = InstructionRef(name, type)
    private fun param32(name: String = "a", idx: Int = 0) = Parameter(name, Type.I32, idx)
    private fun param64(name: String = "a", idx: Int = 0) = Parameter(name, Type.I64, idx)
    private fun paramF32(name: String = "a", idx: Int = 0) = Parameter(name, Type.F32, idx)
    private fun paramF64(name: String = "a", idx: Int = 0) = Parameter(name, Type.F64, idx)
    private fun paramPtr(name: String = "p", idx: Int = 0) = Parameter(name, Type.OpaquePointer, idx)
    private fun paramI1(name: String = "c", idx: Int = 0) = Parameter(name, Type.I1, idx)

    // Valid modules

    @Test
    fun `empty module passes`() {
        assertValid(Module(name = "empty"))
    }

    @Test
    fun `module with single void function passes`() {
        assertValid(Module(name = "test", functions = listOf(simpleFunc())))
    }

    @Test
    fun `module with external function passes`() {
        assertValid(Module(name = "test", functions = listOf(simpleFunc(isExternal = true, blocks = emptyList()))))
    }

    @Test
    fun `module with multiple valid functions passes`() {
        assertValid(Module(name = "test", functions = listOf(
            simpleFunc("f1"), simpleFunc("f2"), simpleFunc("f3"),
        )))
    }

    @Test
    fun `valid add function passes`() {
        val a = param32("a", 0)
        val b = param32("b", 1)
        val mod = Module(name = "test", functions = listOf(
            IrFunction("add", listOf(a, b), Type.I32, listOf(
                BasicBlock("entry", listOf(
                    Add(ref("%0"), a, b),
                    Ret(ref("%0")),
                ))
            ))
        ))
        assertValid(mod)
    }

    @Test
    fun `valid multi-block control flow passes`() {
        val mod = module("test") {
            function("maxval", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
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
        assertValid(mod)
    }

    @Test
    fun `valid function with globals passes`() {
        val mod = Module(name = "test",
            globals = listOf(Global("x", Type.I32, Constant.I32(42), isConstant = true)),
            functions = listOf(simpleFunc()),
        )
        assertValid(mod)
    }

    @Test
    fun `valid function with structs passes`() {
        val mod = Module(name = "test",
            structs = listOf(StructDef("Point", listOf(Param("x", Type.F64), Param("y", Type.F64)))),
            functions = listOf(simpleFunc()),
        )
        assertValid(mod)
    }

    @Test
    fun `valid function with switch passes`() {
        val a = param32("a", 0)
        val mod = Module(name = "test", functions = listOf(
            IrFunction("sw", listOf(a), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    Switch(a, "default", listOf(Constant.I32(0) to "case0", Constant.I32(1) to "case1")),
                )),
                BasicBlock("case0", listOf(Ret(null))),
                BasicBlock("case1", listOf(Ret(null))),
                BasicBlock("default", listOf(Ret(null))),
            ))
        ))
        assertValid(mod)
    }

    @Test
    fun `valid function with alloca and load store passes`() {
        val p = paramPtr("p", 0)
        val mod = Module(name = "test", functions = listOf(
            IrFunction("mem", listOf(p), Type.I32, listOf(
                BasicBlock("entry", listOf(
                    Alloca(ref("%0", Type.OpaquePointer), Type.I32),
                    Load(ref("%1", Type.I32), p, Type.I32),
                    Store(Constant.I32(42), p),
                    Ret(ref("%1", Type.I32)),
                ))
            ))
        ))
        assertValid(mod)
    }

    @Test
    fun `valid function with conversions passes`() {
        val a = param32("a", 0)
        val mod = Module(name = "test", functions = listOf(
            IrFunction("conv", listOf(a), Type.I64, listOf(
                BasicBlock("entry", listOf(
                    ZExt(ref("%0", Type.I64), a, Type.I64),
                    Ret(ref("%0", Type.I64)),
                ))
            ))
        ))
        assertValid(mod)
    }

    @Test
    fun `valid function with select passes`() {
        val c = paramI1("c", 0)
        val a = param32("a", 1)
        val b = param32("b", 2)
        val mod = Module(name = "test", functions = listOf(
            IrFunction("sel", listOf(c, a, b), Type.I32, listOf(
                BasicBlock("entry", listOf(
                    Select(ref("%0"), c, a, b),
                    Ret(ref("%0")),
                ))
            ))
        ))
        assertValid(mod)
    }

    @Test
    fun `valid function with call passes`() {
        val funcType = Type.Function(listOf(Type.I32), Type.I32)
        val funcRef = FunctionRef("callee", funcType)
        val a = param32("a", 0)
        val mod = Module(name = "test", functions = listOf(
            IrFunction("caller", listOf(a), Type.I32, listOf(
                BasicBlock("entry", listOf(
                    Call(ref("%0"), funcRef, listOf(a), Type.I32),
                    Ret(ref("%0")),
                ))
            )),
            IrFunction("callee", listOf(param32("x", 0)), Type.I32,
                listOf(BasicBlock("entry", listOf(Ret(param32("x", 0)))))),
        ))
        assertValid(mod)
    }

    @Test
    fun `valid function with GEP passes`() {
        val p = paramPtr("p", 0)
        val mod = Module(name = "test", functions = listOf(
            IrFunction("gep", listOf(p), Type.OpaquePointer, listOf(
                BasicBlock("entry", listOf(
                    GetElementPtr(ref("%0", Type.OpaquePointer), Type.I32, p, listOf(Constant.I32(0))),
                    Ret(ref("%0", Type.OpaquePointer)),
                ))
            ))
        ))
        assertValid(mod)
    }

    @Test
    fun `valid function with unreachable passes`() {
        val mod = Module(name = "test", functions = listOf(
            IrFunction("unreachable", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(Unreachable())),
            ))
        ))
        assertValid(mod)
    }

    // Duplicate names

    @Test
    fun `duplicate function names fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(simpleFunc("f"), simpleFunc("f"))),
            "Duplicate function name"
        )
    }

    @Test
    fun `duplicate global names fails`() {
        assertInvalid(
            Module(name = "test", globals = listOf(
                Global("x", Type.I32), Global("x", Type.I64),
            )),
            "Duplicate global name"
        )
    }

    @Test
    fun `duplicate struct names fails`() {
        assertInvalid(
            Module(name = "test", structs = listOf(
                StructDef("S", listOf(Param("a", Type.I32))),
                StructDef("S", listOf(Param("b", Type.I64))),
            )),
            "Duplicate struct name"
        )
    }

    @Test
    fun `duplicate block labels fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(Ret(null))),
                    BasicBlock("entry", listOf(Ret(null))),
                ))
            )),
            "Duplicate block label"
        )
    }

    @Test
    fun `duplicate value definition fails`() {
        val a = param32("a", 0)
        val b = param32("b", 1)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(a, b), Type.I32, listOf(
                    BasicBlock("entry", listOf(
                        Add(ref("%0"), a, b),
                        Sub(ref("%0"), a, b),
                        Ret(ref("%0")),
                    ))
                ))
            )),
            "Duplicate value definition"
        )
    }

    // Missing terminators

    @Test
    fun `missing terminator fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        Add(ref("%0"), Constant.I32(1), Constant.I32(2)),
                    ))
                ))
            )),
            "does not end with a terminator"
        )
    }

    @Test
    fun `empty block fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", emptyList()),
                ))
            )),
            "is empty"
        )
    }

    @Test
    fun `terminator in middle of block fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        Ret(null),
                        Add(ref("%0"), Constant.I32(1), Constant.I32(2)),
                        Ret(null),
                    ))
                ))
            )),
            "Terminator in middle"
        )
    }

    @Test
    fun `br in middle of block fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        Br("entry"),
                        Ret(null),
                    )),
                ))
            )),
            "Terminator in middle"
        )
    }

    // External function validation

    @Test
    fun `external function with body fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void,
                    listOf(BasicBlock("entry", listOf(Ret(null)))),
                    isExternal = true)
            )),
            "must not have a body"
        )
    }

    @Test
    fun `non-external function without body fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, emptyList(), isExternal = false)
            )),
            "must have at least one basic block"
        )
    }

    // Integer binary op type mismatches

    @Test
    fun `add with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            Add(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "add operands have different types")
    }

    @Test
    fun `add with float operands fails`() {
        assertInvalid(singleInstrFunc(
            Add(ref("%0"), Constant.F32(1.0f), Constant.F32(2.0f)),
        ), "add operands must be integer type")
    }

    @Test
    fun `sub with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            Sub(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "sub operands have different types")
    }

    @Test
    fun `mul with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            Mul(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "mul operands have different types")
    }

    @Test
    fun `udiv with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            UDiv(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "udiv operands have different types")
    }

    @Test
    fun `sdiv with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            SDiv(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "sdiv operands have different types")
    }

    @Test
    fun `urem with float operands fails`() {
        assertInvalid(singleInstrFunc(
            URem(ref("%0"), Constant.F64(1.0), Constant.F64(2.0)),
        ), "urem operands must be integer type")
    }

    @Test
    fun `srem with float operands fails`() {
        assertInvalid(singleInstrFunc(
            SRem(ref("%0"), Constant.F32(1.0f), Constant.F32(2.0f)),
        ), "srem operands must be integer type")
    }

    @Test
    fun `and with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            And(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "and operands have different types")
    }

    @Test
    fun `or with float operands fails`() {
        assertInvalid(singleInstrFunc(
            Or(ref("%0"), Constant.F32(1.0f), Constant.F32(2.0f)),
        ), "or operands must be integer type")
    }

    @Test
    fun `xor with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            Xor(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "xor operands have different types")
    }

    @Test
    fun `shl with float operands fails`() {
        assertInvalid(singleInstrFunc(
            Shl(ref("%0"), Constant.F32(1.0f), Constant.F32(2.0f)),
        ), "shl operands must be integer type")
    }

    @Test
    fun `lshr with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            LShr(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "lshr operands have different types")
    }

    @Test
    fun `ashr with float operands fails`() {
        assertInvalid(singleInstrFunc(
            AShr(ref("%0"), Constant.F64(1.0), Constant.F64(2.0)),
        ), "ashr operands must be integer type")
    }

    @Test
    fun `rotl with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            RotateLeft(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "rotl operands have different types")
    }

    @Test
    fun `rotr with float operands fails`() {
        assertInvalid(singleInstrFunc(
            RotateRight(ref("%0"), Constant.F32(1.0f), Constant.F32(2.0f)),
        ), "rotr operands must be integer type")
    }

    // Integer unary ops

    @Test
    fun `neg with float operand fails`() {
        assertInvalid(singleInstrFunc(
            Neg(ref("%0"), Constant.F32(1.0f)),
        ), "neg operand must be integer type")
    }

    @Test
    fun `not with float operand fails`() {
        assertInvalid(singleInstrFunc(
            Not(ref("%0"), Constant.F32(1.0f)),
        ), "not operand must be integer type")
    }

    @Test
    fun `abs with float operand fails`() {
        assertInvalid(singleInstrFunc(
            Abs(ref("%0"), Constant.F64(1.0)),
        ), "abs operand must be integer type")
    }

    @Test
    fun `ctlz with float operand fails`() {
        assertInvalid(singleInstrFunc(
            Ctlz(ref("%0"), Constant.F32(1.0f)),
        ), "ctlz operand must be integer type")
    }

    @Test
    fun `cttz with float operand fails`() {
        assertInvalid(singleInstrFunc(
            Cttz(ref("%0"), Constant.F32(1.0f)),
        ), "cttz operand must be integer type")
    }

    @Test
    fun `ctpop with float operand fails`() {
        assertInvalid(singleInstrFunc(
            Ctpop(ref("%0"), Constant.F32(1.0f)),
        ), "ctpop operand must be integer type")
    }

    @Test
    fun `bswap with float operand fails`() {
        assertInvalid(singleInstrFunc(
            BSwap(ref("%0"), Constant.F32(1.0f)),
        ), "bswap operand must be integer type")
    }

    @Test
    fun `bitreverse with float operand fails`() {
        assertInvalid(singleInstrFunc(
            BitReverse(ref("%0"), Constant.F64(1.0)),
        ), "bitreverse operand must be integer type")
    }

    // Overflow-checked arithmetic type mismatches

    @Test
    fun `sadd overflow with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            SAddOverflow(ref("%0", Type.Struct(null, listOf(Type.I32, Type.I1))), Constant.I32(1), Constant.I64(2)),
        ), "sadd.overflow operands have different types")
    }

    @Test
    fun `uadd overflow with float operands fails`() {
        assertInvalid(singleInstrFunc(
            UAddOverflow(ref("%0", Type.Struct(null, listOf(Type.F32, Type.I1))), Constant.F32(1.0f), Constant.F32(2.0f)),
        ), "uadd.overflow operands must be integer type")
    }

    @Test
    fun `ssub overflow with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            SSubOverflow(ref("%0", Type.Struct(null, listOf(Type.I32, Type.I1))), Constant.I32(1), Constant.I64(2)),
        ), "ssub.overflow operands have different types")
    }

    @Test
    fun `usub overflow with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            USubOverflow(ref("%0", Type.Struct(null, listOf(Type.I32, Type.I1))), Constant.I32(1), Constant.I64(2)),
        ), "usub.overflow operands have different types")
    }

    @Test
    fun `smul overflow with float operands fails`() {
        assertInvalid(singleInstrFunc(
            SMulOverflow(ref("%0", Type.Struct(null, listOf(Type.F32, Type.I1))), Constant.F32(1.0f), Constant.F32(2.0f)),
        ), "smul.overflow operands must be integer type")
    }

    @Test
    fun `umul overflow with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            UMulOverflow(ref("%0", Type.Struct(null, listOf(Type.I32, Type.I1))), Constant.I32(1), Constant.I64(2)),
        ), "umul.overflow operands have different types")
    }

    // Saturating arithmetic type mismatches

    @Test
    fun `sadd sat with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            SAddSat(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "sadd.sat operands have different types")
    }

    @Test
    fun `uadd sat with float operands fails`() {
        assertInvalid(singleInstrFunc(
            UAddSat(ref("%0"), Constant.F32(1.0f), Constant.F32(2.0f)),
        ), "uadd.sat operands must be integer type")
    }

    @Test
    fun `ssub sat with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            SSubSat(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "ssub.sat operands have different types")
    }

    @Test
    fun `usub sat with float operands fails`() {
        assertInvalid(singleInstrFunc(
            USubSat(ref("%0"), Constant.F32(1.0f), Constant.F32(2.0f)),
        ), "usub.sat operands must be integer type")
    }

    // Min/max type mismatches

    @Test
    fun `smin with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            SMin(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "smin operands have different types")
    }

    @Test
    fun `smax with float operands fails`() {
        assertInvalid(singleInstrFunc(
            SMax(ref("%0"), Constant.F32(1.0f), Constant.F32(2.0f)),
        ), "smax operands must be integer type")
    }

    @Test
    fun `umin with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            UMin(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "umin operands have different types")
    }

    @Test
    fun `umax with float operands fails`() {
        assertInvalid(singleInstrFunc(
            UMax(ref("%0"), Constant.F64(1.0), Constant.F64(2.0)),
        ), "umax operands must be integer type")
    }

    // Float binary ops

    @Test
    fun `fadd with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            FAdd(ref("%0", Type.F32), Constant.F32(1.0f), Constant.F64(2.0)),
        ), "fadd operands have different types")
    }

    @Test
    fun `fadd with integer operands fails`() {
        assertInvalid(singleInstrFunc(
            FAdd(ref("%0"), Constant.I32(1), Constant.I32(2)),
        ), "fadd operands must be float type")
    }

    @Test
    fun `fsub with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            FSub(ref("%0", Type.F32), Constant.F32(1.0f), Constant.F64(2.0)),
        ), "fsub operands have different types")
    }

    @Test
    fun `fmul with integer operands fails`() {
        assertInvalid(singleInstrFunc(
            FMul(ref("%0"), Constant.I32(1), Constant.I32(2)),
        ), "fmul operands must be float type")
    }

    @Test
    fun `fdiv with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            FDiv(ref("%0", Type.F32), Constant.F32(1.0f), Constant.F64(2.0)),
        ), "fdiv operands have different types")
    }

    @Test
    fun `frem with integer operands fails`() {
        assertInvalid(singleInstrFunc(
            FRem(ref("%0"), Constant.I32(1), Constant.I32(2)),
        ), "frem operands must be float type")
    }

    @Test
    fun `fmin with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            FMin(ref("%0", Type.F32), Constant.F32(1.0f), Constant.F64(2.0)),
        ), "fmin operands have different types")
    }

    @Test
    fun `fmax with integer operands fails`() {
        assertInvalid(singleInstrFunc(
            FMax(ref("%0"), Constant.I32(1), Constant.I32(2)),
        ), "fmax operands must be float type")
    }

    @Test
    fun `copysign with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            CopySign(ref("%0", Type.F32), Constant.F32(1.0f), Constant.F64(2.0)),
        ), "copysign operands have different types")
    }

    @Test
    fun `copysign with integer operands fails`() {
        assertInvalid(singleInstrFunc(
            CopySign(ref("%0"), Constant.I32(1), Constant.I32(2)),
        ), "copysign operands must be float type")
    }

    // Float unary ops

    @Test
    fun `fneg with integer operand fails`() {
        assertInvalid(singleInstrFunc(
            FNeg(ref("%0"), Constant.I32(1)),
        ), "fneg operand must be float type")
    }

    @Test
    fun `fabs with integer operand fails`() {
        assertInvalid(singleInstrFunc(
            FAbs(ref("%0"), Constant.I32(1)),
        ), "fabs operand must be float type")
    }

    @Test
    fun `sqrt with integer operand fails`() {
        assertInvalid(singleInstrFunc(
            Sqrt(ref("%0"), Constant.I32(1)),
        ), "sqrt operand must be float type")
    }

    @Test
    fun `ceil with integer operand fails`() {
        assertInvalid(singleInstrFunc(
            Ceil(ref("%0"), Constant.I32(1)),
        ), "ceil operand must be float type")
    }

    @Test
    fun `floor with integer operand fails`() {
        assertInvalid(singleInstrFunc(
            Floor(ref("%0"), Constant.I32(1)),
        ), "floor operand must be float type")
    }

    @Test
    fun `round with integer operand fails`() {
        assertInvalid(singleInstrFunc(
            Round(ref("%0"), Constant.I32(1)),
        ), "round operand must be float type")
    }

    @Test
    fun `trunc with integer operand fails`() {
        assertInvalid(singleInstrFunc(
            Trunc(ref("%0"), Constant.I32(1)),
        ), "trunc operand must be float type")
    }

    @Test
    fun `fma with mismatched operand types fails`() {
        assertInvalid(singleInstrFunc(
            FMA(ref("%0", Type.F32), Constant.F32(1.0f), Constant.F32(2.0f), Constant.F64(3.0)),
        ), "fma operands must all be same type")
    }

    @Test
    fun `fma with integer operands fails`() {
        assertInvalid(singleInstrFunc(
            FMA(ref("%0"), Constant.I32(1), Constant.I32(2), Constant.I32(3)),
        ), "fma operand must be float type")
    }

    // Comparison

    @Test
    fun `icmp with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            ICmp(ref("%0", Type.I1), ICmpPredicate.EQ, Constant.I32(1), Constant.I64(2)),
        ), "icmp operands have different types")
    }

    @Test
    fun `icmp with non-i1 result fails`() {
        assertInvalid(singleInstrFunc(
            ICmp(ref("%0"), ICmpPredicate.EQ, Constant.I32(1), Constant.I32(2)),
        ), "icmp result must be i1")
    }

    @Test
    fun `fcmp with mismatched types fails`() {
        assertInvalid(singleInstrFunc(
            FCmp(ref("%0", Type.I1), FCmpPredicate.OEQ, Constant.F32(1.0f), Constant.F64(2.0)),
        ), "fcmp operands have different types")
    }

    @Test
    fun `fcmp with integer operands fails`() {
        assertInvalid(singleInstrFunc(
            FCmp(ref("%0", Type.I1), FCmpPredicate.OEQ, Constant.I32(1), Constant.I32(2)),
        ), "fcmp operands must be float type")
    }

    @Test
    fun `fcmp with non-i1 result fails`() {
        assertInvalid(singleInstrFunc(
            FCmp(ref("%0"), FCmpPredicate.OEQ, Constant.F32(1.0f), Constant.F32(2.0f)),
        ), "fcmp result must be i1")
    }

    // Memory operations

    @Test
    fun `load from non-pointer fails`() {
        assertInvalid(singleInstrFunc(
            Load(ref("%0"), Constant.I32(42), Type.I32),
        ), "load ptr operand must be pointer type")
    }

    @Test
    fun `load result type mismatch fails`() {
        assertInvalid(singleInstrFunc(
            Load(ref("%0"), Constant.NullPtr, Type.I64),
        ), "load result type")
    }

    @Test
    fun `store to non-pointer fails`() {
        assertInvalid(singleInstrFunc(
            Store(Constant.I32(42), Constant.I32(0)),
        ), "store ptr operand must be pointer type")
    }

    @Test
    fun `alloca with float numElements fails`() {
        assertInvalid(singleInstrFunc(
            Alloca(ref("%0", Type.OpaquePointer), Type.I32, Constant.F32(4.0f)),
        ), "alloca numElements must be integer type")
    }

    @Test
    fun `cmpxchg with non-pointer fails`() {
        assertInvalid(singleInstrFunc(
            CmpXchg(ref("%0", Type.Struct(null, listOf(Type.I32, Type.I1))), Constant.I32(0), Constant.I32(1), Constant.I32(2),
                AtomicOrdering.SEQ_CST, AtomicOrdering.ACQUIRE),
        ), "cmpxchg ptr operand must be pointer type")
    }

    @Test
    fun `cmpxchg with mismatched cmp and new types fails`() {
        assertInvalid(singleInstrFunc(
            CmpXchg(ref("%0", Type.Struct(null, listOf(Type.I32, Type.I1))), Constant.NullPtr, Constant.I32(1), Constant.I64(2),
                AtomicOrdering.SEQ_CST, AtomicOrdering.ACQUIRE),
        ), "cmpxchg compare and new values must have same type")
    }

    @Test
    fun `atomicrmw with non-pointer fails`() {
        assertInvalid(singleInstrFunc(
            AtomicRMW(ref("%0"), AtomicRMWOp.ADD, Constant.I32(0), Constant.I32(1), AtomicOrdering.SEQ_CST),
        ), "atomicrmw ptr operand must be pointer type")
    }

    @Test
    fun `memcpy with non-pointer dst fails`() {
        assertInvalid(singleInstrFunc(
            MemCpy(Constant.I32(0), Constant.NullPtr, Constant.I32(10)),
        ), "memcpy dst and src must be pointer types")
    }

    @Test
    fun `memset with non-pointer dst fails`() {
        assertInvalid(singleInstrFunc(
            MemSet(Constant.I32(0), Constant.I8(0), Constant.I32(10)),
        ), "memset dst must be pointer type")
    }

    @Test
    fun `memmove with non-pointer src fails`() {
        assertInvalid(singleInstrFunc(
            MemMove(Constant.NullPtr, Constant.I32(0), Constant.I32(10)),
        ), "memmove dst and src must be pointer types")
    }

    @Test
    fun `prefetch with non-pointer address fails`() {
        assertInvalid(singleInstrFunc(
            Prefetch(Constant.I32(0), 0, 3, 0),
        ), "prefetch address must be pointer type")
    }

    @Test
    fun `stackrestore with non-pointer fails`() {
        assertInvalid(singleInstrFunc(
            StackRestore(Constant.I32(0)),
        ), "stackrestore operand must be pointer type")
    }

    @Test
    fun `lifetime start with non-pointer fails`() {
        assertInvalid(singleInstrFunc(
            LifetimeStart(Constant.I32(0), 4),
        ), "lifetime.start operand must be pointer type")
    }

    @Test
    fun `lifetime end with non-pointer fails`() {
        assertInvalid(singleInstrFunc(
            LifetimeEnd(Constant.I32(0), 4),
        ), "lifetime.end operand must be pointer type")
    }

    // GEP

    @Test
    fun `gep with non-pointer base fails`() {
        assertInvalid(singleInstrFunc(
            GetElementPtr(ref("%0", Type.OpaquePointer), Type.I32, Constant.I32(0), listOf(Constant.I32(0))),
        ), "getelementptr ptr operand must be pointer type")
    }

    @Test
    fun `gep with no indices fails`() {
        assertInvalid(singleInstrFunc(
            GetElementPtr(ref("%0", Type.OpaquePointer), Type.I32, Constant.NullPtr, emptyList()),
        ), "getelementptr must have at least one index")
    }

    @Test
    fun `gep with non-integer index fails`() {
        assertInvalid(singleInstrFunc(
            GetElementPtr(ref("%0", Type.OpaquePointer), Type.I32, Constant.NullPtr, listOf(Constant.F32(0.0f))),
        ), "getelementptr index 0 must be integer type")
    }

    @Test
    fun `gep with mixed integer and float indices fails`() {
        assertInvalid(singleInstrFunc(
            GetElementPtr(ref("%0", Type.OpaquePointer), Type.I32, Constant.NullPtr,
                listOf(Constant.I32(0), Constant.F64(1.0))),
        ), "getelementptr index 1 must be integer type")
    }

    // Branch targets

    @Test
    fun `br to nonexistent block fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(Br("nonexistent"))),
                ))
            )),
            "br references undefined block"
        )
    }

    @Test
    fun `condBr with non-i1 condition fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(param32("a", 0)), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        CondBr(param32("a", 0), "t", "f"),
                    )),
                    BasicBlock("t", listOf(Ret(null))),
                    BasicBlock("f", listOf(Ret(null))),
                ))
            )),
            "condbr condition must be i1"
        )
    }

    @Test
    fun `condBr to nonexistent true target fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(paramI1("c", 0)), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        CondBr(paramI1("c", 0), "missing", "ok"),
                    )),
                    BasicBlock("ok", listOf(Ret(null))),
                ))
            )),
            "condbr true references undefined block"
        )
    }

    @Test
    fun `condBr to nonexistent false target fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(paramI1("c", 0)), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        CondBr(paramI1("c", 0), "ok", "missing"),
                    )),
                    BasicBlock("ok", listOf(Ret(null))),
                ))
            )),
            "condbr false references undefined block"
        )
    }

    @Test
    fun `switch with nonexistent default target fails`() {
        val a = param32("a", 0)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(a), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        Switch(a, "missing", emptyList()),
                    )),
                ))
            )),
            "switch default references undefined block"
        )
    }

    @Test
    fun `switch case type mismatch fails`() {
        val a = param32("a", 0)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(a), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        Switch(a, "default", listOf(Constant.I64(0) to "case0")),
                    )),
                    BasicBlock("case0", listOf(Ret(null))),
                    BasicBlock("default", listOf(Ret(null))),
                ))
            )),
            "switch case type"
        )
    }

    @Test
    fun `indirectbr with non-pointer address fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        IndirectBr(Constant.I32(0), listOf("entry")),
                    )),
                ))
            )),
            "indirectbr address must be pointer type"
        )
    }

    @Test
    fun `indirectbr to nonexistent target fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(paramPtr("p", 0)), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        IndirectBr(paramPtr("p", 0), listOf("missing")),
                    )),
                ))
            )),
            "indirectbr references undefined block"
        )
    }

    // Return type mismatches

    @Test
    fun `ret with value in void function fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(Ret(Constant.I32(42)))),
                ))
            )),
            "ret with value in void function"
        )
    }

    @Test
    fun `ret without value in non-void function fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.I32, listOf(
                    BasicBlock("entry", listOf(Ret(null))),
                ))
            )),
            "ret without value in non-void function"
        )
    }

    @Test
    fun `ret type mismatch fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.I32, listOf(
                    BasicBlock("entry", listOf(Ret(Constant.I64(42)))),
                ))
            )),
            "ret type"
        )
    }

    // Phi node validation

    @Test
    fun `phi with empty incoming fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        Phi(ref("%0"), emptyList()),
                        Ret(null),
                    )),
                ))
            )),
            "phi must have at least one incoming value"
        )
    }

    @Test
    fun `phi with mismatched incoming type fails`() {
        val c = paramI1("c", 0)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(c), Type.Void, listOf(
                    BasicBlock("entry", listOf(CondBr(c, "left", "right"))),
                    BasicBlock("left", listOf(Br("merge"))),
                    BasicBlock("right", listOf(Br("merge"))),
                    BasicBlock("merge", listOf(
                        Phi(ref("%0"), listOf(Constant.I32(1) to "left", Constant.I64(2) to "right")),
                        Ret(null),
                    )),
                ))
            )),
            "phi incoming value type"
        )
    }

    @Test
    fun `phi referencing nonexistent block fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        Phi(ref("%0"), listOf(Constant.I32(1) to "nonexistent")),
                        Ret(null),
                    )),
                ))
            )),
            "phi references undefined block"
        )
    }

    @Test
    fun `phi after non-phi instruction fails`() {
        val c = paramI1("c", 0)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(c), Type.Void, listOf(
                    BasicBlock("entry", listOf(CondBr(c, "left", "right"))),
                    BasicBlock("left", listOf(Br("merge"))),
                    BasicBlock("right", listOf(Br("merge"))),
                    BasicBlock("merge", listOf(
                        Add(ref("%0"), Constant.I32(1), Constant.I32(2)),
                        Phi(ref("%1"), listOf(Constant.I32(1) to "left", Constant.I32(2) to "right")),
                        Ret(null),
                    )),
                ))
            )),
            "Phi node after non-phi instruction"
        )
    }

    @Test
    fun `phi with non-predecessor incoming block fails`() {
        val c = paramI1("c", 0)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(c), Type.Void, listOf(
                    BasicBlock("entry", listOf(CondBr(c, "left", "right"))),
                    BasicBlock("left", listOf(Br("merge"))),
                    BasicBlock("right", listOf(Br("merge"))),
                    BasicBlock("other", listOf(Br("merge"))),
                    BasicBlock("merge", listOf(
                        Phi(ref("%0"), listOf(Constant.I32(1) to "left", Constant.I32(2) to "other")),
                        Ret(null),
                    )),
                ))
            )),
            "missing entry for predecessor"
        )
    }

    @Test
    fun `phi with duplicate predecessor entry fails`() {
        val c = paramI1("c", 0)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(c), Type.Void, listOf(
                    BasicBlock("entry", listOf(CondBr(c, "left", "right"))),
                    BasicBlock("left", listOf(Br("merge"))),
                    BasicBlock("right", listOf(Br("merge"))),
                    BasicBlock("merge", listOf(
                        Phi(ref("%0"), listOf(Constant.I32(1) to "left", Constant.I32(3) to "left", Constant.I32(2) to "right")),
                        Ret(null),
                    )),
                ))
            )),
            "has 2 entries for predecessor %left"
        )
    }

    // Conversion type errors

    @Test
    fun `inttrunc with float source fails`() {
        assertInvalid(singleInstrFunc(
            IntTrunc(ref("%0", Type.I16), Constant.F32(1.0f), Type.I16),
        ), "inttrunc source must be integer type")
    }

    @Test
    fun `inttrunc with float target fails`() {
        assertInvalid(singleInstrFunc(
            IntTrunc(ref("%0", Type.F32), Constant.I32(1), Type.F32),
        ), "inttrunc target must be integer type")
    }

    @Test
    fun `zext with float source fails`() {
        assertInvalid(singleInstrFunc(
            ZExt(ref("%0", Type.I64), Constant.F32(1.0f), Type.I64),
        ), "zext source must be integer type")
    }

    @Test
    fun `sext with float target fails`() {
        assertInvalid(singleInstrFunc(
            SExt(ref("%0", Type.F64), Constant.I32(1), Type.F64),
        ), "sext target must be integer type")
    }

    @Test
    fun `fptrunc with integer source fails`() {
        assertInvalid(singleInstrFunc(
            FPTrunc(ref("%0", Type.F32), Constant.I64(1), Type.F32),
        ), "fptrunc source must be float type")
    }

    @Test
    fun `fptrunc with integer target fails`() {
        assertInvalid(singleInstrFunc(
            FPTrunc(ref("%0"), Constant.F64(1.0), Type.I32),
        ), "fptrunc target must be float type")
    }

    @Test
    fun `fpext with integer source fails`() {
        assertInvalid(singleInstrFunc(
            FPExt(ref("%0", Type.F64), Constant.I32(1), Type.F64),
        ), "fpext source must be float type")
    }

    @Test
    fun `fpext with integer target fails`() {
        assertInvalid(singleInstrFunc(
            FPExt(ref("%0"), Constant.F32(1.0f), Type.I64),
        ), "fpext target must be float type")
    }

    @Test
    fun `fptoui with integer source fails`() {
        assertInvalid(singleInstrFunc(
            FPToUI(ref("%0"), Constant.I32(1), Type.I32),
        ), "fptoui source must be float type")
    }

    @Test
    fun `fptoui with float target fails`() {
        assertInvalid(singleInstrFunc(
            FPToUI(ref("%0", Type.F32), Constant.F32(1.0f), Type.F32),
        ), "fptoui target must be integer type")
    }

    @Test
    fun `fptosi with integer source fails`() {
        assertInvalid(singleInstrFunc(
            FPToSI(ref("%0"), Constant.I32(1), Type.I32),
        ), "fptosi source must be float type")
    }

    @Test
    fun `uitofp with float source fails`() {
        assertInvalid(singleInstrFunc(
            UIToFP(ref("%0", Type.F32), Constant.F32(1.0f), Type.F32),
        ), "uitofp source must be integer type")
    }

    @Test
    fun `uitofp with integer target fails`() {
        assertInvalid(singleInstrFunc(
            UIToFP(ref("%0"), Constant.I32(1), Type.I32),
        ), "uitofp target must be float type")
    }

    @Test
    fun `sitofp with float source fails`() {
        assertInvalid(singleInstrFunc(
            SIToFP(ref("%0", Type.F32), Constant.F32(1.0f), Type.F32),
        ), "sitofp source must be integer type")
    }

    @Test
    fun `ptrtoint with non-pointer source fails`() {
        assertInvalid(singleInstrFunc(
            PtrToInt(ref("%0"), Constant.I32(1), Type.I64),
        ), "ptrtoint source must be pointer type")
    }

    @Test
    fun `ptrtoint with non-integer target fails`() {
        assertInvalid(singleInstrFunc(
            PtrToInt(ref("%0", Type.F64), Constant.NullPtr, Type.F64),
        ), "ptrtoint target must be integer type")
    }

    @Test
    fun `inttoptr with non-integer source fails`() {
        assertInvalid(singleInstrFunc(
            IntToPtr(ref("%0", Type.OpaquePointer), Constant.F32(1.0f), Type.OpaquePointer),
        ), "inttoptr source must be integer type")
    }

    @Test
    fun `inttoptr with non-pointer target fails`() {
        assertInvalid(singleInstrFunc(
            IntToPtr(ref("%0"), Constant.I64(1), Type.I32),
        ), "inttoptr target must be pointer type")
    }

    // BitCast / AddrSpaceCast

    @Test
    fun `bitcast between incompatible types fails`() {
        assertInvalid(singleInstrFunc(
            BitCast(ref("%0", Type.F32), Constant.I32(1), Type.F32),
        ), "bitcast between incompatible type categories")
    }

    @Test
    fun `addrspacecast with non-pointer source fails`() {
        assertInvalid(singleInstrFunc(
            AddrSpaceCast(ref("%0", Type.Pointer(Type.I8, 1)), Constant.I32(1), Type.Pointer(Type.I8, 1)),
        ), "addrspacecast source must be pointer type")
    }

    @Test
    fun `addrspacecast with non-pointer target fails`() {
        assertInvalid(singleInstrFunc(
            AddrSpaceCast(ref("%0"), Constant.NullPtr, Type.I64),
        ), "addrspacecast target must be pointer type")
    }

    // Vector operations

    @Test
    fun `extractelement from non-vector fails`() {
        assertInvalid(singleInstrFunc(
            ExtractElement(ref("%0"), Constant.I32(1), Constant.I32(0)),
        ), "extractelement operand must be vector type")
    }

    @Test
    fun `insertelement into non-vector fails`() {
        assertInvalid(singleInstrFunc(
            InsertElement(ref("%0"), Constant.I32(1), Constant.I32(2), Constant.I32(0)),
        ), "insertelement operand must be vector type")
    }

    @Test
    fun `shufflevector with non-vector fails`() {
        assertInvalid(singleInstrFunc(
            ShuffleVector(ref("%0", Type.Vector(Type.I32, 4)), Constant.I32(1), Constant.I32(2), listOf(0, 1)),
        ), "shufflevector operand must be vector type")
    }

    @Test
    fun `shufflevector with mismatched vector types fails`() {
        val v1 = Constant.VectorConst(Type.Vector(Type.I32, 2), listOf(Constant.I32(1), Constant.I32(2)))
        val v2 = Constant.VectorConst(Type.Vector(Type.I64, 2), listOf(Constant.I64(1), Constant.I64(2)))
        assertInvalid(singleInstrFunc(
            ShuffleVector(ref("%0", Type.Vector(Type.I32, 2)), v1, v2, listOf(0, 1)),
        ), "shufflevector operands must have same type")
    }

    @Test
    fun `splat scalar type mismatch fails`() {
        assertInvalid(singleInstrFunc(
            Splat(ref("%0", Type.Vector(Type.I32, 4)), Constant.I64(1), Type.Vector(Type.I32, 4)),
        ), "splat scalar type must match vector element type")
    }

    @Test
    fun `vector reduce with non-vector fails`() {
        assertInvalid(singleInstrFunc(
            VectorReduce(ref("%0"), VectorReduceOp.ADD, Constant.I32(1)),
        ), "vector.reduce operand must be vector type")
    }

    // Select

    @Test
    fun `select with non-i1 condition fails`() {
        assertInvalid(singleInstrFunc(
            Select(ref("%0"), Constant.I32(1), Constant.I32(2), Constant.I32(3)),
        ), "select condition must be i1")
    }

    @Test
    fun `select with mismatched true false types fails`() {
        assertInvalid(singleInstrFunc(
            Select(ref("%0"), Constant.I1(true), Constant.I32(2), Constant.I64(3)),
        ), "select true/false values have different types")
    }

    @Test
    fun `select result type mismatch fails`() {
        assertInvalid(singleInstrFunc(
            Select(ref("%0", Type.I64), Constant.I1(true), Constant.I32(2), Constant.I32(3)),
        ), "select result type")
    }

    // Call validation

    @Test
    fun `call with wrong arg count fails`() {
        val funcType = Type.Function(listOf(Type.I32, Type.I32), Type.I32)
        assertInvalid(singleInstrFunc(
            Call(ref("%0"), FunctionRef("callee", funcType), listOf(Constant.I32(1)), Type.I32),
        ), "call arg count")
    }

    @Test
    fun `call with wrong arg type fails`() {
        val funcType = Type.Function(listOf(Type.I32), Type.I32)
        assertInvalid(singleInstrFunc(
            Call(ref("%0"), FunctionRef("callee", funcType), listOf(Constant.I64(1)), Type.I32),
        ), "call arg 0 type")
    }

    @Test
    fun `vararg call with fewer args than required fails`() {
        val funcType = Type.Function(listOf(Type.I32, Type.I32), Type.I32, vararg = true)
        assertInvalid(singleInstrFunc(
            Call(ref("%0"), FunctionRef("callee", funcType), listOf(Constant.I32(1)), Type.I32),
        ), "call has fewer args than required params")
    }

    @Test
    fun `invoke with nonexistent normal dest fails`() {
        val funcType = Type.Function(emptyList(), Type.Void)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        Invoke(null, FunctionRef("callee", funcType), emptyList(), Type.Void, "missing", "unwind"),
                    )),
                    BasicBlock("unwind", listOf(Ret(null))),
                ))
            )),
            "invoke normal references undefined block"
        )
    }

    @Test
    fun `invoke with nonexistent unwind dest fails`() {
        val funcType = Type.Function(emptyList(), Type.Void)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        Invoke(null, FunctionRef("callee", funcType), emptyList(), Type.Void, "normal", "missing"),
                    )),
                    BasicBlock("normal", listOf(Ret(null))),
                ))
            )),
            "invoke unwind references undefined block"
        )
    }

    // Expect and assume

    @Test
    fun `expect type mismatch fails`() {
        assertInvalid(singleInstrFunc(
            Expect(ref("%0"), Constant.I32(1), Constant.I64(2)),
        ), "expect value type")
    }

    @Test
    fun `assume with non-i1 condition fails`() {
        assertInvalid(singleInstrFunc(
            Assume(Constant.I32(1)),
        ), "assume condition must be i1")
    }

    // High-level array ops

    @Test
    fun `newarray with float size fails`() {
        assertInvalid(singleInstrFunc(
            NewArray(ref("%0", Type.Reference(Type.Array(Type.I32, 0))), Type.I32, Constant.F32(10.0f)),
        ), "newarray size must be integer type")
    }

    @Test
    fun `newmultiarray with float dimension fails`() {
        assertInvalid(singleInstrFunc(
            NewMultiArray(ref("%0", Type.Reference(Type.Array(Type.I32, 0))), Type.I32,
                listOf(Constant.I32(3), Constant.F64(4.0))),
        ), "newmultiarray dimension 1 must be integer type")
    }

    @Test
    fun `arrayget with float index fails`() {
        assertInvalid(singleInstrFunc(
            ArrayGet(ref("%0"), Constant.NullRef, Constant.F32(0.0f), Type.I32),
        ), "arrayget index must be integer type")
    }

    @Test
    fun `arrayset with float index fails`() {
        assertInvalid(singleInstrFunc(
            ArraySet(Constant.NullRef, Constant.F32(0.0f), Constant.I32(42), Type.I32),
        ), "arrayset index must be integer type")
    }

    // Global constant type mismatch

    @Test
    fun `global initializer type mismatch fails`() {
        assertInvalid(
            Module(name = "test", globals = listOf(
                Global("x", Type.I32, Constant.I64(42), isConstant = true),
            )),
            "Constant type"
        )
    }

    @Test
    fun `global with zeroinitializer passes`() {
        assertValid(
            Module(name = "test", globals = listOf(
                Global("x", Type.I32, Constant.ZeroInitializer(Type.I64), isConstant = true),
            ))
        )
    }

    @Test
    fun `global with undef passes regardless of type`() {
        assertValid(
            Module(name = "test", globals = listOf(
                Global("x", Type.I32, Constant.Undef(Type.I64), isConstant = false),
            ))
        )
    }

    // SSA dominance

    @Test
    fun `use before def in same block fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.I32, listOf(
                    BasicBlock("entry", listOf(
                        Add(ref("%1"), ref("%0"), Constant.I32(1)),
                        Add(ref("%0"), Constant.I32(1), Constant.I32(2)),
                        Ret(ref("%1")),
                    ))
                ))
            )),
            "used before definition"
        )
    }

    @Test
    fun `use of undefined value fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.I32, listOf(
                    BasicBlock("entry", listOf(
                        Add(ref("%0"), ref("%undef", Type.I32), Constant.I32(1)),
                        Ret(ref("%0")),
                    ))
                ))
            )),
            "undefined value"
        )
    }

    @Test
    fun `value from non-dominating block fails`() {
        val c = paramI1("c", 0)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", listOf(c), Type.I32, listOf(
                    BasicBlock("entry", listOf(
                        CondBr(c, "left", "right"),
                    )),
                    BasicBlock("left", listOf(
                        Add(ref("%x"), Constant.I32(1), Constant.I32(2)),
                        Br("merge"),
                    )),
                    BasicBlock("right", listOf(
                        Add(ref("%y"), ref("%x"), Constant.I32(3)),
                        Br("merge"),
                    )),
                    BasicBlock("merge", listOf(
                        Ret(Constant.I32(0)),
                    )),
                ))
            )),
            "does not dominate use"
        )
    }

    // EH block references

    @Test
    fun `catchswitch with nonexistent handler fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        CatchSwitch(ref("%0", Type.Token), null, listOf("missing"), null),
                    )),
                ))
            )),
            "catchswitch handler references undefined block"
        )
    }

    @Test
    fun `catchret to nonexistent block fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        CatchRet(ref("%tok", Type.Token), "missing"),
                    )),
                ))
            )),
            "catchret references undefined block"
        )
    }

    @Test
    fun `cleanupret with nonexistent unwind dest fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        CleanupRet(ref("%tok", Type.Token), "missing"),
                    )),
                ))
            )),
            "cleanupret unwind references undefined block"
        )
    }

    // TagSwitch block references

    @Test
    fun `tagswitch with nonexistent case target fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        TagSwitch(Constant.I32(0), listOf("A" to "missing"), null),
                    )),
                ))
            )),
            "tagswitch case references undefined block"
        )
    }

    // Multiple errors

    @Test
    fun `module with multiple errors collects all`() {
        val mod = Module(name = "test", functions = listOf(
            IrFunction("f", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    Add(ref("%0"), Constant.I32(1), Constant.I64(2)),
                    FAdd(ref("%1", Type.F32), Constant.I32(1), Constant.I32(2)),
                    Ret(Constant.I32(42)),
                )),
            )),
        ))
        val result = verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.size >= 3, "Expected at least 3 errors, got ${result.errors.size}")
    }

    @Test
    fun `verification result toString for valid`() {
        val result = VerificationResult(emptyList())
        assertTrue(result.isValid)
        assertTrue(result.toString().contains("passed"))
    }

    @Test
    fun `verification result toString for invalid`() {
        val result = VerificationResult(listOf(VerificationError("test error")))
        assertFalse(result.isValid)
        assertTrue(result.toString().contains("test error"))
        assertTrue(result.toString().contains("1 error"))
    }

    // Companion method tests

    @Test
    fun `isIntegerType recognizes all integer types`() {
        assertTrue(IrVerifier.isIntegerType(Type.I1))
        assertTrue(IrVerifier.isIntegerType(Type.I8))
        assertTrue(IrVerifier.isIntegerType(Type.I16))
        assertTrue(IrVerifier.isIntegerType(Type.I32))
        assertTrue(IrVerifier.isIntegerType(Type.I64))
        assertTrue(IrVerifier.isIntegerType(Type.I128))
        assertTrue(IrVerifier.isIntegerType(Type.IntN(256)))
        assertFalse(IrVerifier.isIntegerType(Type.F32))
        assertFalse(IrVerifier.isIntegerType(Type.OpaquePointer))
        assertFalse(IrVerifier.isIntegerType(Type.Void))
    }

    @Test
    fun `isFloatType recognizes all float types`() {
        assertTrue(IrVerifier.isFloatType(Type.F16))
        assertTrue(IrVerifier.isFloatType(Type.BF16))
        assertTrue(IrVerifier.isFloatType(Type.F32))
        assertTrue(IrVerifier.isFloatType(Type.F64))
        assertTrue(IrVerifier.isFloatType(Type.F80))
        assertTrue(IrVerifier.isFloatType(Type.F128))
        assertFalse(IrVerifier.isFloatType(Type.I32))
        assertFalse(IrVerifier.isFloatType(Type.OpaquePointer))
    }

    @Test
    fun `isPointerType recognizes pointer types`() {
        assertTrue(IrVerifier.isPointerType(Type.OpaquePointer))
        assertTrue(IrVerifier.isPointerType(Type.Pointer(Type.I8, 1)))
        assertTrue(IrVerifier.isPointerType(Type.OpaquePointer))
        assertFalse(IrVerifier.isPointerType(Type.I64))
        assertFalse(IrVerifier.isPointerType(Type.F32))
    }

    @Test
    fun `isTerminator recognizes all terminators`() {
        assertTrue(IrVerifier.isTerminator(Ret(null)))
        assertTrue(IrVerifier.isTerminator(Br("label")))
        assertTrue(IrVerifier.isTerminator(CondBr(Constant.I1(true), "t", "f")))
        assertTrue(IrVerifier.isTerminator(Switch(Constant.I32(0), "default", emptyList())))
        assertTrue(IrVerifier.isTerminator(Unreachable()))
        assertTrue(IrVerifier.isTerminator(Trap()))
        assertFalse(IrVerifier.isTerminator(Add(ref("%0"), Constant.I32(1), Constant.I32(2))))
        assertFalse(IrVerifier.isTerminator(Load(ref("%0"), Constant.NullPtr, Type.I32)))
    }

    // Valid complex module passes

    @Test
    fun `complex module with structs globals and functions passes`() {
        val mod = module("complex") {
            struct("Point", listOf(Param("x", Type.F64), Param("y", Type.F64)))
            global("origin", Type.Struct("Point", listOf(Type.F64, Type.F64)), isConstant = true,
                initializer = Constant.StructConst(Type.Struct("Point", listOf(Type.F64, Type.F64)), listOf(Constant.F64(0.0), Constant.F64(0.0))))
            function("identity", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") { ret(param(0)) }
            }
            function("sum", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val r = add(param(0), param(1))
                    ret(r)
                }
            }
        }
        assertValid(mod)
    }

    @Test
    fun `valid function with i8 i16 i64 operations passes`() {
        val mod = Module(name = "test", functions = listOf(
            IrFunction("f", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    Add(ref("%0", Type.I8), Constant.I8(1), Constant.I8(2)),
                    Sub(ref("%1", Type.I16), Constant.I16(10), Constant.I16(5)),
                    Mul(ref("%2", Type.I64), Constant.I64(3), Constant.I64(4)),
                    Ret(null),
                ))
            ))
        ))
        assertValid(mod)
    }

    @Test
    fun `valid function with f32 and f64 operations passes`() {
        val mod = Module(name = "test", functions = listOf(
            IrFunction("f", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    FAdd(ref("%0", Type.F32), Constant.F32(1.0f), Constant.F32(2.0f)),
                    FSub(ref("%1", Type.F64), Constant.F64(3.0), Constant.F64(4.0)),
                    FMul(ref("%2", Type.F32), Constant.F32(5.0f), Constant.F32(6.0f)),
                    FDiv(ref("%3", Type.F64), Constant.F64(7.0), Constant.F64(8.0)),
                    Ret(null),
                ))
            ))
        ))
        assertValid(mod)
    }

    @Test
    fun `valid function with bitwise ops passes`() {
        val mod = Module(name = "test", functions = listOf(
            IrFunction("f", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    And(ref("%0"), Constant.I32(0xFF), Constant.I32(0x0F)),
                    Or(ref("%1"), Constant.I32(0xF0), Constant.I32(0x0F)),
                    Xor(ref("%2"), Constant.I32(0xFF), Constant.I32(0x0F)),
                    Shl(ref("%3"), Constant.I32(1), Constant.I32(4)),
                    LShr(ref("%4"), Constant.I32(16), Constant.I32(2)),
                    AShr(ref("%5"), Constant.I32(-8), Constant.I32(1)),
                    Not(ref("%6"), Constant.I32(0)),
                    Ret(null),
                ))
            ))
        ))
        assertValid(mod)
    }

    @Test
    fun `valid function with all conversion types passes`() {
        val mod = Module(name = "test", functions = listOf(
            IrFunction("f", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    ZExt(ref("%0", Type.I64), Constant.I32(1), Type.I64),
                    SExt(ref("%1", Type.I64), Constant.I32(-1), Type.I64),
                    IntTrunc(ref("%2", Type.I16), Constant.I32(1000), Type.I16),
                    FPExt(ref("%3", Type.F64), Constant.F32(1.0f), Type.F64),
                    FPTrunc(ref("%4", Type.F32), Constant.F64(1.0), Type.F32),
                    FPToUI(ref("%5"), Constant.F32(1.0f), Type.I32),
                    FPToSI(ref("%6"), Constant.F64(1.0), Type.I32),
                    UIToFP(ref("%7", Type.F32), Constant.I32(1), Type.F32),
                    SIToFP(ref("%8", Type.F64), Constant.I32(-1), Type.F64),
                    PtrToInt(ref("%9", Type.I64), Constant.NullPtr, Type.I64),
                    IntToPtr(ref("%10", Type.OpaquePointer), Constant.I64(0), Type.OpaquePointer),
                    Ret(null),
                ))
            ))
        ))
        assertValid(mod)
    }

    @Test
    fun `valid vector operations pass`() {
        val vecType = Type.Vector(Type.I32, 4)
        val vec = Constant.VectorConst(vecType, listOf(Constant.I32(1), Constant.I32(2), Constant.I32(3), Constant.I32(4)))
        val mod = Module(name = "test", functions = listOf(
            IrFunction("f", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    ExtractElement(ref("%0"), vec, Constant.I32(0)),
                    InsertElement(ref("%1", vecType), vec, Constant.I32(99), Constant.I32(0)),
                    ShuffleVector(ref("%2", vecType), vec, vec, listOf(0, 1, 2, 3)),
                    Splat(ref("%3", vecType), Constant.I32(42), vecType),
                    VectorReduce(ref("%4"), VectorReduceOp.ADD, vec),
                    Ret(null),
                ))
            ))
        ))
        assertValid(mod)
    }

    @Test
    fun `callbr with nonexistent fallthrough fails`() {
        val funcType = Type.Function(emptyList(), Type.Void)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        CallBr(null, FunctionRef("callee", funcType), emptyList(), Type.Void, "missing", emptyList()),
                    )),
                ))
            )),
            "callbr fallthrough references undefined block"
        )
    }

    @Test
    fun `callbr with nonexistent indirect dest fails`() {
        val funcType = Type.Function(emptyList(), Type.Void)
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        CallBr(null, FunctionRef("callee", funcType), emptyList(), Type.Void, "ft", listOf("missing")),
                    )),
                    BasicBlock("ft", listOf(Ret(null))),
                ))
            )),
            "callbr indirect references undefined block"
        )
    }

    @Test
    fun `trycatch with nonexistent try block fails`() {
        assertInvalid(
            Module(name = "test", functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(
                    BasicBlock("entry", listOf(
                        TryCatchRegion("missing", listOf(CatchHandler(Type.ClassRef("Exception"), "handler"))),
                        Ret(null),
                    )),
                    BasicBlock("handler", listOf(Ret(null))),
                ))
            )),
            "trycatch try references undefined block"
        )
    }

    @Test
    fun `valid function with debug instructions between phis passes`() {
        val c = paramI1("c", 0)
        val mod = Module(name = "test", functions = listOf(
            IrFunction("f", listOf(c), Type.I32, listOf(
                BasicBlock("entry", listOf(CondBr(c, "left", "right"))),
                BasicBlock("left", listOf(Br("merge"))),
                BasicBlock("right", listOf(Br("merge"))),
                BasicBlock("merge", listOf(
                    Phi(ref("%0"), listOf(Constant.I32(1) to "left", Constant.I32(2) to "right")),
                    DebugLoc(1, 1, "test.kt"),
                    Phi(ref("%1"), listOf(Constant.I32(3) to "left", Constant.I32(4) to "right")),
                    Add(ref("%2"), ref("%0"), ref("%1")),
                    Ret(ref("%2")),
                )),
            ))
        ))
        assertValid(mod)
    }

    // Instance-level verify

    @Test
    fun `instance verifier is reusable`() {
        val verifier = IrVerifier()
        val good = Module(name = "good", functions = listOf(simpleFunc("f1")))
        val bad = Module(name = "bad", functions = listOf(
            IrFunction("f", emptyList(), Type.Void, emptyList(), isExternal = false)
        ))

        assertTrue(verifier.verify(good).isValid)
        assertFalse(verifier.verify(bad).isValid)
        assertTrue(verifier.verify(good).isValid)
    }
}
