package org.kgen.runtime.compile

import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.pass.Inlining
import org.kgen.pass.Mem2Reg
import org.kgen.target.jvm.*

/**
 * Compiles `@KgenRuntime` classfiles to kgen IR.
 *
 * Reads JVM classfile bytecode, validates the Runtime Subset rules,
 * and lowers the bytecode to kgen IR. The resulting IR module can be
 * compiled by any kgen backend (x86, ARM64, RISC-V).
 *
 * ```java
 * var compiler = new RuntimeCompiler(Target.x86_64());
 * var module = compiler.compile(classBytes);
 * // module is a standard kgen IR Module — pass to CodeGenerator
 * ```
 */
class RuntimeCompiler(
    private val target: Target,
    private val classLayout: ClassLayout? = null,
) {

    /**
     * Compile a classfile to a kgen IR module.
     *
     * @param classBytes the raw .class file bytes
     * @return compiled IR module
     * @throws RuntimeSubsetException if the classfile violates subset rules
     */
    fun compile(classBytes: ByteArray): Module {
        val cf = JvmClassReader.read(classBytes)

        // Validate subset
        val errors = SubsetValidator.validate(cf)
        if (errors.isNotEmpty()) {
            throw RuntimeSubsetException(cf.thisClassName, errors)
        }

        val builder = IrBuilder(cf.thisClassName.replace('/', '_'), target)
        val classPrefix = cf.thisClassName.replace('/', '_')
        val importMap = mutableMapOf<String, String>() // java method name → native symbol name

        // First pass: process @KgenImport declarations
        val declaredImports = mutableSetOf<String>()
        for (method in cf.methods) {
            val name = cf.string(method.nameIndex)
            val desc = cf.string(method.descriptorIndex)
            val flags = method.accessFlags
            val isNative = flags and AccessFlags.NATIVE != 0
            val isImport = hasAnnotation(method, cf, "org/kgen/unmanaged/KgenImport")

            if (isImport && isNative) {
                val importName = getAnnotationStringValue(method, cf, "org/kgen/unmanaged/KgenImport", "value")
                val symbolName = if (!importName.isNullOrEmpty()) importName else name
                val paramTypes = BytecodeToIrLowering.parseDescriptorTypes(desc)
                val returnType = BytecodeToIrLowering.parseReturnType(desc)
                // Skip duplicate declarations (e.g., printf with different arity overloads)
                if (symbolName !in declaredImports) {
                    builder.declareFunction(symbolName, paramTypes.mapIndexed { i, t -> Param("p$i", t) }, returnType)
                    declaredImports.add(symbolName)
                }
                if (symbolName != name) importMap[name] = symbolName
            }
        }

        // Second pass: compile method bodies
        for (method in cf.methods) {
            val name = cf.string(method.nameIndex)
            val desc = cf.string(method.descriptorIndex)
            val flags = method.accessFlags
            val isStatic = flags and AccessFlags.STATIC != 0
            val isNative = flags and AccessFlags.NATIVE != 0

            if (isNative) continue

            val codeAttr = method.attributes.firstOrNull { cf.string(it.nameIndex) == "Code" }
                ?: continue

            val code = AttributeParser.parseCode(codeAttr, cf.constantPool)
            val isExported = hasAnnotation(method, cf, "org/kgen/unmanaged/KgenExport")
            val exportName = getAnnotationStringValue(method, cf, "org/kgen/unmanaged/KgenExport", "value")
            val isInline = hasAnnotation(method, cf, "org/kgen/unmanaged/KgenInline")
            val isLeaf = hasAnnotation(method, cf, "org/kgen/unmanaged/KgenLeaf")

            val attrs = mutableSetOf<FnAttribute>()
            if (isInline) attrs.add(FnAttribute.ALWAYSINLINE)
            if (isLeaf) attrs.add(FnAttribute.NOUNWIND)

            val irName = when {
                name == "<clinit>" -> "__clinit_$classPrefix"
                name == "<init>" -> "${classPrefix}_init"
                exportName != null && exportName.isNotEmpty() -> exportName
                !isStatic -> "${classPrefix}_$name"
                else -> name
            }

            BytecodeToIrLowering(builder, cf, irName, desc, code, isExported, attrs,
                isInstance = !isStatic, classLayout = classLayout, importMap = importMap).lower()

            if (name == "<clinit>") {
                builder.addGlobalCtor(irName)
            }
        }

        var module = builder.build()
        module = Mem2Reg().run(module)
        module = Inlining().run(module)
        return module
    }

    private fun hasAnnotation(method: MethodInfo, cf: ClassFile, annotationClass: String): Boolean {
        for (attr in method.attributes) {
            val attrName = cf.string(attr.nameIndex)
            if (attrName == "RuntimeVisibleAnnotations" || attrName == "RuntimeInvisibleAnnotations") {
                val data = attr.data
                val count = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                var pos = 2
                for (i in 0 until count) {
                    val typeIdx = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                    val typeName = cf.constantPool.utf8(typeIdx)
                    // Annotation type descriptor is like "Lorg/kgen/unmanaged/KgenExport;"
                    val className = typeName.removePrefix("L").removeSuffix(";")
                    if (className == annotationClass) return true
                    // Skip this annotation's element-value pairs
                    pos = skipAnnotation(data, pos + 2)
                }
            }
        }
        return false
    }

    private fun getAnnotationStringValue(
        method: MethodInfo, cf: ClassFile, annotationClass: String, elementName: String
    ): String? {
        for (attr in method.attributes) {
            val attrName = cf.string(attr.nameIndex)
            if (attrName == "RuntimeVisibleAnnotations" || attrName == "RuntimeInvisibleAnnotations") {
                val data = attr.data
                val count = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                var pos = 2
                for (i in 0 until count) {
                    val typeIdx = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                    val typeName = cf.constantPool.utf8(typeIdx)
                    val className = typeName.removePrefix("L").removeSuffix(";")
                    if (className == annotationClass) {
                        // Parse element-value pairs for this annotation
                        val numPairs = ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
                        var pairPos = pos + 4
                        for (j in 0 until numPairs) {
                            val nameIdx = ((data[pairPos].toInt() and 0xFF) shl 8) or (data[pairPos + 1].toInt() and 0xFF)
                            val name = cf.constantPool.utf8(nameIdx)
                            pairPos += 2
                            if (name == elementName) {
                                val tag = data[pairPos].toInt().toChar()
                                if (tag == 's') {
                                    val valIdx = ((data[pairPos + 1].toInt() and 0xFF) shl 8) or (data[pairPos + 2].toInt() and 0xFF)
                                    return cf.constantPool.utf8(valIdx)
                                }
                                return null
                            }
                            pairPos = skipElementValue(data, pairPos)
                        }
                        return null
                    }
                    pos = skipAnnotation(data, pos + 2)
                }
            }
        }
        return null
    }

    private fun skipAnnotation(data: ByteArray, start: Int): Int {
        var pos = start
        val numPairs = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2
        for (i in 0 until numPairs) {
            pos += 2 // element_name_index
            pos = skipElementValue(data, pos)
        }
        return pos
    }

    private fun skipElementValue(data: ByteArray, start: Int): Int {
        var pos = start
        val tag = data[pos].toInt().toChar()
        pos++
        when (tag) {
            'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 's' -> pos += 2
            'e' -> pos += 4
            'c' -> pos += 2
            '@' -> {
                pos += 2 // type_index
                pos = skipAnnotation(data, pos)
            }
            '[' -> {
                val count = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                pos += 2
                for (i in 0 until count) pos = skipElementValue(data, pos)
            }
        }
        return pos
    }
}

class RuntimeSubsetException(
    val className: String,
    val errors: List<SubsetValidator.ValidationError>,
) : Exception("Runtime subset violations in $className:\n${errors.joinToString("\n") { "  $it" }}")
