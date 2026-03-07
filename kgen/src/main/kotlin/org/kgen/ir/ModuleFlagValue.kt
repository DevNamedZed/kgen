package org.kgen.ir

/** Module flag values for controlling linker/codegen behavior. */
sealed interface ModuleFlagValue {
    data class IntFlag(val value: Long) : ModuleFlagValue
    data class StringFlag(val value: String) : ModuleFlagValue
    data class MetadataFlag(val value: MetadataValue) : ModuleFlagValue
}
