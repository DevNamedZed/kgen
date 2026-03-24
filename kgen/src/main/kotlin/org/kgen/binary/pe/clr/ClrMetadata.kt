package org.kgen.binary.pe.clr

/**
 * CLR metadata model for .NET assemblies (PE files with COR20 header).
 *
 * This represents the metadata streams and tables found in managed PE files.
 * Both the PE reader and (future) PE writer use this model.
 */
data class ClrMetadata(
    val majorRuntimeVersion: Int,
    val minorRuntimeVersion: Int,
    val flags: Int,
    val entryPointToken: Int,
    val metadataVersion: String,
    val tables: ClrTables,
    val strings: ClrStringHeap,
    val blobs: ClrBlobHeap,
    val guids: ClrGuidHeap,
    val userStrings: ClrUserStringHeap,
) {
    val isILOnly: Boolean get() = flags and COR_FLAGS_ILONLY != 0
    val is32BitRequired: Boolean get() = flags and COR_FLAGS_32BITREQUIRED != 0
    val isStrongNameSigned: Boolean get() = flags and COR_FLAGS_STRONGNAMESIGNED != 0
    val isNativeEntryPoint: Boolean get() = flags and COR_FLAGS_NATIVE_ENTRYPOINT != 0

    companion object {
        const val COR_FLAGS_ILONLY = 0x00000001
        const val COR_FLAGS_32BITREQUIRED = 0x00000002
        const val COR_FLAGS_STRONGNAMESIGNED = 0x00000008
        const val COR_FLAGS_NATIVE_ENTRYPOINT = 0x00000010
        const val COR_FLAGS_TRACKDEBUGDATA = 0x00010000
        const val COR_FLAGS_PREFER32BIT = 0x00020000
    }
}

data class ClrTables(
    val modules: List<ClrModule> = emptyList(),
    val typeRefs: List<ClrTypeRef> = emptyList(),
    val typeDefs: List<ClrTypeDef> = emptyList(),
    val fields: List<ClrField> = emptyList(),
    val methodDefs: List<ClrMethodDefinition> = emptyList(),
    val params: List<ClrParam> = emptyList(),
    val interfaceImpls: List<ClrInterfaceImpl> = emptyList(),
    val memberRefs: List<ClrMemberRef> = emptyList(),
    val constants: List<ClrConstant> = emptyList(),
    val customAttributes: List<ClrCustomAttribute> = emptyList(),
    val fieldMarshals: List<ClrFieldMarshal> = emptyList(),
    val declSecurities: List<ClrDeclSecurity> = emptyList(),
    val standAloneSigs: List<ClrStandAloneSig> = emptyList(),
    val classlayouts: List<ClrClassLayout> = emptyList(),
    val fieldLayouts: List<ClrFieldLayout> = emptyList(),
    val eventMaps: List<ClrEventMap> = emptyList(),
    val events: List<ClrEvent> = emptyList(),
    val propertyMaps: List<ClrPropertyMap> = emptyList(),
    val properties: List<ClrProperty> = emptyList(),
    val methodSemantics: List<ClrMethodSemantics> = emptyList(),
    val methodImpls: List<ClrMethodImpl> = emptyList(),
    val moduleRefs: List<ClrModuleRef> = emptyList(),
    val typeSpecs: List<ClrTypeSpec> = emptyList(),
    val implMaps: List<ClrImplMap> = emptyList(),
    val fieldRVAs: List<ClrFieldRVA> = emptyList(),
    val assemblies: List<ClrAssembly> = emptyList(),
    val assemblyRefs: List<ClrAssemblyRef> = emptyList(),
    val nestedClasses: List<ClrNestedClass> = emptyList(),
    val genericParams: List<ClrGenericParam> = emptyList(),
    val methodSpecs: List<ClrMethodSpec> = emptyList(),
    val genericParamConstraints: List<ClrGenericParamConstraint> = emptyList(),
)

enum class ClrTableId(val id: Int) {
    MODULE(0x00),
    TYPE_REF(0x01),
    TYPE_DEF(0x02),
    FIELD(0x04),
    METHOD_DEF(0x06),
    PARAM(0x08),
    INTERFACE_IMPL(0x09),
    MEMBER_REF(0x0A),
    CONSTANT(0x0B),
    CUSTOM_ATTRIBUTE(0x0C),
    FIELD_MARSHAL(0x0D),
    DECL_SECURITY(0x0E),
    STAND_ALONE_SIG(0x11),
    EVENT_MAP(0x12),
    EVENT(0x14),
    PROPERTY_MAP(0x15),
    PROPERTY(0x17),
    METHOD_SEMANTICS(0x18),
    METHOD_IMPL(0x19),
    MODULE_REF(0x1A),
    TYPE_SPEC(0x1B),
    IMPL_MAP(0x1C),
    FIELD_RVA(0x1D),
    ASSEMBLY(0x20),
    ASSEMBLY_REF(0x23),
    NESTED_CLASS(0x29),
    GENERIC_PARAM(0x2A),
    METHOD_SPEC(0x2B),
    GENERIC_PARAM_CONSTRAINT(0x2C),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }
        @JvmStatic fun fromId(id: Int): ClrTableId? = byId[id]
    }
}
