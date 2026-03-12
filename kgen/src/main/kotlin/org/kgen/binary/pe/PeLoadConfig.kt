package org.kgen.binary.pe

/**
 * IMAGE_LOAD_CONFIG_DIRECTORY parsed from PE data directory 10.
 *
 * Contains security features and configuration data: security cookie,
 * SE handler table, CFG (Control Flow Guard) settings, etc.
 */
data class PeLoadConfig(
    val size: Int,
    val timeDateStamp: Int,
    val majorVersion: Int,
    val minorVersion: Int,
    val globalFlagsClear: Int,
    val globalFlagsSet: Int,
    val criticalSectionDefaultTimeout: Int,
    val deCommitFreeBlockThreshold: Long,
    val deCommitTotalFreeThreshold: Long,
    val lockPrefixTable: Long,
    val maximumAllocationSize: Long,
    val virtualMemoryThreshold: Long,
    val processAffinityMask: Long,
    val processHeapFlags: Int,
    val csdVersion: Int,
    val dependentLoadFlags: Int,
    val editList: Long,
    val securityCookie: Long,
    val seHandlerTable: Long,
    val seHandlerCount: Long,
    val guardCfCheckFunctionPointer: Long,
    val guardCfDispatchFunctionPointer: Long,
    val guardCfFunctionTable: Long,
    val guardCfFunctionCount: Long,
    val guardFlags: Int,
) {
    val hasSecurityCookie: Boolean get() = securityCookie != 0L
    val hasSehTable: Boolean get() = seHandlerTable != 0L
    val hasCfg: Boolean get() = guardFlags and GUARD_CF_INSTRUMENTED != 0

    companion object {
        const val GUARD_CF_INSTRUMENTED = 0x00000100
        const val GUARD_CFW_INSTRUMENTED = 0x00000200
        const val GUARD_CF_FUNCTION_TABLE_PRESENT = 0x00000400
        const val GUARD_SECURITY_COOKIE_UNUSED = 0x00000800
        const val GUARD_PROTECT_DELAYLOAD_IAT = 0x00001000
        const val GUARD_DELAYLOAD_IAT_IN_ITS_OWN_SECTION = 0x00002000
        const val GUARD_CF_EXPORT_SUPPRESSION_INFO_PRESENT = 0x00004000
        const val GUARD_CF_ENABLE_EXPORT_SUPPRESSION = 0x00008000
        const val GUARD_CF_LONGJUMP_TABLE_PRESENT = 0x00010000
    }
}
