package org.kgen.ir.visit

/**
 * Root marker interface for all IR instruction visitors.
 *
 * Implementations selectively extend one or more category-specific visitor interfaces
 * (e.g., [ArithmeticInstructionVisitor], [MemoryInstructionVisitor]) to receive
 * callbacks for instructions belonging to those categories. Categories that a visitor
 * does not implement are silently skipped during dispatch via [Instructions.accept].
 *
 * This design follows the acyclic visitor pattern: each category defines its own
 * visitor interface with default no-op methods, so consumers only override the
 * instructions they care about without pulling in every category.
 *
 * @see Instructions.accept
 */
interface InstructionVisitor
