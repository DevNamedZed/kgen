package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Basic alias analysis using local reasoning rules.
 *
 * Rules (in order of application):
 * 1. **Identity**: same value → MustAlias
 * 2. **Distinct allocas**: two different alloca results → NoAlias
 * 3. **Global vs alloca**: a global and an alloca → NoAlias
 * 4. **Global vs global**: two different globals → NoAlias
 * 5. **Null pointer**: null doesn't alias valid pointers → NoAlias
 * 6. **GEP base**: if two GEPs share a base but differ in a constant index → NoAlias
 * 7. **GEP base decomposition**: a GEP derived from an alloca/global
 *    doesn't alias a different alloca/global → NoAlias
 *
 * Falls back to [AliasResult.MayAlias] when no rule applies.
 */
class BasicAliasAnalysis(private val fn: IrFunction) : AliasAnalysis {

    // Map from instruction result name → the instruction that defines it
    private val defs: Map<String, Instruction> = buildMap {
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                val r = inst.result
                if (r != null) put(r.name, inst)
            }
        }
    }

    override fun alias(a: Value, b: Value): AliasResult {
        // Rule 1: identity
        if (a.name == b.name) return AliasResult.MustAlias

        val baseA = underlyingObject(a)
        val baseB = underlyingObject(b)

        // After decomposition, check identity again
        if (baseA.name == baseB.name) {
            // Same base — check GEP indices
            return aliasGepCheck(a, b)
        }

        // Rule 5: null pointer
        if (baseA is Constant.NullPtr || baseB is Constant.NullPtr) return AliasResult.NoAlias

        // Rule 2: distinct allocas
        val defA = defs[baseA.name]
        val defB = defs[baseB.name]
        if (defA is Alloca && defB is Alloca) return AliasResult.NoAlias

        // Rule 3: global vs alloca
        if (baseA is GlobalRef && defB is Alloca) return AliasResult.NoAlias
        if (defA is Alloca && baseB is GlobalRef) return AliasResult.NoAlias

        // Rule 4: distinct globals
        if (baseA is GlobalRef && baseB is GlobalRef && baseA.name != baseB.name) return AliasResult.NoAlias

        // Rule 3 extended: function param (caller memory) vs alloca (callee stack)
        if (baseA is Parameter && defB is Alloca) return AliasResult.NoAlias
        if (defA is Alloca && baseB is Parameter) return AliasResult.NoAlias

        return AliasResult.MayAlias
    }

    /**
     * Strips GEP / bitcast / addrspacecast to find the underlying object.
     */
    private fun underlyingObject(v: Value): Value {
        var current = v
        val visited = mutableSetOf<String>()
        while (current.name !in visited) {
            visited.add(current.name)
            val def = defs[current.name] ?: break
            current = when (def) {
                is GetElementPtr -> def.ptr
                is BitCast -> def.value
                is AddrSpaceCast -> def.value
                else -> break
            }
        }
        return current
    }

    /**
     * When two pointers share the same base, check if constant GEP indices
     * prove they point to different memory.
     */
    private fun aliasGepCheck(a: Value, b: Value): AliasResult {
        val gepA = defs[a.name]
        val gepB = defs[b.name]

        if (gepA is GetElementPtr && gepB is GetElementPtr) {
            // Same base pointer — compare constant indices
            if (gepA.ptr.name == gepB.ptr.name && gepA.indices.size == gepB.indices.size) {
                for (i in gepA.indices.indices) {
                    val idxA = gepA.indices[i]
                    val idxB = gepB.indices[i]
                    if (idxA is Constant && idxB is Constant) {
                        if (constantValue(idxA) != constantValue(idxB)) {
                            return AliasResult.NoAlias
                        }
                    }
                }
                // All constant indices match → same location
                if (gepA.indices.all { it is Constant } && gepB.indices.all { it is Constant }) {
                    return AliasResult.MustAlias
                }
            }
        }

        // One is GEP, other is the base itself → PartialAlias (field within object)
        if (gepA is GetElementPtr && gepA.ptr.name == b.name) return AliasResult.PartialAlias
        if (gepB is GetElementPtr && gepB.ptr.name == a.name) return AliasResult.PartialAlias

        return AliasResult.MayAlias
    }

    private fun constantValue(c: Constant): Long = when (c) {
        is Constant.I1 -> if (c.value) 1L else 0L
        is Constant.I8 -> c.value.toLong()
        is Constant.I16 -> c.value.toLong()
        is Constant.I32 -> c.value.toLong()
        is Constant.I64 -> c.value
        is Constant.I128 -> c.value
        is Constant.IntN -> c.value
        else -> Long.MIN_VALUE // distinct from any normal value
    }
}
