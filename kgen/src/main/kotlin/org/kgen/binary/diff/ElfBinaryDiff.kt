package org.kgen.binary.diff

import org.kgen.binary.elf.ElfReader

class ElfBinaryDiff : BinaryDiff {

    override fun diff(a: ByteArray, b: ByteArray): List<BinaryDelta> {
        val elfA = ElfReader.read(a)
        val elfB = ElfReader.read(b)
        val objA = ElfReader.toObjectFile(elfA)
        val objB = ElfReader.toObjectFile(elfB)

        val result = mutableListOf<BinaryDelta>()
        val sectionNamesA = objA.sections.associate { it.name to it }
        val sectionNamesB = objB.sections.associate { it.name to it }
        val symbolsByOffset = objA.symbols.associate { it.value to it.name }

        for ((name, secA) in sectionNamesA) {
            val secB = sectionNamesB[name] ?: continue
            result.addAll(diffBytes(secA.data, secB.data, secA.address, name, symbolsByOffset))
        }

        return result
    }

    override fun structuralDiff(a: ByteArray, b: ByteArray): StructuralDelta {
        val elfA = ElfReader.read(a)
        val elfB = ElfReader.read(b)
        val objA = ElfReader.toObjectFile(elfA)
        val objB = ElfReader.toObjectFile(elfB)

        val secNamesA = objA.sections.map { it.name }.toSet()
        val secNamesB = objB.sections.map { it.name }.toSet()

        val symNamesA = objA.symbols.map { it.name }.toSet()
        val symNamesB = objB.symbols.map { it.name }.toSet()

        val impNamesA = objA.imports.map { it.symbolName }.toSet()
        val impNamesB = objB.imports.map { it.symbolName }.toSet()

        val expNamesA = objA.exports.map { it.symbolName }.toSet()
        val expNamesB = objB.exports.map { it.symbolName }.toSet()

        val sectionsByNameA = objA.sections.associateBy { it.name }
        val sectionsByNameB = objB.sections.associateBy { it.name }
        val symbolsByOffset = objA.symbols.associate { it.value to it.name }

        val modifiedSections = mutableListOf<String>()
        val sectionDiffs = mutableMapOf<String, List<BinaryDelta>>()
        for (name in secNamesA.intersect(secNamesB)) {
            val secA = sectionsByNameA[name]!!
            val secB = sectionsByNameB[name]!!
            if (!secA.data.contentEquals(secB.data) || secA.kind != secB.kind || secA.flags != secB.flags) {
                modifiedSections.add(name)
                val deltas = diffBytes(secA.data, secB.data, secA.address, name, symbolsByOffset)
                if (deltas.isNotEmpty()) sectionDiffs[name] = deltas
            }
        }

        val symsByNameA = objA.symbols.associateBy { it.name }
        val symsByNameB = objB.symbols.associateBy { it.name }
        val modifiedSymbols = mutableListOf<String>()
        for (name in symNamesA.intersect(symNamesB)) {
            val sA = symsByNameA[name]!!
            val sB = symsByNameB[name]!!
            if (sA.value != sB.value || sA.size != sB.size || sA.binding != sB.binding ||
                sA.kind != sB.kind || sA.visibility != sB.visibility || sA.section != sB.section
            ) {
                modifiedSymbols.add(name)
            }
        }

        return StructuralDelta(
            addedSections = (secNamesB - secNamesA).toList(),
            removedSections = (secNamesA - secNamesB).toList(),
            modifiedSections = modifiedSections,
            addedSymbols = (symNamesB - symNamesA).toList(),
            removedSymbols = (symNamesA - symNamesB).toList(),
            modifiedSymbols = modifiedSymbols,
            addedImports = (impNamesB - impNamesA).toList(),
            removedImports = (impNamesA - impNamesB).toList(),
            addedExports = (expNamesB - expNamesA).toList(),
            removedExports = (expNamesA - expNamesB).toList(),
            sectionDiffs = sectionDiffs,
        )
    }

    companion object {
        private const val MAX_COALESCE_GAP = 8

        internal fun diffBytes(
            a: ByteArray, b: ByteArray, baseAddress: Long,
            section: String?, symbolsByOffset: Map<Long, String>
        ): List<BinaryDelta> {
            val minLen = minOf(a.size, b.size)
            val maxLen = maxOf(a.size, b.size)
            val runs = mutableListOf<IntRange>()

            var i = 0
            while (i < maxLen) {
                val aVal = if (i < a.size) a[i] else 0
                val bVal = if (i < b.size) b[i] else 0
                if (aVal != bVal) {
                    val start = i
                    while (i < maxLen) {
                        val av = if (i < a.size) a[i] else 0
                        val bv = if (i < b.size) b[i] else 0
                        if (av != bv) {
                            i++
                            continue
                        }
                        var gap = 0
                        var j = i
                        while (j < maxLen && gap < MAX_COALESCE_GAP) {
                            val av2 = if (j < a.size) a[j] else 0
                            val bv2 = if (j < b.size) b[j] else 0
                            if (av2 != bv2) break
                            gap++
                            j++
                        }
                        if (j < maxLen && gap < MAX_COALESCE_GAP) {
                            i = j
                            continue
                        }
                        break
                    }
                    runs.add(start until i)
                } else {
                    i++
                }
            }

            return runs.map { range ->
                val offset = baseAddress + range.first
                val old = if (range.first < a.size) {
                    a.copyOfRange(range.first, minOf(range.last + 1, a.size))
                } else byteArrayOf()
                val new = if (range.first < b.size) {
                    b.copyOfRange(range.first, minOf(range.last + 1, b.size))
                } else byteArrayOf()
                val nearest = findNearestSymbol(offset, symbolsByOffset)
                BinaryDelta(offset, old, new, section, nearest)
            }
        }

        private fun findNearestSymbol(offset: Long, symbolsByOffset: Map<Long, String>): String? {
            var best: String? = null
            var bestDist = Long.MAX_VALUE
            for ((addr, name) in symbolsByOffset) {
                if (addr <= offset) {
                    val dist = offset - addr
                    if (dist < bestDist) {
                        bestDist = dist
                        best = name
                    }
                }
            }
            return best
        }
    }
}
