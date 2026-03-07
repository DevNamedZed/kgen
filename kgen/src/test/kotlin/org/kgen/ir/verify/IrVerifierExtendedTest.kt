package org.kgen.ir.verify

import org.kgen.ir.*
import org.kgen.ir.Type
import org.kgen.ir.Type.Companion.i32
import org.kgen.ir.Type.Companion.i64
import org.kgen.ir.Type.Companion.f32
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IrVerifierExtendedTest {

    // 1. Phi with different incoming types
    @Test
    fun `phi with mismatched incoming types from different branches fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "phi_type_mismatch",
                    params = listOf(Parameter("cond", Type.I1, 0)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.CondBr(Parameter("cond", Type.I1, 0), "left", "right"),
                        )),
                        BasicBlock("left", listOf(
                            Instruction.Br("merge"),
                        )),
                        BasicBlock("right", listOf(
                            Instruction.Br("merge"),
                        )),
                        BasicBlock("merge", listOf(
                            Instruction.Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(i32(1), "left"),
                                Pair(i64(2), "right"),
                            )),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("phi incoming value type") }, result.toString())
    }

    // 2. CondBr to non-existent block (true target)
    @Test
    fun `condbr true target references undefined block fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_condbr_target",
                    params = listOf(Parameter("cond", Type.I1, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.CondBr(Parameter("cond", Type.I1, 0), "ghost", "fallback"),
                        )),
                        BasicBlock("fallback", listOf(Instruction.Ret(null))),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("undefined block") && it.message.contains("ghost") }, result.toString())
    }

    // 3. Missing terminator in middle block (two blocks, first has no terminator)
    @Test
    fun `missing terminator in non-final block fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "no_term_mid",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Add(InstructionRef("%0", Type.I32), i32(1), i32(2)),
                        )),
                        BasicBlock("next", listOf(
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("does not end with a terminator") && it.message.contains("entry") }, result.toString())
    }

    // 4. Call with wrong number of arguments
    @Test
    fun `call with too many arguments fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_call_argc",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Call(
                                dest = null,
                                function = FunctionRef("target", Type.Function(listOf(Type.I32), Type.Void)),
                                args = listOf(i32(1), i32(2)),
                                returnType = Type.Void,
                            ),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("arg count") }, result.toString())
    }

    // 5. Return value type does not match function return type
    @Test
    fun `return i64 from i32 function fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "ret_mismatch",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Ret(i64(42)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("ret type") }, result.toString())
    }

    // 6. Store to non-pointer type
    @Test
    fun `store to non-pointer type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_store",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Store(i32(42), i32(0)),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("store ptr operand must be pointer") }, result.toString())
    }

    // 7. GEP on non-pointer base
    @Test
    fun `gep with non-pointer base fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_gep_base",
                    params = emptyList(),
                    returnType = Type.OpaquePointer,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.GetElementPtr(
                                InstructionRef("%0", Type.OpaquePointer),
                                Type.I32,
                                i32(0),
                                listOf(i32(0)),
                            ),
                            Instruction.Ret(InstructionRef("%0", Type.OpaquePointer)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("getelementptr ptr operand must be pointer") }, result.toString())
    }

    // 8. Duplicate block labels (already tested in original, add variant with 3 blocks)
    @Test
    fun `duplicate block labels across three blocks fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "dup_labels",
                    params = listOf(Parameter("c", Type.I1, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.CondBr(Parameter("c", Type.I1, 0), "body", "body"),
                        )),
                        BasicBlock("body", listOf(Instruction.Ret(null))),
                        BasicBlock("body", listOf(Instruction.Ret(null))),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("Duplicate block label") }, result.toString())
    }

    // 9. ICmp with incompatible types
    @Test
    fun `icmp with different operand types fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_icmp",
                    params = emptyList(),
                    returnType = Type.I1,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.ICmp(InstructionRef("%0", Type.I1), ICmpPredicate.EQ, i32(1), i64(2)),
                            Instruction.Ret(InstructionRef("%0", Type.I1)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("icmp operands have different types") }, result.toString())
    }

    // 10. FCmp with incompatible types
    @Test
    fun `fcmp with different operand types fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_fcmp_types",
                    params = listOf(Parameter("a", Type.F32, 0), Parameter("b", Type.F64, 1)),
                    returnType = Type.I1,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.FCmp(InstructionRef("%0", Type.I1), FCmpPredicate.OEQ,
                                Parameter("a", Type.F32, 0), Parameter("b", Type.F64, 1)),
                            Instruction.Ret(InstructionRef("%0", Type.I1)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("fcmp operands have different types") }, result.toString())
    }

    // 11. IntTrunc to larger type (invalid: truncating i32 to i64)
    @Test
    fun `inttrunc from float type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_inttrunc",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.IntTrunc(InstructionRef("%0", Type.I32), f32(1.0f), Type.I32),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("inttrunc source must be integer type") }, result.toString())
    }

    // 12. ZExt from float type (invalid source)
    @Test
    fun `zext target must be integer type`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_zext_target",
                    params = emptyList(),
                    returnType = Type.F64,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.ZExt(InstructionRef("%0", Type.F64), i32(1), Type.F64),
                            Instruction.Ret(InstructionRef("%0", Type.F64)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("zext target must be integer type") }, result.toString())
    }

    // 13. SExt from float type fails
    @Test
    fun `sext from float type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_sext",
                    params = emptyList(),
                    returnType = Type.I64,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.SExt(InstructionRef("%0", Type.I64), f32(1.0f), Type.I64),
                            Instruction.Ret(InstructionRef("%0", Type.I64)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("sext source must be integer type") }, result.toString())
    }

    // 14. Non-external function with no blocks
    @Test
    fun `non-external function with zero blocks fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction("empty", emptyList(), Type.I32, emptyList(), isExternal = false),
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("must have at least one basic block") }, result.toString())
    }

    // 15. Vararg function call with too few args
    @Test
    fun `vararg call with fewer args than required params fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_vararg",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Call(
                                dest = null,
                                function = FunctionRef("printf", Type.Function(listOf(Type.OpaquePointer, Type.I32), Type.Void, vararg = true)),
                                args = listOf(),
                                returnType = Type.Void,
                            ),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("fewer args than required params") }, result.toString())
    }

    // 16. Terminator in middle of block (unreachable after br)
    @Test
    fun `unreachable after unconditional branch is terminator in middle`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "dead_code",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Br("target"),
                            Instruction.Unreachable(),
                        )),
                        BasicBlock("target", listOf(Instruction.Ret(null))),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("Terminator in middle") }, result.toString())
    }

    // 17. Phi in entry block (entry has no predecessors, so phi is invalid)
    @Test
    fun `phi in entry block with non-predecessor fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "phi_entry",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(i32(42), "entry"),
                            )),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("not a predecessor") }, result.toString())
    }

    // 18. Switch on non-integer type (float)
    @Test
    fun `switch on float type fails with case type mismatch`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_switch",
                    params = listOf(Parameter("x", Type.F32, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Switch(Parameter("x", Type.F32, 0), "default", listOf(
                                Pair(Constant.I32(0), "case0"),
                            )),
                        )),
                        BasicBlock("case0", listOf(Instruction.Ret(null))),
                        BasicBlock("default", listOf(Instruction.Ret(null))),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("switch case type") }, result.toString())
    }

    // 19. ExtractElement on non-vector type
    @Test
    fun `extractelement on non-vector type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_extract",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.ExtractElement(InstructionRef("%0", Type.I32), i32(42), i32(0)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("extractelement operand must be vector type") }, result.toString())
    }

    // 20. InsertElement on non-vector type
    @Test
    fun `insertelement on non-vector type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_insert",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.InsertElement(InstructionRef("%0", Type.I32), i32(0), i32(1), i32(0)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("insertelement operand must be vector type") }, result.toString())
    }

    // 21. CmpXchg with non-pointer base
    @Test
    fun `cmpxchg with non-pointer ptr fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_cmpxchg",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.CmpXchg(
                                InstructionRef("%0", Type.I32),
                                i32(0),
                                i32(1),
                                i32(2),
                                AtomicOrdering.SEQ_CST,
                                AtomicOrdering.ACQUIRE,
                            ),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("cmpxchg ptr operand must be pointer") }, result.toString())
    }

    // 22. CmpXchg with mismatched compare and new types
    @Test
    fun `cmpxchg with mismatched cmp and new types fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_cmpxchg_types",
                    params = listOf(Parameter("p", Type.OpaquePointer, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.CmpXchg(
                                InstructionRef("%0", Type.I32),
                                Parameter("p", Type.OpaquePointer, 0),
                                i32(1),
                                i64(2),
                                AtomicOrdering.SEQ_CST,
                                AtomicOrdering.ACQUIRE,
                            ),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("cmpxchg compare and new values must have same type") }, result.toString())
    }

    // 23. AtomicRMW with non-pointer base
    @Test
    fun `atomicrmw with non-pointer ptr fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_atomicrmw",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.AtomicRMW(
                                InstructionRef("%0", Type.I32),
                                AtomicRMWOp.ADD,
                                i32(0),
                                i32(1),
                                AtomicOrdering.SEQ_CST,
                            ),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("atomicrmw ptr operand must be pointer") }, result.toString())
    }

    // 24. Call with wrong argument type
    @Test
    fun `call with wrong argument type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_call_type",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Call(
                                dest = null,
                                function = FunctionRef("target", Type.Function(listOf(Type.I32), Type.Void)),
                                args = listOf(i64(42)),
                                returnType = Type.Void,
                            ),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("call arg 0 type") }, result.toString())
    }

    // 25. Ret with value in void function
    @Test
    fun `ret with value in void function fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "void_ret_val",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Ret(i32(42)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("ret with value in void function") }, result.toString())
    }

    // 26. Ret without value in non-void function
    @Test
    fun `ret without value in non-void function fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "no_ret_val",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("ret without value in non-void function") }, result.toString())
    }

    // 27. MemCpy with non-pointer dst
    @Test
    fun `memcpy with non-pointer dst fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_memcpy",
                    params = listOf(Parameter("src", Type.OpaquePointer, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.MemCpy(i32(0), Parameter("src", Type.OpaquePointer, 0), i32(16)),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("memcpy dst and src must be pointer") }, result.toString())
    }

    // 28. MemSet with non-pointer dst
    @Test
    fun `memset with non-pointer dst fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_memset",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.MemSet(i32(0), i32(0), i32(16)),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("memset dst must be pointer") }, result.toString())
    }

    // 29. MemMove with non-pointer src
    @Test
    fun `memmove with non-pointer src fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_memmove",
                    params = listOf(Parameter("dst", Type.OpaquePointer, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.MemMove(Parameter("dst", Type.OpaquePointer, 0), i32(0), i32(16)),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("memmove dst and src must be pointer") }, result.toString())
    }

    // 30. FPTrunc from non-float type
    @Test
    fun `fptrunc from integer type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_fptrunc",
                    params = emptyList(),
                    returnType = Type.F32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.FPTrunc(InstructionRef("%0", Type.F32), i64(1), Type.F32),
                            Instruction.Ret(InstructionRef("%0", Type.F32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("fptrunc source must be float type") }, result.toString())
    }

    // 31. FPExt from non-float type
    @Test
    fun `fpext from integer type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_fpext",
                    params = emptyList(),
                    returnType = Type.F64,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.FPExt(InstructionRef("%0", Type.F64), i32(1), Type.F64),
                            Instruction.Ret(InstructionRef("%0", Type.F64)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("fpext source must be float type") }, result.toString())
    }

    // 32. FPToUI from non-float type
    @Test
    fun `fptoui from integer type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_fptoui",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.FPToUI(InstructionRef("%0", Type.I32), i32(1), Type.I32),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("fptoui source must be float") }, result.toString())
    }

    // 33. UIToFP from non-integer type
    @Test
    fun `uitofp from float type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_uitofp",
                    params = emptyList(),
                    returnType = Type.F64,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.UIToFP(InstructionRef("%0", Type.F64), f32(1.0f), Type.F64),
                            Instruction.Ret(InstructionRef("%0", Type.F64)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("uitofp source must be integer") }, result.toString())
    }

    // 34. PtrToInt from non-pointer type
    @Test
    fun `ptrtoint from non-pointer type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_ptrtoint",
                    params = emptyList(),
                    returnType = Type.I64,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.PtrToInt(InstructionRef("%0", Type.I64), i32(0), Type.I64),
                            Instruction.Ret(InstructionRef("%0", Type.I64)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("ptrtoint source must be pointer") }, result.toString())
    }

    // 35. IntToPtr from non-integer type
    @Test
    fun `inttoptr from float type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_inttoptr",
                    params = emptyList(),
                    returnType = Type.OpaquePointer,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.IntToPtr(InstructionRef("%0", Type.OpaquePointer), f32(0.0f), Type.OpaquePointer),
                            Instruction.Ret(InstructionRef("%0", Type.OpaquePointer)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("inttoptr source must be integer") }, result.toString())
    }

    // 36. ShuffleVector with mismatched types
    @Test
    fun `shufflevector with mismatched operand types fails`() {
        val v4i32 = Type.Vector(Type.I32, 4)
        val v4i64 = Type.Vector(Type.I64, 4)
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_shuffle",
                    params = listOf(
                        Parameter("v1", v4i32, 0),
                        Parameter("v2", v4i64, 1),
                    ),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.ShuffleVector(InstructionRef("%0", v4i32),
                                Parameter("v1", v4i32, 0), Parameter("v2", v4i64, 1), listOf(0, 1, 2, 3)),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("shufflevector operands must have same type") }, result.toString())
    }

    // 37. VectorReduce on non-vector type
    @Test
    fun `vector reduce on non-vector type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_reduce",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.VectorReduce(InstructionRef("%0", Type.I32), VectorReduceOp.ADD, i32(42)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("vector.reduce operand must be vector type") }, result.toString())
    }

    // 38. IndirectBr with non-pointer address
    @Test
    fun `indirectbr with non-pointer address fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_indirectbr",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.IndirectBr(i32(0), listOf("entry")),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("indirectbr address must be pointer type") }, result.toString())
    }

    // 39. GEP with empty indices
    @Test
    fun `gep with no indices fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "gep_no_idx",
                    params = listOf(Parameter("p", Type.OpaquePointer, 0)),
                    returnType = Type.OpaquePointer,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.GetElementPtr(InstructionRef("%0", Type.OpaquePointer), Type.I32, Parameter("p", Type.OpaquePointer, 0), emptyList()),
                            Instruction.Ret(InstructionRef("%0", Type.OpaquePointer)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("getelementptr must have at least one index") }, result.toString())
    }

    // 40. Select with mismatched true/false types
    @Test
    fun `select with different true and false types fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_select_types",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Select(InstructionRef("%0", Type.I32), Constant.I1(true), i32(1), i64(2)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("select true/false values have different types") }, result.toString())
    }

    // 41. Load result type mismatch
    @Test
    fun `load result type mismatch fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_load_type",
                    params = listOf(Parameter("p", Type.OpaquePointer, 0)),
                    returnType = Type.I64,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Load(InstructionRef("%0", Type.I64), Parameter("p", Type.OpaquePointer, 0), Type.I32),
                            Instruction.Ret(InstructionRef("%0", Type.I64)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("load result type") && it.message.contains("doesn't match load type") }, result.toString())
    }

    // 42. BitCast between incompatible type categories
    @Test
    fun `bitcast between int and float fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_bitcast",
                    params = emptyList(),
                    returnType = Type.OpaquePointer,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.BitCast(InstructionRef("%0", Type.OpaquePointer), i32(0), Type.OpaquePointer),
                            Instruction.Ret(InstructionRef("%0", Type.OpaquePointer)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("bitcast between incompatible type categories") }, result.toString())
    }

    // 43. Empty block (no instructions at all)
    @Test
    fun `empty block fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "empty_block",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", emptyList()),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("empty") }, result.toString())
    }

    // 44. Call with zero args when function expects one
    @Test
    fun `call with too few arguments fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "call_few_args",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Call(
                                dest = null,
                                function = FunctionRef("target", Type.Function(listOf(Type.I32, Type.I64), Type.Void)),
                                args = listOf(i32(1)),
                                returnType = Type.Void,
                            ),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("arg count") }, result.toString())
    }

    // 45. FMA with mismatched operand types
    @Test
    fun `fma with mismatched operand types fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_fma",
                    params = emptyList(),
                    returnType = Type.F64,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.FMA(
                                InstructionRef("%0", Type.F64),
                                Constant.F64(1.0),
                                Constant.F32(2.0f),
                                Constant.F64(3.0),
                            ),
                            Instruction.Ret(InstructionRef("%0", Type.F64)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("fma operands must all be same type") }, result.toString())
    }

    // 46. Alloca with non-integer numElements
    @Test
    fun `alloca with non-integer numElements fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_alloca",
                    params = emptyList(),
                    returnType = Type.OpaquePointer,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Alloca(InstructionRef("%0", Type.OpaquePointer), Type.I32, f32(1.0f)),
                            Instruction.Ret(InstructionRef("%0", Type.OpaquePointer)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("alloca numElements must be integer") }, result.toString())
    }

    // 47. Splat with mismatched scalar type
    @Test
    fun `splat scalar type must match vector element type`() {
        val v4i32 = Type.Vector(Type.I32, 4)
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_splat",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Splat(InstructionRef("%0", v4i32), i64(1), v4i32),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("splat scalar type must match vector element type") }, result.toString())
    }

    // 48. ICmp result must be i1
    @Test
    fun `icmp result must be i1`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_icmp_result",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.ICmp(InstructionRef("%0", Type.I32), ICmpPredicate.EQ, i32(1), i32(2)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("icmp result must be i1") }, result.toString())
    }

    // 49. Vararg function call with exact required args passes
    @Test
    fun `vararg call with exact required args passes`() {
        val mod = Module(
            name = "good",
            functions = listOf(
                IrFunction(
                    name = "ok_vararg",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Call(
                                dest = null,
                                function = FunctionRef("printf", Type.Function(listOf(Type.OpaquePointer), Type.Void, vararg = true)),
                                args = listOf(Constant.NullPtr),
                                returnType = Type.Void,
                            ),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    // 50. Vararg function call with extra args passes
    @Test
    fun `vararg call with extra args passes`() {
        val mod = Module(
            name = "good",
            functions = listOf(
                IrFunction(
                    name = "ok_vararg_extra",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Call(
                                dest = null,
                                function = FunctionRef("printf", Type.Function(listOf(Type.OpaquePointer), Type.Void, vararg = true)),
                                args = listOf(Constant.NullPtr, i32(42), i64(100)),
                                returnType = Type.Void,
                            ),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }
}
