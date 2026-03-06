package org.kgen.ir

/** Memory ordering for atomic operations, from weakest to strongest. */
enum class AtomicOrdering {
    UNORDERED, MONOTONIC, ACQUIRE, RELEASE, ACQ_REL, SEQ_CST,
}
