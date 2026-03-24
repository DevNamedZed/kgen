package org.kgen.ir.build.sets

/**
 * Root marker interface for all instruction set interfaces.
 *
 * Each category has a corresponding `*InstructionSet` interface that defines
 * the emission methods for that category's instructions. Implementations hold
 * an [org.kgen.ir.build.InstructionSink] and delegate to it.
 *
 * The typed builder API composes instruction sets via Kotlin delegation:
 * ```kotlin
 * val builder = ModuleBuilder("module", Target.x86_64())
 *     .allowing<ArithmeticInstructionSet>()
 *     .allowing<ObjectInstructionSet>()
 *     .build()
 *
 * builder.add(x, y)        // compiles — ArithmeticInstructionSet
 * builder.newObject("Foo")  // compiles — ObjectInstructionSet
 * // builder.pin(ref)       // does not compile — InteropInstructionSet not added
 * ```
 *
 * @see org.kgen.ir.build.InstructionSink
 */
interface InstructionSet
