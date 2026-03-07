package org.kgen.ir

/** Float comparison predicates. O-prefix = ordered (false if NaN), U-prefix = unordered (true if NaN). */
enum class FCmpPredicate {
    FALSE, OEQ, OGT, OGE, OLT, OLE, ONE, ORD,
    UEQ, UGT, UGE, ULT, ULE, UNE, UNO, TRUE,
}
