package org.kgen.binary.pe

data class CoffHeader(
    val machine: Int,
    val numberOfSections: Int,
    val timestamp: Int,
    val symbolTableOffset: Int,
    val numberOfSymbols: Int,
    val optionalHeaderSize: Int,
    val characteristics: Int,
)
