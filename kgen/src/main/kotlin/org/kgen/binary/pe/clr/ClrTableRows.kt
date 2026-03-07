package org.kgen.binary.pe.clr

data class ClrModule(val generation: Int, val name: Int, val mvid: Int, val encId: Int, val encBaseId: Int)
data class ClrTypeRef(val resolutionScope: Int, val name: Int, val namespace: Int)
data class ClrTypeDef(
    val flags: Int, val name: Int, val namespace: Int,
    val extends: Int, val fieldList: Int, val methodList: Int,
)
data class ClrField(val flags: Int, val name: Int, val signature: Int)
data class ClrMethodDef(
    val rva: Int, val implFlags: Int, val flags: Int,
    val name: Int, val signature: Int, val paramList: Int,
)
data class ClrParam(val flags: Int, val sequence: Int, val name: Int)
data class ClrInterfaceImpl(val classIndex: Int, val interfaceIndex: Int)
data class ClrMemberRef(val classIndex: Int, val name: Int, val signature: Int)
data class ClrConstant(val type: Int, val parent: Int, val value: Int)
data class ClrCustomAttribute(val parent: Int, val type: Int, val value: Int)
data class ClrStandAloneSig(val signature: Int)
data class ClrClassLayout(val packingSize: Int, val classSize: Int, val parent: Int)
data class ClrFieldLayout(val offset: Int, val field: Int)
data class ClrModuleRef(val name: Int)
data class ClrTypeSpec(val signature: Int)
data class ClrImplMap(val mappingFlags: Int, val memberForwarded: Int, val importName: Int, val importScope: Int)
data class ClrFieldRVA(val rva: Int, val field: Int)
data class ClrAssembly(
    val hashAlgId: Int, val majorVersion: Int, val minorVersion: Int,
    val buildNumber: Int, val revisionNumber: Int, val flags: Int,
    val publicKey: Int, val name: Int, val culture: Int,
)
data class ClrAssemblyRef(
    val majorVersion: Int, val minorVersion: Int,
    val buildNumber: Int, val revisionNumber: Int, val flags: Int,
    val publicKeyOrToken: Int, val name: Int, val culture: Int, val hashValue: Int,
)
data class ClrNestedClass(val nestedClass: Int, val enclosingClass: Int)
data class ClrGenericParam(val number: Int, val flags: Int, val owner: Int, val name: Int)
data class ClrMethodSpec(val method: Int, val instantiation: Int)
data class ClrGenericParamConstraint(val owner: Int, val constraint: Int)
