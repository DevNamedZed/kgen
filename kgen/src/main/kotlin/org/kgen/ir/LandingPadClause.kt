package org.kgen.ir

sealed interface LandingPadClause {
    /** Catch exceptions of a specific type. */
    data class Catch(val type: Value) : LandingPadClause
    /** Filter: resume unwinding if exception type is NOT in the list. */
    data class Filter(val types: List<Value>) : LandingPadClause
}
