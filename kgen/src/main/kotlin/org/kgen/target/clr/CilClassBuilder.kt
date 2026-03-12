package org.kgen.target.clr

import org.kgen.binary.pe.clr.*
import org.kgen.target.clr.asm.CilAssembler
import org.kgen.target.clr.asm.CilToken
import java.io.ByteArrayOutputStream

/**
 * Fluent builder for constructing .NET assemblies (CLR metadata) programmatically.
 *
 * The CIL equivalent of [org.kgen.target.jvm.ClassFileBuilder]. Wraps
 * [CilAssembler] and CLR heap/table builders to provide an ergonomic API
 * for generating .NET metadata without touching raw table indices.
 *
 * ```java
 * var builder = new CilClassBuilder("MyAssembly", "MyNamespace.MyClass");
 * builder.method("Add", CilClassBuilder.sig(CilSigType.I4, CilSigType.I4, CilSigType.I4),
 *     CilMethodFlags.PUBLIC | CilMethodFlags.STATIC, code -> {
 *         code.ldarg(0);
 *         code.ldarg(1);
 *         code.add();
 *         code.ret();
 *     });
 * ClrMetadata meta = builder.build();
 * byte[] metadataBytes = builder.toBytes();
 * ```
 */
class CilClassBuilder(
    private val assemblyName: String,
    private val className: String,
) {
    private val strings = ClrStringHeapBuilder()
    private val blobs = ClrBlobHeapBuilder()
    private val guids = ClrGuidHeapBuilder()
    private val us = ClrUserStringHeapBuilder()

    private val typeDefs = mutableListOf<TypeDefEntry>()
    private val methodDefs = mutableListOf<MethodDefEntry>()
    private val fieldDefs = mutableListOf<FieldDefEntry>()
    private val typeRefs = mutableListOf<TypeRefEntry>()
    private val memberRefs = mutableListOf<MemberRefEntry>()
    private val assemblyRefs = mutableListOf<AssemblyRefEntry>()
    private val moduleRefs = mutableListOf<String>()
    private val implMaps = mutableListOf<ImplMapEntry>()
    private val params = mutableListOf<ParamEntry>()

    private var currentNamespace: String = ""
    private var classFlags: Int = 0x00100001 // Public | Class
    private var entryPointMethodIndex: Int = -1

    init {
        val dot = className.lastIndexOf('.')
        if (dot >= 0) {
            currentNamespace = className.substring(0, dot)
        }
        // Add mscorlib assembly ref and System.Object type ref by default
        addAssemblyRef("mscorlib", 4, 0, 0, 0)
        addTypeRef(1, "Object", "System")
    }

    /**
     * Set the class flags. Defaults to Public | Class (0x00100001).
     */
    fun flags(flags: Int): CilClassBuilder {
        classFlags = flags
        return this
    }

    /**
     * Add a field to the class.
     */
    @JvmOverloads
    fun field(name: String, type: CilSigType, flags: Int = CilFieldFlags.PRIVATE): CilClassBuilder {
        val sig = buildFieldSig(type)
        fieldDefs.add(FieldDefEntry(name, flags, sig))
        return this
    }

    /**
     * Add a method with a CIL body emitted via the callback.
     */
    fun method(name: String, signature: ByteArray, flags: Int, body: (CilAssembler) -> Unit): CilClassBuilder {
        val asm = CilAssembler()
        body(asm)
        val codeBytes = asm.toByteArray()
        methodDefs.add(MethodDefEntry(name, flags, signature, codeBytes))
        return this
    }

    /**
     * Add a method with pre-assembled CIL bytecode.
     */
    fun method(name: String, signature: ByteArray, flags: Int, code: ByteArray): CilClassBuilder {
        methodDefs.add(MethodDefEntry(name, flags, signature, code))
        return this
    }

    /**
     * Add a method with no body (abstract or extern).
     */
    fun method(name: String, signature: ByteArray, flags: Int): CilClassBuilder {
        methodDefs.add(MethodDefEntry(name, flags, signature, null))
        return this
    }

    /**
     * Add a P/Invoke method declaration — a managed method that calls a native function.
     *
     * ```java
     * builder.pinvoke("kernel32.dll", "GetTickCount",
     *     CilClassBuilder.sig(CilSigType.U4),
     *     CilMethodFlags.PUBLIC | CilMethodFlags.STATIC);
     *
     * // With different native name
     * builder.pinvoke("msvcrt.dll", "puts", "puts",
     *     CilClassBuilder.sig(CilSigType.I4, CilSigType.I),
     *     CilMethodFlags.PUBLIC | CilMethodFlags.STATIC);
     * ```
     *
     * @param dllName the native library name (e.g., "kernel32.dll")
     * @param methodName the managed method name
     * @param nativeName the native function name (defaults to methodName)
     * @param signature the method signature blob
     * @param flags method attribute flags (must include STATIC)
     * @param charSet character set for string marshaling (0=not specified, 2=Ansi, 4=Unicode, 6=Auto)
     */
    @JvmOverloads
    fun pinvoke(
        dllName: String, methodName: String, nativeName: String = methodName,
        signature: ByteArray, flags: Int, charSet: Int = 0,
    ): CilClassBuilder {
        // PInvokeImpl flag = 0x2000
        val methodFlags = flags or 0x2000
        methodDefs.add(MethodDefEntry(methodName, methodFlags, signature, null))

        // Find or add ModuleRef
        var moduleRefIndex = moduleRefs.indexOf(dllName) + 1
        if (moduleRefIndex == 0) {
            moduleRefs.add(dllName)
            moduleRefIndex = moduleRefs.size
        }

        // MemberForwarded coded index: MethodDef tag = 1, 1-bit tag
        val methodIndex = methodDefs.size // 1-based
        val memberForwarded = (methodIndex shl 1) or 1

        // Mapping flags: charSet bits [1:2]
        val mappingFlags = charSet

        implMaps.add(ImplMapEntry(mappingFlags, memberForwarded, nativeName, moduleRefIndex))
        return this
    }

    /**
     * Mark a method as the assembly entry point (by 0-based index among added methods).
     */
    fun entryPoint(methodIndex: Int): CilClassBuilder {
        entryPointMethodIndex = methodIndex
        return this
    }

    /**
     * Add an assembly reference (e.g., "mscorlib" or "System.Runtime").
     */
    @JvmOverloads
    fun addAssemblyRef(
        name: String, major: Int = 0, minor: Int = 0,
        build: Int = 0, revision: Int = 0,
    ): CilClassBuilder {
        assemblyRefs.add(AssemblyRefEntry(name, major, minor, build, revision))
        return this
    }

    /**
     * Add a type reference (to an external type).
     * Returns the 1-based type ref index for use in coded indices.
     */
    fun addTypeRef(assemblyRefIndex: Int, name: String, namespace: String): Int {
        typeRefs.add(TypeRefEntry(assemblyRefIndex, name, namespace))
        return typeRefs.size
    }

    /**
     * Add a member reference (e.g., to call a method on an external type).
     * Returns a [CilToken] for use in CIL call instructions.
     *
     * @param typeRefIndex 1-based index into the TypeRef table
     * @param name method or field name
     * @param signature blob-encoded signature
     */
    fun addMemberRef(typeRefIndex: Int, name: String, signature: ByteArray): CilToken {
        memberRefs.add(MemberRefEntry(typeRefIndex, name, signature))
        return CilToken.memberRef(memberRefs.size)
    }

    /**
     * Build the [ClrMetadata] model.
     */
    fun build(): ClrMetadata {
        val moduleName = strings.add("$assemblyName.dll")
        val mvid = guids.add(ByteArray(16))

        val asmNameIdx = strings.add(assemblyName)
        val emptyStr = strings.add("")

        // Build assembly refs
        val asmRefRows = assemblyRefs.map { ref ->
            ClrAssemblyRef(
                majorVersion = ref.major, minorVersion = ref.minor,
                buildNumber = ref.build, revisionNumber = ref.revision,
                flags = 0, publicKeyOrToken = 0,
                name = strings.add(ref.name), culture = 0, hashValue = 0,
            )
        }

        // Build type refs
        val typeRefRows = typeRefs.map { ref ->
            ClrTypeRef(
                // RESOLUTION_SCOPE coded index: AssemblyRef tag = 3, 2-bit tag
                resolutionScope = (ref.assemblyRefIndex shl 2) or 3,
                name = strings.add(ref.name),
                namespace = strings.add(ref.namespace),
            )
        }

        // Build member refs
        val memberRefRows = memberRefs.map { ref ->
            ClrMemberRef(
                // MemberRefParent coded index: TypeRef tag = 1, 3-bit tag
                classIndex = (ref.typeRefIndex shl 3) or 1,
                name = strings.add(ref.name),
                signature = blobs.add(ref.signature),
            )
        }

        // Build field rows
        val fieldRows = fieldDefs.map { f ->
            ClrField(flags = f.flags, name = strings.add(f.name), signature = blobs.add(f.signature))
        }

        // Build method rows and param rows
        val methodRows = mutableListOf<ClrMethodDef>()
        val paramRows = mutableListOf<ClrParam>()
        var paramIndex = 1

        for (m in methodDefs) {
            methodRows.add(ClrMethodDef(
                rva = 0, // no real RVA (metadata only)
                implFlags = 0,
                flags = m.flags,
                name = strings.add(m.name),
                signature = blobs.add(m.signature),
                paramList = paramIndex,
            ))
            // If there are params added, they'd increment here
            // For now, we don't track individual parameters in the builder
        }

        // Simple name parsing
        val dot = className.lastIndexOf('.')
        val simpleClassName = if (dot >= 0) className.substring(dot + 1) else className
        val ns = if (dot >= 0) className.substring(0, dot) else ""

        // TypeDef rows: <Module> + our class
        val typeDefRows = listOf(
            ClrTypeDef(0, strings.add("<Module>"), strings.add(""), 0, 1, 1),
            ClrTypeDef(
                flags = classFlags,
                name = strings.add(simpleClassName),
                namespace = strings.add(ns),
                // extends System.Object: TypeRef #1, TypeDefOrRef tag=1, 2-bit tag
                extends = (1 shl 2) or 1,
                fieldList = 1,
                methodList = 1,
            ),
        )

        val entryToken = if (entryPointMethodIndex >= 0) {
            0x06000000 or (entryPointMethodIndex + 1) // MethodDef token
        } else 0

        // Build ModuleRef rows
        val moduleRefRows = moduleRefs.map { name -> ClrModuleRef(name = strings.add(name)) }

        // Build ImplMap rows
        val implMapRows = implMaps.map { im ->
            ClrImplMap(
                mappingFlags = im.mappingFlags,
                memberForwarded = im.memberForwarded,
                importName = strings.add(im.importName),
                importScope = im.moduleRefIndex,
            )
        }

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = typeDefRows,
            typeRefs = typeRefRows,
            fields = fieldRows,
            methodDefs = methodRows,
            params = paramRows,
            memberRefs = memberRefRows,
            moduleRefs = moduleRefRows,
            implMaps = implMapRows,
            assemblies = listOf(ClrAssembly(
                hashAlgId = 0x8004,
                majorVersion = 1, minorVersion = 0,
                buildNumber = 0, revisionNumber = 0,
                flags = 0, publicKey = 0,
                name = asmNameIdx, culture = emptyStr,
            )),
            assemblyRefs = asmRefRows,
        )

        return ClrMetadata(
            majorRuntimeVersion = 2,
            minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY,
            entryPointToken = entryToken,
            metadataVersion = "v4.0.30319",
            tables = tables,
            strings = strings.build(),
            blobs = blobs.build(),
            guids = guids.build(),
            userStrings = us.build(),
        )
    }

    /**
     * Build and serialize to CLR metadata bytes (BSJB format).
     */
    fun toBytes(): ByteArray = ClrTableWriter().write(build())

    private fun buildFieldSig(type: CilSigType): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x06) // FIELD calling convention
        writeSigType(out, type)
        return out.toByteArray()
    }

    private fun writeSigType(out: ByteArrayOutputStream, type: CilSigType) {
        out.write(type.code)
    }

    private data class TypeDefEntry(val name: String, val namespace: String, val flags: Int)
    private data class MethodDefEntry(val name: String, val flags: Int, val signature: ByteArray, val code: ByteArray?)
    private data class FieldDefEntry(val name: String, val flags: Int, val signature: ByteArray)
    private data class TypeRefEntry(val assemblyRefIndex: Int, val name: String, val namespace: String)
    private data class MemberRefEntry(val typeRefIndex: Int, val name: String, val signature: ByteArray)
    private data class AssemblyRefEntry(val name: String, val major: Int, val minor: Int, val build: Int, val revision: Int)
    private data class ImplMapEntry(val mappingFlags: Int, val memberForwarded: Int, val importName: String, val moduleRefIndex: Int)
    private data class ParamEntry(val name: String, val sequence: Int, val flags: Int)

    companion object {
        /**
         * Build a method signature blob.
         *
         * ```java
         * // static int Add(int a, int b)
         * var sig = CilClassBuilder.sig(CilSigType.I4, CilSigType.I4, CilSigType.I4);
         *
         * // static void Main()
         * var sig = CilClassBuilder.sig(CilSigType.VOID);
         *
         * // instance void .ctor()
         * var sig = CilClassBuilder.instanceSig(CilSigType.VOID);
         * ```
         *
         * @param returnType the return type
         * @param paramTypes the parameter types
         */
        @JvmStatic
        fun sig(returnType: CilSigType, vararg paramTypes: CilSigType): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(0x00) // DEFAULT calling convention (static)
            writeCompressedInt(out, paramTypes.size)
            out.write(returnType.code)
            for (p in paramTypes) {
                out.write(p.code)
            }
            return out.toByteArray()
        }

        /**
         * Build an instance method signature blob.
         */
        @JvmStatic
        fun instanceSig(returnType: CilSigType, vararg paramTypes: CilSigType): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(0x20) // HASTHIS calling convention (instance)
            writeCompressedInt(out, paramTypes.size)
            out.write(returnType.code)
            for (p in paramTypes) {
                out.write(p.code)
            }
            return out.toByteArray()
        }

        private fun writeCompressedInt(out: ByteArrayOutputStream, value: Int) {
            if (value <= 0x7F) {
                out.write(value)
            } else if (value <= 0x3FFF) {
                out.write(0x80 or (value shr 8))
                out.write(value and 0xFF)
            } else {
                out.write(0xC0 or (value shr 24))
                out.write((value shr 16) and 0xFF)
                out.write((value shr 8) and 0xFF)
                out.write(value and 0xFF)
            }
        }
    }
}

/**
 * CIL element type codes for use in method/field signatures.
 */
enum class CilSigType(val code: Int) {
    VOID(0x01),
    BOOLEAN(0x02),
    CHAR(0x03),
    I1(0x04),
    U1(0x05),
    I2(0x06),
    U2(0x07),
    I4(0x08),
    U4(0x09),
    I8(0x0A),
    U8(0x0B),
    R4(0x0C),
    R8(0x0D),
    STRING(0x0E),
    OBJECT(0x1C),
    SZARRAY(0x1D),
    I(0x18),   // native int
    U(0x19),   // native uint
}

/**
 * Common CIL method attribute flags.
 */
object CilMethodFlags {
    const val PRIVATE = 0x0001
    const val FAM_AND_ASSEM = 0x0002
    const val ASSEMBLY = 0x0003
    const val FAMILY = 0x0004
    const val FAM_OR_ASSEM = 0x0005
    const val PUBLIC = 0x0006
    const val STATIC = 0x0010
    const val FINAL = 0x0020
    const val VIRTUAL = 0x0040
    const val HIDE_BY_SIG = 0x0080
    const val ABSTRACT = 0x0400
    const val SPECIAL_NAME = 0x0800
    const val RT_SPECIAL_NAME = 0x1000
}

/**
 * Common CIL field attribute flags.
 */
object CilFieldFlags {
    const val PRIVATE = 0x0001
    const val FAM_AND_ASSEM = 0x0002
    const val ASSEMBLY = 0x0003
    const val FAMILY = 0x0004
    const val FAM_OR_ASSEM = 0x0005
    const val PUBLIC = 0x0006
    const val STATIC = 0x0010
    const val INIT_ONLY = 0x0020
    const val LITERAL = 0x0040
}
