package org.kgen.binary.pe

data class PeSection(
    val name: String,
    val virtualSize: Int,
    val virtualAddress: Int,
    val rawDataSize: Int,
    val rawDataOffset: Int,
    val relocationsOffset: Int,
    val numberOfRelocations: Int,
    val characteristics: Int,
    val data: ByteArray,
) {
    val isCode: Boolean get() = characteristics and PeConstants.IMAGE_SCN_CNT_CODE != 0
    val isInitializedData: Boolean get() = characteristics and PeConstants.IMAGE_SCN_CNT_INITIALIZED_DATA != 0
    val isUninitializedData: Boolean get() = characteristics and PeConstants.IMAGE_SCN_CNT_UNINITIALIZED_DATA != 0
    val isReadable: Boolean get() = characteristics and PeConstants.IMAGE_SCN_MEM_READ != 0
    val isWritable: Boolean get() = characteristics and PeConstants.IMAGE_SCN_MEM_WRITE != 0
    val isExecutable: Boolean get() = characteristics and PeConstants.IMAGE_SCN_MEM_EXECUTE != 0
    val isDiscardable: Boolean get() = characteristics and PeConstants.IMAGE_SCN_MEM_DISCARDABLE != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeSection) return false
        return name == other.name && virtualAddress == other.virtualAddress &&
            virtualSize == other.virtualSize && characteristics == other.characteristics &&
            data.contentEquals(other.data)
    }
    override fun hashCode(): Int = name.hashCode() * 31 + data.contentHashCode()
}
