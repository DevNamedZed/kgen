package org.kgen.binary

data class ComdatGroup(
    val name: String,
    val selection: ComdatSelection,
    val sections: List<String>,      // member section names
)

enum class ComdatSelection {
    ANY,                // linker picks any — all must be identical
    EXACT_MATCH,        // PE: all definitions must match exactly
    LARGEST,            // PE: pick largest
    NEWEST,             // PE: pick newest
    SAME_SIZE,          // PE: all must be same size
    NO_DUPLICATES,      // error on duplicates
}
