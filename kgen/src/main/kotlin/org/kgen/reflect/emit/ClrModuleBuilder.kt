package org.kgen.reflect.emit

import org.kgen.reflect.Signature
import org.kgen.reflect.TypeFlag
import org.kgen.target.clr.asm.CilToken

/**
 * Builds a .NET assembly with types, methods, and fields.
 *
 * ```java
 * ClrModuleBuilder mod = ModuleBuilder.clr("MyLib");
 * ClrTypeBuilder type = mod.defineType("MyNamespace.Calculator");
 *
 * CilToken countField = type.defineField("count", TypeRef.I32,
 *     FieldFlag.PRIVATE, FieldFlag.STATIC);
 *
 * ClrMethodBuilder add = type.defineMethod("Add",
 *     Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32),
 *     MethodFlag.PUBLIC, MethodFlag.STATIC);
 * add.il().ldarg(0);
 * add.il().ldarg(1);
 * add.il().add();
 * add.il().ret();
 *
 * byte[] dll = mod.toBytes();
 * ```
 */
class ClrModuleBuilder(name: String) : ModuleBuilder(name) {

    private var typeBuilder: ClrTypeBuilder? = null
    private var entryPointMethod: ClrMethodBuilder? = null

    fun defineType(fullName: String, vararg flags: TypeFlag): ClrTypeBuilder {
        require(typeBuilder == null) { "Only one type per module is currently supported" }
        val tb = ClrTypeBuilder(this, fullName, flags.toSet())
        typeBuilder = tb
        return tb
    }

    fun setEntryPoint(method: ClrMethodBuilder): ClrModuleBuilder {
        entryPointMethod = method
        return this
    }

    fun addTypeRef(assemblyRefIndex: Int, name: String, namespace: String): Int {
        val tb = typeBuilder ?: error("Define a type before adding type refs")
        return tb.addTypeRef(assemblyRefIndex, name, namespace)
    }

    @JvmOverloads
    fun addMemberRef(typeRefIndex: Int, name: String, signature: Signature, instance: Boolean = false): CilToken {
        val tb = typeBuilder ?: error("Define a type before adding member refs")
        return tb.addMemberRef(typeRefIndex, name, signature, instance)
    }

    override fun toBytes(): ByteArray {
        val tb = typeBuilder ?: error("No type defined")
        val builder = tb.toCilClassBuilder(name)
        val ep = entryPointMethod
        if (ep != null) {
            builder.entryPoint(tb.methodIndex(ep))
        }
        return builder.toBytes()
    }
}
