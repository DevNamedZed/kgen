package org.kgen.ir.verify

import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IrVerifierTest {

    @Test
    fun `valid simple function passes verification`() {
        val mod = module("test") {
            function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = add(param(0), param(1))
                    ret(result)
                }
            }
        }

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `missing terminator fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "no_terminator",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Add(InstructionRef("%0", Type.I32), i32(1), i32(2))
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("does not end with a terminator") })
    }

    @Test
    fun `type mismatch in add fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_add",
                    params = listOf(Parameter("a", Type.I32, 0), Parameter("b", Type.I64, 1)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Add(InstructionRef("%0", Type.I32), Parameter("a", Type.I32, 0), Parameter("b", Type.I64, 1)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("add operands have different types") })
    }

    @Test
    fun `return type mismatch fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_ret",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Ret(Constant.I64(42)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("ret type") })
    }

    @Test
    fun `undefined branch target fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_br",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Br("nonexistent"),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("undefined block") })
    }

    @Test
    fun `duplicate function names fail`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction("foo", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Instruction.Ret(null))))),
                IrFunction("foo", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Instruction.Ret(null))))),
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("Duplicate function name") })
    }

    @Test
    fun `external function with body fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "ext_with_body",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(BasicBlock("entry", listOf(Instruction.Ret(null)))),
                    isExternal = true,
                ),
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("External function") })
    }

    @Test
    fun `condbr condition must be i1`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_cond",
                    params = listOf(Parameter("x", Type.I32, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.CondBr(Parameter("x", Type.I32, 0), "a", "b"),
                        )),
                        BasicBlock("a", listOf(Instruction.Ret(null))),
                        BasicBlock("b", listOf(Instruction.Ret(null))),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("condbr condition must be i1") })
    }

    @Test
    fun `valid void function`() {
        val mod = module("test") {
            function("noop", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
        }

        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `isTerminator identifies all terminators`() {
        assertTrue(IrVerifier.isTerminator(Instruction.Ret(null)))
        assertTrue(IrVerifier.isTerminator(Instruction.Br("x")))
        assertTrue(IrVerifier.isTerminator(Instruction.Unreachable()))
        assertFalse(IrVerifier.isTerminator(Instruction.Add(InstructionRef("%0", Type.I32), i32(1), i32(2))))
    }

    // --- SSA Dominance Tests ---

    @Test
    fun `valid diamond CFG passes dominance check`() {
        val mod = Module(
            name = "test",
            functions = listOf(
                IrFunction(
                    name = "diamond",
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
                                Pair(i32(2), "right"),
                            )),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `use before def in same block fails dominance`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "use_before_def",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            // %1 uses %0 but %0 is defined later
                            Instruction.Add(InstructionRef("%1", Type.I32), InstructionRef("%0", Type.I32), i32(1)),
                            Instruction.Add(InstructionRef("%0", Type.I32), i32(1), i32(2)),
                            Instruction.Ret(InstructionRef("%1", Type.I32)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("used before definition") }, result.toString())
    }

    @Test
    fun `value defined in non-dominating block fails`() {
        // Value defined in "left" is used in "right" — "left" does not dominate "right"
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "cross_branch_use",
                    params = listOf(Parameter("cond", Type.I1, 0)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.CondBr(Parameter("cond", Type.I1, 0), "left", "right"),
                        )),
                        BasicBlock("left", listOf(
                            Instruction.Add(InstructionRef("%0", Type.I32), i32(10), i32(20)),
                            Instruction.Br("merge"),
                        )),
                        BasicBlock("right", listOf(
                            // Using %0 which is defined in "left" — not dominated
                            Instruction.Add(InstructionRef("%1", Type.I32), InstructionRef("%0", Type.I32), i32(5)),
                            Instruction.Br("merge"),
                        )),
                        BasicBlock("merge", listOf(
                            Instruction.Ret(i32(0)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("does not dominate") }, result.toString())
    }

    @Test
    fun `value from dominating block is valid`() {
        // entry dominates everything, so using entry-defined value in any block is fine
        val mod = Module(
            name = "test",
            functions = listOf(
                IrFunction(
                    name = "dom_ok",
                    params = listOf(Parameter("cond", Type.I1, 0)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Add(InstructionRef("%0", Type.I32), i32(1), i32(2)),
                            Instruction.CondBr(Parameter("cond", Type.I1, 0), "left", "right"),
                        )),
                        BasicBlock("left", listOf(
                            Instruction.Add(InstructionRef("%1", Type.I32), InstructionRef("%0", Type.I32), i32(10)),
                            Instruction.Ret(InstructionRef("%1", Type.I32)),
                        )),
                        BasicBlock("right", listOf(
                            Instruction.Add(InstructionRef("%2", Type.I32), InstructionRef("%0", Type.I32), i32(20)),
                            Instruction.Ret(InstructionRef("%2", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `parameter use is valid everywhere`() {
        val mod = Module(
            name = "test",
            functions = listOf(
                IrFunction(
                    name = "param_use",
                    params = listOf(Parameter("x", Type.I32, 0), Parameter("cond", Type.I1, 1)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.CondBr(Parameter("cond", Type.I1, 1), "a", "b"),
                        )),
                        BasicBlock("a", listOf(
                            Instruction.Add(InstructionRef("%0", Type.I32), Parameter("x", Type.I32, 0), i32(1)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                        BasicBlock("b", listOf(
                            Instruction.Sub(InstructionRef("%1", Type.I32), Parameter("x", Type.I32, 0), i32(1)),
                            Instruction.Ret(InstructionRef("%1", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `use of undefined value fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "undef_use",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            // %99 was never defined
                            Instruction.Add(InstructionRef("%0", Type.I32), InstructionRef("%99", Type.I32), i32(1)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("undefined value") }, result.toString())
    }

    // --- Phi Predecessor Validation Tests ---

    @Test
    fun `phi with non-predecessor incoming block fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_phi_pred",
                    params = listOf(Parameter("cond", Type.I1, 0)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.CondBr(Parameter("cond", Type.I1, 0), "left", "merge"),
                        )),
                        BasicBlock("left", listOf(
                            Instruction.Br("merge"),
                        )),
                        BasicBlock("right", listOf(
                            // "right" is unreachable but defined
                            Instruction.Ret(i32(0)),
                        )),
                        BasicBlock("merge", listOf(
                            // phi claims value from "right" which is NOT a predecessor of "merge"
                            Instruction.Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(i32(1), "left"),
                                Pair(i32(2), "right"),
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

    @Test
    fun `phi missing predecessor entry fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "missing_pred",
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
                            // phi only has entry for "left" but "right" is also a predecessor
                            Instruction.Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(i32(1), "left"),
                            )),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("missing entry for predecessor") }, result.toString())
    }

    @Test
    fun `phi with duplicate predecessor entry fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "dup_pred",
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
                                Pair(i32(2), "left"),  // duplicate!
                                Pair(i32(3), "right"),
                            )),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("2 entries for predecessor") }, result.toString())
    }

    @Test
    fun `valid phi with correct predecessors passes`() {
        val mod = Module(
            name = "test",
            functions = listOf(
                IrFunction(
                    name = "good_phi",
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
                                Pair(i32(2), "right"),
                            )),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    // --- Loop dominance ---

    @Test
    fun `valid loop with phi passes`() {
        val mod = Module(
            name = "test",
            functions = listOf(
                IrFunction(
                    name = "loop",
                    params = listOf(Parameter("n", Type.I32, 0)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Br("header"),
                        )),
                        BasicBlock("header", listOf(
                            Instruction.Phi(InstructionRef("%i", Type.I32), listOf(
                                Pair(i32(0), "entry"),
                                Pair(InstructionRef("%next", Type.I32), "body"),
                            )),
                            Instruction.ICmp(InstructionRef("%cmp", Type.I1), ICmpPredicate.SLT, InstructionRef("%i", Type.I32), Parameter("n", Type.I32, 0)),
                            Instruction.CondBr(InstructionRef("%cmp", Type.I1), "body", "exit"),
                        )),
                        BasicBlock("body", listOf(
                            Instruction.Add(InstructionRef("%next", Type.I32), InstructionRef("%i", Type.I32), i32(1)),
                            Instruction.Br("header"),
                        )),
                        BasicBlock("exit", listOf(
                            Instruction.Ret(InstructionRef("%i", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `using value from loop body outside loop fails dominance`() {
        // %next is defined in "body" which does not dominate "exit"
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_loop",
                    params = listOf(Parameter("n", Type.I32, 0)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Br("header"),
                        )),
                        BasicBlock("header", listOf(
                            Instruction.Phi(InstructionRef("%i", Type.I32), listOf(
                                Pair(i32(0), "entry"),
                                Pair(InstructionRef("%next", Type.I32), "body"),
                            )),
                            Instruction.ICmp(InstructionRef("%cmp", Type.I1), ICmpPredicate.SLT, InstructionRef("%i", Type.I32), Parameter("n", Type.I32, 0)),
                            Instruction.CondBr(InstructionRef("%cmp", Type.I1), "body", "exit"),
                        )),
                        BasicBlock("body", listOf(
                            Instruction.Add(InstructionRef("%next", Type.I32), InstructionRef("%i", Type.I32), i32(1)),
                            Instruction.Br("header"),
                        )),
                        BasicBlock("exit", listOf(
                            // Using %next which is defined in "body" — body does NOT dominate exit
                            Instruction.Ret(InstructionRef("%next", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("does not dominate") }, result.toString())
    }

    // --- Phi dominance for incoming values ---

    @Test
    fun `phi incoming value must dominate predecessor block`() {
        // phi claims value %x from "entry", but %x is defined in "left" which doesn't dominate "entry"
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_phi_dom",
                    params = listOf(Parameter("cond", Type.I1, 0)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.CondBr(Parameter("cond", Type.I1, 0), "left", "merge"),
                        )),
                        BasicBlock("left", listOf(
                            Instruction.Add(InstructionRef("%x", Type.I32), i32(10), i32(20)),
                            Instruction.Br("merge"),
                        )),
                        BasicBlock("merge", listOf(
                            // %x from "entry" — but %x is defined in "left", not reachable from entry path
                            Instruction.Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(InstructionRef("%x", Type.I32), "entry"),
                                Pair(InstructionRef("%x", Type.I32), "left"),
                            )),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("does not dominate") }, result.toString())
    }

    // --- Complex valid programs ---

    @Test
    fun `valid multi-block function with multiple operations`() {
        val mod = module("test") {
            function("complex", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val sum = add(param(0), param(1))
                    val cmp = icmp(ICmpPredicate.SGT, sum, i32(100))
                    condBr(cmp, "big", "small")
                }
                block("big") {
                    val doubled = mul(param(0), i32(2))
                    ret(doubled)
                }
                block("small") {
                    ret(param(1))
                }
            }
        }

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `valid switch instruction`() {
        val mod = Module(
            name = "test",
            functions = listOf(
                IrFunction(
                    name = "switcher",
                    params = listOf(Parameter("x", Type.I32, 0)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Switch(Parameter("x", Type.I32, 0), "default", listOf(
                                Pair(Constant.I32(0), "case0"),
                                Pair(Constant.I32(1), "case1"),
                            )),
                        )),
                        BasicBlock("case0", listOf(Instruction.Ret(i32(10)))),
                        BasicBlock("case1", listOf(Instruction.Ret(i32(20)))),
                        BasicBlock("default", listOf(Instruction.Ret(i32(-1)))),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `terminator in middle of block fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "mid_term",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Br("entry"),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("Terminator in middle") }, result.toString())
    }

    @Test
    fun `duplicate block label fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "dup_block",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(Instruction.Ret(null))),
                        BasicBlock("entry", listOf(Instruction.Ret(null))),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("Duplicate block label") }, result.toString())
    }

    @Test
    fun `duplicate value definition fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "dup_val",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Add(InstructionRef("%0", Type.I32), i32(1), i32(2)),
                            Instruction.Add(InstructionRef("%0", Type.I32), i32(3), i32(4)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("Duplicate value definition") }, result.toString())
    }

    @Test
    fun `phi after non-phi fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_phi_order",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Br("merge"),
                        )),
                        BasicBlock("merge", listOf(
                            Instruction.Add(InstructionRef("%0", Type.I32), i32(1), i32(2)),
                            Instruction.Phi(InstructionRef("%1", Type.I32), listOf(Pair(i32(1), "entry"))),
                            Instruction.Ret(InstructionRef("%1", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("Phi node after non-phi") }, result.toString())
    }

    @Test
    fun `empty phi fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "empty_phi",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Br("target"),
                        )),
                        BasicBlock("target", listOf(
                            Instruction.Phi(InstructionRef("%0", Type.I32), emptyList()),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("phi must have at least one incoming") }, result.toString())
    }

    @Test
    fun `phi incoming type mismatch fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "phi_type",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Br("target"),
                        )),
                        BasicBlock("target", listOf(
                            Instruction.Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(Constant.I64(42), "entry"),
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

    @Test
    fun `select with non-i1 condition fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_select",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Select(InstructionRef("%0", Type.I32), i32(1), i32(10), i32(20)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("select condition must be i1") }, result.toString())
    }

    @Test
    fun `load from non-pointer fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_load",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Load(InstructionRef("%0", Type.I32), i32(42), Type.I32),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("load ptr operand must be pointer") }, result.toString())
    }

    @Test
    fun `float operation on int type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_fadd",
                    params = emptyList(),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.FAdd(InstructionRef("%0", Type.I32), i32(1), i32(2)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("fadd operands must be float type") }, result.toString())
    }

    @Test
    fun `int operation on float type fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_add_float",
                    params = emptyList(),
                    returnType = Type.F32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.And(InstructionRef("%0", Type.F32), f32(1.0f), f32(2.0f)),
                            Instruction.Ret(InstructionRef("%0", Type.F32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("and operands must be integer type") }, result.toString())
    }

    @Test
    fun `conversion type mismatch fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_conv",
                    params = emptyList(),
                    returnType = Type.I64,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            // zext from float — should be int only
                            Instruction.ZExt(InstructionRef("%0", Type.I64), f32(1.0f), Type.I64),
                            Instruction.Ret(InstructionRef("%0", Type.I64)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("zext source must be integer type") }, result.toString())
    }

    @Test
    fun `valid nested loop with phis passes`() {
        // Outer loop with inner loop — all phis have correct predecessors
        val mod = Module(
            name = "test",
            functions = listOf(
                IrFunction(
                    name = "nested_loop",
                    params = listOf(Parameter("n", Type.I32, 0)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Br("outer_header"),
                        )),
                        BasicBlock("outer_header", listOf(
                            Instruction.Phi(InstructionRef("%i", Type.I32), listOf(
                                Pair(i32(0), "entry"),
                                Pair(InstructionRef("%i_next", Type.I32), "outer_latch"),
                            )),
                            Instruction.ICmp(InstructionRef("%outer_cmp", Type.I1), ICmpPredicate.SLT, InstructionRef("%i", Type.I32), Parameter("n", Type.I32, 0)),
                            Instruction.CondBr(InstructionRef("%outer_cmp", Type.I1), "inner_header", "exit"),
                        )),
                        BasicBlock("inner_header", listOf(
                            Instruction.Phi(InstructionRef("%j", Type.I32), listOf(
                                Pair(i32(0), "outer_header"),
                                Pair(InstructionRef("%j_next", Type.I32), "inner_body"),
                            )),
                            Instruction.ICmp(InstructionRef("%inner_cmp", Type.I1), ICmpPredicate.SLT, InstructionRef("%j", Type.I32), InstructionRef("%i", Type.I32)),
                            Instruction.CondBr(InstructionRef("%inner_cmp", Type.I1), "inner_body", "outer_latch"),
                        )),
                        BasicBlock("inner_body", listOf(
                            Instruction.Add(InstructionRef("%j_next", Type.I32), InstructionRef("%j", Type.I32), i32(1)),
                            Instruction.Br("inner_header"),
                        )),
                        BasicBlock("outer_latch", listOf(
                            Instruction.Add(InstructionRef("%i_next", Type.I32), InstructionRef("%i", Type.I32), i32(1)),
                            Instruction.Br("outer_header"),
                        )),
                        BasicBlock("exit", listOf(
                            Instruction.Ret(InstructionRef("%i", Type.I32)),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `valid global and struct declarations`() {
        val mod = Module(
            name = "test",
            globals = listOf(
                Global("x", Type.I32, Constant.I32(42)),
                Global("y", Type.I64, Constant.I64(100)),
            ),
            structs = listOf(
                StructDef("Point", listOf(Param("x", Type.F64), Param("y", Type.F64))),
            ),
            functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Instruction.Ret(null))))),
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `duplicate global name fails`() {
        val mod = Module(
            name = "bad",
            globals = listOf(
                Global("x", Type.I32, Constant.I32(1)),
                Global("x", Type.I32, Constant.I32(2)),
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("Duplicate global name") })
    }

    @Test
    fun `duplicate struct name fails`() {
        val mod = Module(
            name = "bad",
            structs = listOf(
                StructDef("S", listOf(Param("a", Type.I32))),
                StructDef("S", listOf(Param("b", Type.I64))),
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("Duplicate struct name") })
    }

    @Test
    fun `global initializer type mismatch fails`() {
        val mod = Module(
            name = "bad",
            globals = listOf(
                Global("x", Type.I32, Constant.I64(42)),
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("Constant type") })
    }

    @Test
    fun `external function without body passes`() {
        val mod = Module(
            name = "test",
            functions = listOf(
                IrFunction("printf", listOf(Parameter("fmt", Type.OpaquePointer, 0)), Type.I32, emptyList(), isExternal = true, isVarArg = true),
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `non-external function without body fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction("f", emptyList(), Type.Void, emptyList(), isExternal = false),
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("must have at least one basic block") })
    }

    @Test
    fun `TagSwitch is a terminator`() {
        assertTrue(IrVerifier.isTerminator(Instruction.TagSwitch(
            i32(0), listOf(Pair("A", "blockA")), "default"
        )))
    }

    @Test
    fun `fcmp result must be i1`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_fcmp",
                    params = listOf(Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.FCmp(InstructionRef("%0", Type.I32), FCmpPredicate.OEQ,
                                Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("fcmp result must be i1") })
    }

    @Test
    fun `select result type must match operands`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_select",
                    params = emptyList(),
                    returnType = Type.I64,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Select(InstructionRef("%0", Type.I64), Constant.I1(true), i32(1), i32(2)),
                            Instruction.Ret(InstructionRef("%0", Type.I64)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("select result type") })
    }

    @Test
    fun `assume condition must be i1`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_assume",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Assume(i32(1)),
                            Instruction.Ret(null),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("assume condition must be i1") })
    }

    @Test
    fun `expect value and expected must match types`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_expect",
                    params = listOf(Parameter("x", Type.I32, 0)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Expect(InstructionRef("%0", Type.I32), Parameter("x", Type.I32, 0), Constant.I64(42)),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("expect value type") })
    }

    @Test
    fun `addrspacecast must be pointer to pointer`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_asc",
                    params = listOf(Parameter("x", Type.I32, 0)),
                    returnType = Type.OpaquePointer,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.AddrSpaceCast(InstructionRef("%0", Type.OpaquePointer), Parameter("x", Type.I32, 0), Type.OpaquePointer),
                            Instruction.Ret(InstructionRef("%0", Type.OpaquePointer)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("addrspacecast source must be pointer") })
    }

    @Test
    fun `prefetch address must be pointer`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_prefetch",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Prefetch(i32(0), 0, 3, 1),
                            Instruction.Ret(null),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("prefetch address must be pointer") })
    }

    @Test
    fun `newarray size must be integer`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_newarray",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.NewArray(InstructionRef("%0", Type.Reference(Type.I32)), Type.I32, Constant.F32(1.0f)),
                            Instruction.Ret(null),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("newarray size must be integer") })
    }

    @Test
    fun `arrayget index must be integer`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_arrayget",
                    params = listOf(Parameter("arr", Type.Reference(Type.I32), 0)),
                    returnType = Type.I32,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.ArrayGet(InstructionRef("%0", Type.I32), Parameter("arr", Type.Reference(Type.I32), 0), Constant.F32(0.0f), Type.I32),
                            Instruction.Ret(InstructionRef("%0", Type.I32)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("arrayget index must be integer") })
    }

    @Test
    fun `gep indices must be integer`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_gep",
                    params = listOf(Parameter("p", Type.OpaquePointer, 0)),
                    returnType = Type.OpaquePointer,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.GetElementPtr(InstructionRef("%0", Type.OpaquePointer), Type.I32, Parameter("p", Type.OpaquePointer, 0), listOf(Constant.F32(0.0f))),
                            Instruction.Ret(InstructionRef("%0", Type.OpaquePointer)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("getelementptr index 0 must be integer") })
    }

    @Test
    fun `stackrestore operand must be pointer`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_stackrestore",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.StackRestore(i32(0)),
                            Instruction.Ret(null),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("stackrestore operand must be pointer") })
    }

    @Test
    fun `valid fcmp with i1 result passes`() {
        val mod = Module(
            name = "good",
            functions = listOf(
                IrFunction(
                    name = "good_fcmp",
                    params = listOf(Parameter("a", Type.F64, 0), Parameter("b", Type.F64, 1)),
                    returnType = Type.I1,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.FCmp(InstructionRef("%0", Type.I1), FCmpPredicate.OLT,
                                Parameter("a", Type.F64, 0), Parameter("b", Type.F64, 1)),
                            Instruction.Ret(InstructionRef("%0", Type.I1)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `invoke validates arg types`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_invoke",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            Instruction.Invoke(
                                dest = null,
                                function = FunctionRef("target", Type.Function(listOf(Type.I32), Type.Void)),
                                args = listOf(Constant.I64(42)),
                                returnType = Type.Void,
                                normalDest = "cont",
                                unwindDest = "cleanup",
                            ),
                        )),
                        BasicBlock("cont", listOf(Instruction.Ret(null))),
                        BasicBlock("cleanup", listOf(
                            Instruction.LandingPad(InstructionRef("%lp", Type.OpaquePointer), Type.OpaquePointer, emptyList(), false),
                            Instruction.Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("invoke arg 0 type") })
    }
}
