package org.kgen.ir

/** Integer comparison predicates. EQ/NE work for both signed and unsigned.
 *  U-prefix = unsigned, S-prefix = signed. */
enum class ICmpPredicate {
    EQ, NE, UGT, UGE, ULT, ULE, SGT, SGE, SLT, SLE,
}
