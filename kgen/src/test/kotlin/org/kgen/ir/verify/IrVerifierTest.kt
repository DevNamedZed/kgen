package org.kgen.ir.verify

import org.kgen.ir.*
import org.kgen.ir.instructions.*
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
                            Add(InstructionRef("%0", Type.I32), i32(1), i32(2))
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
                            Add(InstructionRef("%0", Type.I32), Parameter("a", Type.I32, 0), Parameter("b", Type.I64, 1)),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            Ret(Constant.I64(42)),
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
                            Br(BlockRef("nonexistent")),
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
                IrFunction("foo", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Ret(null))))),
                IrFunction("foo", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Ret(null))))),
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
                    blocks = listOf(BasicBlock("entry", listOf(Ret(null)))),
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
                            CondBr(Parameter("x", Type.I32, 0), BlockRef("a"), BlockRef("b")),
                        )),
                        BasicBlock("a", listOf(Ret(null))),
                        BasicBlock("b", listOf(Ret(null))),
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
        assertTrue(IrVerifier.isTerminator(Ret(null)))
        assertTrue(IrVerifier.isTerminator(Br(BlockRef("x"))))
        assertTrue(IrVerifier.isTerminator(Unreachable()))
        assertFalse(IrVerifier.isTerminator(Add(InstructionRef("%0", Type.I32), i32(1), i32(2))))
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
                            CondBr(Parameter("cond", Type.I1, 0), BlockRef("left"), BlockRef("right")),
                        )),
                        BasicBlock("left", listOf(
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("right", listOf(
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("merge", listOf(
                            Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(i32(1), BlockRef("left")),
                                Pair(i32(2), BlockRef("right")),
                            )),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            Add(InstructionRef("%1", Type.I32), InstructionRef("%0", Type.I32), i32(1)),
                            Add(InstructionRef("%0", Type.I32), i32(1), i32(2)),
                            Ret(InstructionRef("%1", Type.I32)),
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
                            CondBr(Parameter("cond", Type.I1, 0), BlockRef("left"), BlockRef("right")),
                        )),
                        BasicBlock("left", listOf(
                            Add(InstructionRef("%0", Type.I32), i32(10), i32(20)),
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("right", listOf(
                            // Using %0 which is defined in "left" — not dominated
                            Add(InstructionRef("%1", Type.I32), InstructionRef("%0", Type.I32), i32(5)),
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("merge", listOf(
                            Ret(i32(0)),
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
                            Add(InstructionRef("%0", Type.I32), i32(1), i32(2)),
                            CondBr(Parameter("cond", Type.I1, 0), BlockRef("left"), BlockRef("right")),
                        )),
                        BasicBlock("left", listOf(
                            Add(InstructionRef("%1", Type.I32), InstructionRef("%0", Type.I32), i32(10)),
                            Ret(InstructionRef("%1", Type.I32)),
                        )),
                        BasicBlock("right", listOf(
                            Add(InstructionRef("%2", Type.I32), InstructionRef("%0", Type.I32), i32(20)),
                            Ret(InstructionRef("%2", Type.I32)),
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
                            CondBr(Parameter("cond", Type.I1, 1), BlockRef("a"), BlockRef("b")),
                        )),
                        BasicBlock("a", listOf(
                            Add(InstructionRef("%0", Type.I32), Parameter("x", Type.I32, 0), i32(1)),
                            Ret(InstructionRef("%0", Type.I32)),
                        )),
                        BasicBlock("b", listOf(
                            Sub(InstructionRef("%1", Type.I32), Parameter("x", Type.I32, 0), i32(1)),
                            Ret(InstructionRef("%1", Type.I32)),
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
                            Add(InstructionRef("%0", Type.I32), InstructionRef("%99", Type.I32), i32(1)),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            CondBr(Parameter("cond", Type.I1, 0), BlockRef("left"), BlockRef("merge")),
                        )),
                        BasicBlock("left", listOf(
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("right", listOf(
                            // "right" is unreachable but defined
                            Ret(i32(0)),
                        )),
                        BasicBlock("merge", listOf(
                            // phi claims value from "right" which is NOT a predecessor of "merge"
                            Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(i32(1), BlockRef("left")),
                                Pair(i32(2), BlockRef("right")),
                            )),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            CondBr(Parameter("cond", Type.I1, 0), BlockRef("left"), BlockRef("right")),
                        )),
                        BasicBlock("left", listOf(
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("right", listOf(
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("merge", listOf(
                            // phi only has entry for "left" but "right" is also a predecessor
                            Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(i32(1), BlockRef("left")),
                            )),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            CondBr(Parameter("cond", Type.I1, 0), BlockRef("left"), BlockRef("right")),
                        )),
                        BasicBlock("left", listOf(
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("right", listOf(
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("merge", listOf(
                            Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(i32(1), BlockRef("left")),
                                Pair(i32(2), BlockRef("left")),  // duplicate!
                                Pair(i32(3), BlockRef("right")),
                            )),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            CondBr(Parameter("cond", Type.I1, 0), BlockRef("left"), BlockRef("right")),
                        )),
                        BasicBlock("left", listOf(
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("right", listOf(
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("merge", listOf(
                            Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(i32(1), BlockRef("left")),
                                Pair(i32(2), BlockRef("right")),
                            )),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            Br(BlockRef("header")),
                        )),
                        BasicBlock("header", listOf(
                            Phi(InstructionRef("%i", Type.I32), listOf(
                                Pair(i32(0), BlockRef("entry")),
                                Pair(InstructionRef("%next", Type.I32), BlockRef("body")),
                            )),
                            ICmp(InstructionRef("%cmp", Type.I1), ICmpPredicate.SLT, InstructionRef("%i", Type.I32), Parameter("n", Type.I32, 0)),
                            CondBr(InstructionRef("%cmp", Type.I1), BlockRef("body"), BlockRef("exit")),
                        )),
                        BasicBlock("body", listOf(
                            Add(InstructionRef("%next", Type.I32), InstructionRef("%i", Type.I32), i32(1)),
                            Br(BlockRef("header")),
                        )),
                        BasicBlock("exit", listOf(
                            Ret(InstructionRef("%i", Type.I32)),
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
                            Br(BlockRef("header")),
                        )),
                        BasicBlock("header", listOf(
                            Phi(InstructionRef("%i", Type.I32), listOf(
                                Pair(i32(0), BlockRef("entry")),
                                Pair(InstructionRef("%next", Type.I32), BlockRef("body")),
                            )),
                            ICmp(InstructionRef("%cmp", Type.I1), ICmpPredicate.SLT, InstructionRef("%i", Type.I32), Parameter("n", Type.I32, 0)),
                            CondBr(InstructionRef("%cmp", Type.I1), BlockRef("body"), BlockRef("exit")),
                        )),
                        BasicBlock("body", listOf(
                            Add(InstructionRef("%next", Type.I32), InstructionRef("%i", Type.I32), i32(1)),
                            Br(BlockRef("header")),
                        )),
                        BasicBlock("exit", listOf(
                            // Using %next which is defined in "body" — body does NOT dominate exit
                            Ret(InstructionRef("%next", Type.I32)),
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
                            CondBr(Parameter("cond", Type.I1, 0), BlockRef("left"), BlockRef("merge")),
                        )),
                        BasicBlock("left", listOf(
                            Add(InstructionRef("%x", Type.I32), i32(10), i32(20)),
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("merge", listOf(
                            // %x from "entry" — but %x is defined in "left", not reachable from entry path
                            Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(InstructionRef("%x", Type.I32), BlockRef("entry")),
                                Pair(InstructionRef("%x", Type.I32), BlockRef("left")),
                            )),
                            Ret(InstructionRef("%0", Type.I32)),
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
                    condBr(cmp, BlockRef("big"), BlockRef("small"))
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
                            Switch(Parameter("x", Type.I32, 0), BlockRef("default"), listOf(
                                Pair(Constant.I32(0), BlockRef("case0")),
                                Pair(Constant.I32(1), BlockRef("case1")),
                            )),
                        )),
                        BasicBlock("case0", listOf(Ret(i32(10)))),
                        BasicBlock("case1", listOf(Ret(i32(20)))),
                        BasicBlock("default", listOf(Ret(i32(-1)))),
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
                            Br(BlockRef("entry")),
                            Ret(null),
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
                        BasicBlock("entry", listOf(Ret(null))),
                        BasicBlock("entry", listOf(Ret(null))),
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
                            Add(InstructionRef("%0", Type.I32), i32(1), i32(2)),
                            Add(InstructionRef("%0", Type.I32), i32(3), i32(4)),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            Br(BlockRef("merge")),
                        )),
                        BasicBlock("merge", listOf(
                            Add(InstructionRef("%0", Type.I32), i32(1), i32(2)),
                            Phi(InstructionRef("%1", Type.I32), listOf(Pair(i32(1), BlockRef("entry")))),
                            Ret(InstructionRef("%1", Type.I32)),
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
                            Br(BlockRef("target")),
                        )),
                        BasicBlock("target", listOf(
                            Phi(InstructionRef("%0", Type.I32), emptyList()),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            Br(BlockRef("target")),
                        )),
                        BasicBlock("target", listOf(
                            Phi(InstructionRef("%0", Type.I32), listOf(
                                Pair(Constant.I64(42), BlockRef("entry")),
                            )),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            Select(InstructionRef("%0", Type.I32), i32(1), i32(10), i32(20)),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            Load(InstructionRef("%0", Type.I32), i32(42), Type.I32),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            FAdd(InstructionRef("%0", Type.I32), i32(1), i32(2)),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            And(InstructionRef("%0", Type.F32), f32(1.0f), f32(2.0f)),
                            Ret(InstructionRef("%0", Type.F32)),
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
                            ZExt(InstructionRef("%0", Type.I64), f32(1.0f), Type.I64),
                            Ret(InstructionRef("%0", Type.I64)),
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
                            Br(BlockRef("outer_header")),
                        )),
                        BasicBlock("outer_header", listOf(
                            Phi(InstructionRef("%i", Type.I32), listOf(
                                Pair(i32(0), BlockRef("entry")),
                                Pair(InstructionRef("%i_next", Type.I32), BlockRef("outer_latch")),
                            )),
                            ICmp(InstructionRef("%outer_cmp", Type.I1), ICmpPredicate.SLT, InstructionRef("%i", Type.I32), Parameter("n", Type.I32, 0)),
                            CondBr(InstructionRef("%outer_cmp", Type.I1), BlockRef("inner_header"), BlockRef("exit")),
                        )),
                        BasicBlock("inner_header", listOf(
                            Phi(InstructionRef("%j", Type.I32), listOf(
                                Pair(i32(0), BlockRef("outer_header")),
                                Pair(InstructionRef("%j_next", Type.I32), BlockRef("inner_body")),
                            )),
                            ICmp(InstructionRef("%inner_cmp", Type.I1), ICmpPredicate.SLT, InstructionRef("%j", Type.I32), InstructionRef("%i", Type.I32)),
                            CondBr(InstructionRef("%inner_cmp", Type.I1), BlockRef("inner_body"), BlockRef("outer_latch")),
                        )),
                        BasicBlock("inner_body", listOf(
                            Add(InstructionRef("%j_next", Type.I32), InstructionRef("%j", Type.I32), i32(1)),
                            Br(BlockRef("inner_header")),
                        )),
                        BasicBlock("outer_latch", listOf(
                            Add(InstructionRef("%i_next", Type.I32), InstructionRef("%i", Type.I32), i32(1)),
                            Br(BlockRef("outer_header")),
                        )),
                        BasicBlock("exit", listOf(
                            Ret(InstructionRef("%i", Type.I32)),
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
                StructDefinition("Point", listOf(Param("x", Type.F64), Param("y", Type.F64))),
            ),
            functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Ret(null))))),
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
                StructDefinition("S", listOf(Param("a", Type.I32))),
                StructDefinition("S", listOf(Param("b", Type.I64))),
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
        assertTrue(IrVerifier.isTerminator(TagSwitch(
            i32(0), listOf(Pair("A", BlockRef("blockA"))), BlockRef("default")
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
                            FCmp(InstructionRef("%0", Type.I32), FCmpPredicate.OEQ,
                                Parameter("a", Type.F32, 0), Parameter("b", Type.F32, 1)),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            Select(InstructionRef("%0", Type.I64), Constant.I1(true), i32(1), i32(2)),
                            Ret(InstructionRef("%0", Type.I64)),
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
                            Assume(i32(1)),
                            Ret(null),
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
                            Expect(InstructionRef("%0", Type.I32), Parameter("x", Type.I32, 0), Constant.I64(42)),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            AddrSpaceCast(InstructionRef("%0", Type.OpaquePointer), Parameter("x", Type.I32, 0), Type.OpaquePointer),
                            Ret(InstructionRef("%0", Type.OpaquePointer)),
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
                            Prefetch(i32(0), 0, 3, 1),
                            Ret(null),
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
                            NewArray(InstructionRef("%0", Type.Reference(Type.I32)), Type.I32, Constant.F32(1.0f)),
                            Ret(null),
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
                            ArrayGet(InstructionRef("%0", Type.I32), Parameter("arr", Type.Reference(Type.I32), 0), Constant.F32(0.0f), Type.I32),
                            Ret(InstructionRef("%0", Type.I32)),
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
                            GetElementPtr(InstructionRef("%0", Type.OpaquePointer), Type.I32, Parameter("p", Type.OpaquePointer, 0), listOf(Constant.F32(0.0f))),
                            Ret(InstructionRef("%0", Type.OpaquePointer)),
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
                            StackRestore(i32(0)),
                            Ret(null),
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
                            FCmp(InstructionRef("%0", Type.I1), FCmpPredicate.OLT,
                                Parameter("a", Type.F64, 0), Parameter("b", Type.F64, 1)),
                            Ret(InstructionRef("%0", Type.I1)),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    // --- GCRoot / InteriorPtr gc strategy ---

    @Test
    fun `GCRoot without gc strategy fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "no_gc",
                    params = listOf(Parameter("p", Type.OpaquePointer, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            GCRoot(Parameter("p", Type.OpaquePointer, 0), null),
                            Ret(null),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("GCRoot") && it.message.contains("gc strategy") }, result.toString())
    }

    @Test
    fun `GCRoot with gc strategy passes`() {
        val mod = Module(
            name = "good",
            functions = listOf(
                IrFunction(
                    name = "with_gc",
                    params = listOf(Parameter("p", Type.OpaquePointer, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            GCRoot(Parameter("p", Type.OpaquePointer, 0), null),
                            Ret(null),
                        ))
                    ),
                    gc = "shadow-stack",
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `InteriorPtr without gc strategy fails`() {
        val refType = Type.Reference(Type.ClassRef("Obj"))
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "no_gc",
                    params = listOf(Parameter("obj", refType, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            InteriorPtr(InstructionRef("%0", Type.OpaquePointer), Parameter("obj", refType, 0), i32(0), Type.I32),
                            Ret(null),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("InteriorPtr") && it.message.contains("gc strategy") }, result.toString())
    }

    // --- Coroutine ordering ---

    @Test
    fun `CoroEnd without CoroBegin fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "no_begin",
                    params = listOf(Parameter("h", Type.OpaquePointer, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            CoroEnd(Parameter("h", Type.OpaquePointer, 0)),
                            Ret(null),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("CoroBegin") }, result.toString())
    }

    @Test
    fun `CoroEnd dominated by CoroBegin passes`() {
        val mod = Module(
            name = "good",
            functions = listOf(
                IrFunction(
                    name = "coro",
                    params = listOf(Parameter("id", Type.OpaquePointer, 0), Parameter("mem", Type.OpaquePointer, 1)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            CoroBegin(InstructionRef("%h", Type.OpaquePointer), Parameter("id", Type.OpaquePointer, 0), Parameter("mem", Type.OpaquePointer, 1)),
                            CoroEnd(InstructionRef("%h", Type.OpaquePointer)),
                            Ret(null),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `CoroResume not dominated by CoroBegin fails`() {
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "coro",
                    params = listOf(Parameter("id", Type.OpaquePointer, 0), Parameter("mem", Type.OpaquePointer, 1)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            CoroResume(Parameter("id", Type.OpaquePointer, 0)),
                            Br(BlockRef("body")),
                        )),
                        BasicBlock("body", listOf(
                            CoroBegin(InstructionRef("%h", Type.OpaquePointer), Parameter("id", Type.OpaquePointer, 0), Parameter("mem", Type.OpaquePointer, 1)),
                            CoroEnd(InstructionRef("%h", Type.OpaquePointer)),
                            Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("CoroResume") && it.message.contains("not dominated") }, result.toString())
    }

    // --- TagSwitch exhaustiveness ---

    @Test
    fun `TagSwitch without default and missing variants fails`() {
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
            TaggedVariant("Rect", 1, listOf(Type.F64, Type.F64)),
            TaggedVariant("Triangle", 2, listOf(Type.F64, Type.F64, Type.F64)),
        ))
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "partial_switch",
                    params = listOf(Parameter("s", unionType, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            TagSwitch(Parameter("s", unionType, 0), listOf(
                                Pair("Circle", BlockRef("circle_bb")),
                            ), defaultTarget = null),
                        )),
                        BasicBlock("circle_bb", listOf(Ret(null))),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("missing variants") }, result.toString())
    }

    @Test
    fun `TagSwitch without default covering all variants passes`() {
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
            TaggedVariant("Rect", 1, listOf(Type.F64, Type.F64)),
        ))
        val mod = Module(
            name = "good",
            functions = listOf(
                IrFunction(
                    name = "exhaustive_switch",
                    params = listOf(Parameter("s", unionType, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            TagSwitch(Parameter("s", unionType, 0), listOf(
                                Pair("Circle", BlockRef("circle_bb")),
                                Pair("Rect", BlockRef("rect_bb")),
                            ), defaultTarget = null),
                        )),
                        BasicBlock("circle_bb", listOf(Ret(null))),
                        BasicBlock("rect_bb", listOf(Ret(null))),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `TagSwitch with default and partial cases passes`() {
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
            TaggedVariant("Rect", 1, listOf(Type.F64, Type.F64)),
        ))
        val mod = Module(
            name = "good",
            functions = listOf(
                IrFunction(
                    name = "partial_with_default",
                    params = listOf(Parameter("s", unionType, 0)),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            TagSwitch(Parameter("s", unionType, 0), listOf(
                                Pair("Circle", BlockRef("circle_bb")),
                            ), defaultTarget = BlockRef("default_bb")),
                        )),
                        BasicBlock("circle_bb", listOf(Ret(null))),
                        BasicBlock("default_bb", listOf(Ret(null))),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    // --- CatchValue validation ---

    @Test
    fun `CatchValue outside catch handler fails`() {
        val exType = Type.ClassRef("Exception")
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_catch",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            CatchValue(InstructionRef("%0", Type.Reference(exType)), exType),
                            Ret(null),
                        ))
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("CatchValue") && it.message.contains("not in a catch handler") }, result.toString())
    }

    @Test
    fun `CatchValue in catch handler with matching type passes`() {
        val exType = Type.ClassRef("Exception")
        val mod = Module(
            name = "good",
            functions = listOf(
                IrFunction(
                    name = "good_catch",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            TryCatchRegion(BlockRef("try_body"), listOf(
                                CatchHandler(exType, "handler"),
                            )),
                            Br(BlockRef("try_body")),
                        )),
                        BasicBlock("try_body", listOf(Ret(null))),
                        BasicBlock("handler", listOf(
                            CatchValue(InstructionRef("%0", Type.Reference(exType)), exType),
                            Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, result.toString())
    }

    @Test
    fun `CatchValue with mismatched exception type fails`() {
        val exType = Type.ClassRef("Exception")
        val otherType = Type.ClassRef("IOException")
        val mod = Module(
            name = "bad",
            functions = listOf(
                IrFunction(
                    name = "bad_catch_type",
                    params = emptyList(),
                    returnType = Type.Void,
                    blocks = listOf(
                        BasicBlock("entry", listOf(
                            TryCatchRegion(BlockRef("try_body"), listOf(
                                CatchHandler(exType, "handler"),
                            )),
                            Br(BlockRef("try_body")),
                        )),
                        BasicBlock("try_body", listOf(Ret(null))),
                        BasicBlock("handler", listOf(
                            CatchValue(InstructionRef("%0", Type.Reference(otherType)), otherType),
                            Ret(null),
                        )),
                    ),
                )
            ),
        )

        val result = IrVerifier.verify(mod)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("CatchValue exception type") && it.message.contains("doesn't match") }, result.toString())
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
                            Invoke(
                                dest = null,
                                function = FunctionRef("target", Type.Function(listOf(Type.I32), Type.Void)),
                                args = listOf(Constant.I64(42)),
                                returnType = Type.Void,
                                normalDest = BlockRef("cont"),
                                unwindDest = BlockRef("cleanup"),
                            ),
                        )),
                        BasicBlock("cont", listOf(Ret(null))),
                        BasicBlock("cleanup", listOf(
                            LandingPad(InstructionRef("%lp", Type.OpaquePointer), Type.OpaquePointer, emptyList(), false),
                            Ret(null),
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
