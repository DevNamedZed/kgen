package org.kgen.binary.pe

/**
 * PE resource directory tree. Resources are organized as a three-level tree:
 * Type → Name → Language → Data.
 *
 * ```kotlin
 * val pe = PeReader.read(bytes)
 * val resources = pe.resources
 * for (type in resources.entries) {
 *     println("Type: ${type.nameOrId}")
 *     for (name in type.directory!!.entries) {
 *         println("  Name: ${name.nameOrId}")
 *         for (lang in name.directory!!.entries) {
 *             println("    Language: ${lang.nameOrId}, size=${lang.data!!.size}")
 *         }
 *     }
 * }
 * ```
 */
data class PeResourceDirectory(
    val characteristics: Int = 0,
    val timeDateStamp: Int = 0,
    val majorVersion: Short = 0,
    val minorVersion: Short = 0,
    val entries: List<PeResourceEntry>,
)

data class PeResourceEntry(
    val id: Int? = null,
    val name: String? = null,
    val directory: PeResourceDirectory? = null,
    val data: PeResourceData? = null,
) {
    val nameOrId: String get() = name ?: "#$id"
    val isDirectory: Boolean get() = directory != null
}

data class PeResourceData(
    val rva: Int,
    val size: Int,
    val codePage: Int = 0,
    val reserved: Int = 0,
    val bytes: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeResourceData) return false
        return rva == other.rva && size == other.size && codePage == other.codePage && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = rva
        result = 31 * result + size
        result = 31 * result + codePage
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/**
 * Standard PE resource type IDs.
 */
object PeResourceType {
    const val CURSOR = 1
    const val BITMAP = 2
    const val ICON = 3
    const val MENU = 4
    const val DIALOG = 5
    const val STRING = 6
    const val FONTDIR = 7
    const val FONT = 8
    const val ACCELERATOR = 9
    const val RCDATA = 10
    const val MESSAGETABLE = 11
    const val GROUP_CURSOR = 12
    const val GROUP_ICON = 14
    const val VERSION = 16
    const val DLGINCLUDE = 17
    const val PLUGPLAY = 19
    const val VXD = 20
    const val ANICURSOR = 21
    const val ANIICON = 22
    const val HTML = 23
    const val MANIFEST = 24

    fun nameOf(id: Int): String = when (id) {
        CURSOR -> "RT_CURSOR"
        BITMAP -> "RT_BITMAP"
        ICON -> "RT_ICON"
        MENU -> "RT_MENU"
        DIALOG -> "RT_DIALOG"
        STRING -> "RT_STRING"
        FONTDIR -> "RT_FONTDIR"
        FONT -> "RT_FONT"
        ACCELERATOR -> "RT_ACCELERATOR"
        RCDATA -> "RT_RCDATA"
        MESSAGETABLE -> "RT_MESSAGETABLE"
        GROUP_CURSOR -> "RT_GROUP_CURSOR"
        GROUP_ICON -> "RT_GROUP_ICON"
        VERSION -> "RT_VERSION"
        DLGINCLUDE -> "RT_DLGINCLUDE"
        PLUGPLAY -> "RT_PLUGPLAY"
        VXD -> "RT_VXD"
        ANICURSOR -> "RT_ANICURSOR"
        ANIICON -> "RT_ANIICON"
        HTML -> "RT_HTML"
        MANIFEST -> "RT_MANIFEST"
        else -> "RT_UNKNOWN($id)"
    }
}
