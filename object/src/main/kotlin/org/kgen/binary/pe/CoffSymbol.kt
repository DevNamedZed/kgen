package org.kgen.binary.pe

data class CoffSymbol(
    val name: String,
    val value: Long,
    val sectionNumber: Int,
    val type: Int,
    val storageClass: Int,
    val numberOfAuxSymbols: Int,
) {
    val isFunction: Boolean get() = type and 0x20 != 0
    val isExternal: Boolean get() = storageClass == PeConstants.IMAGE_SYM_CLASS_EXTERNAL
    val isStatic: Boolean get() = storageClass == PeConstants.IMAGE_SYM_CLASS_STATIC
    val isUndefined: Boolean get() = sectionNumber == 0
    val isAbsolute: Boolean get() = sectionNumber == -1
}

data class CoffRelocation(
    val virtualAddress: Int,
    val symbolIndex: Int,
    val type: Int,
)
