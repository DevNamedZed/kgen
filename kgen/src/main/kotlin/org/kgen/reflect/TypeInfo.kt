package org.kgen.reflect

/**
 * Unified type reflection across JVM, CLR, native (DWARF/PDB), and WASM.
 *
 * Represents a type with its members, hierarchy, and metadata. Works the
 * same whether the source is CLR metadata, JVM class files, or DWARF debug info.
 *
 * ```java
 * var type = module.type("System.String");
 * type.name();               // "String"
 * type.namespace();          // "System"
 * type.methods();            // List<MethodInfo>
 * type.fields();             // List<FieldInfo>
 * type.baseType();           // TypeInfo?
 * ```
 */
class TypeInfo(
    private val qname: QualifiedName,
    private val kind: TypeKind = TypeKind.CLASS,
    private val ownerModule: Module? = null,
    private val base: TypeInfo? = null,
    private val ifaces: List<TypeInfo> = emptyList(),
    private val nested: List<TypeInfo> = emptyList(),
    private val methods: List<MethodInfo> = emptyList(),
    private val fields: List<FieldInfo> = emptyList(),
    private val properties: List<PropertyInfo> = emptyList(),
    private val events: List<EventInfo> = emptyList(),
    private val constructors: List<MethodInfo> = emptyList(),
    private val attrs: List<AttributeInfo> = emptyList(),
    private val genericArgs: List<TypeRef> = emptyList(),
    private val flags: Set<TypeFlag> = emptySet(),
    private val instanceSize: Int = 0,
    private val packing: Int = 0,
) {
    // -- Identity --

    fun name(): String = qname.name()
    fun namespace(): String? = qname.namespace()
    fun qualifiedName(): QualifiedName = qname
    fun fullName(): String = qname.fullName()
    fun module(): Module? = ownerModule

    // -- Hierarchy --

    fun baseType(): TypeInfo? = base
    fun interfaces(): List<TypeInfo> = ifaces
    fun nestedTypes(): List<TypeInfo> = nested

    // -- Classification --

    fun kind(): TypeKind = kind
    fun isClass(): Boolean = kind == TypeKind.CLASS
    fun isInterface(): Boolean = kind == TypeKind.INTERFACE
    fun isEnum(): Boolean = kind == TypeKind.ENUM
    fun isStruct(): Boolean = kind == TypeKind.STRUCT
    fun isDelegate(): Boolean = kind == TypeKind.DELEGATE
    fun isAnnotation(): Boolean = kind == TypeKind.ANNOTATION

    fun isAbstract(): Boolean = TypeFlag.ABSTRACT in flags
    fun isSealed(): Boolean = TypeFlag.SEALED in flags
    fun isPublic(): Boolean = TypeFlag.PUBLIC in flags
    fun isInternal(): Boolean = TypeFlag.INTERNAL in flags
    fun isGeneric(): Boolean = genericArgs.isNotEmpty() || qname.isGeneric()

    fun genericArguments(): List<TypeRef> = genericArgs

    // -- Members --

    fun methods(): List<MethodInfo> = methods
    fun method(name: String): MethodInfo? = methods.firstOrNull { it.name() == name }
    fun fields(): List<FieldInfo> = fields
    fun field(name: String): FieldInfo? = fields.firstOrNull { it.name() == name }
    fun properties(): List<PropertyInfo> = properties
    fun property(name: String): PropertyInfo? = properties.firstOrNull { it.name() == name }
    fun events(): List<EventInfo> = events
    fun event(name: String): EventInfo? = events.firstOrNull { it.name() == name }
    fun constructors(): List<MethodInfo> = constructors

    // -- Attributes --

    fun attributes(): List<AttributeInfo> = attrs
    fun attribute(name: String): AttributeInfo? = attrs.firstOrNull { it.name() == name }

    // -- Layout --

    fun size(): Int = instanceSize
    fun packingSize(): Int = packing

    // -- TypeRef conversion --

    fun toTypeRef(): TypeRef = TypeRef.of(qname)

    override fun toString(): String = fullName()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeInfo) return false
        return qname == other.qname
    }

    override fun hashCode(): Int = qname.hashCode()

    companion object {
        @JvmStatic
        fun builder(name: QualifiedName): Builder = Builder(name)

        @JvmStatic
        fun builder(name: String): Builder = Builder(QualifiedName.parse(name))
    }

    class Builder(private val name: QualifiedName) {
        private var kind: TypeKind = TypeKind.CLASS
        private var module: Module? = null
        private var base: TypeInfo? = null
        private val ifaces = mutableListOf<TypeInfo>()
        private val nested = mutableListOf<TypeInfo>()
        private val methods = mutableListOf<MethodInfo>()
        private val fields = mutableListOf<FieldInfo>()
        private val properties = mutableListOf<PropertyInfo>()
        private val events = mutableListOf<EventInfo>()
        private val constructors = mutableListOf<MethodInfo>()
        private val attrs = mutableListOf<AttributeInfo>()
        private val genericArgs = mutableListOf<TypeRef>()
        private val flags = mutableSetOf<TypeFlag>()
        private var instanceSize = 0
        private var packing = 0

        fun kind(kind: TypeKind) = apply { this.kind = kind }
        fun module(module: Module?) = apply { this.module = module }
        fun baseType(base: TypeInfo?) = apply { this.base = base }
        fun addInterface(iface: TypeInfo) = apply { ifaces.add(iface) }
        fun addNestedType(type: TypeInfo) = apply { nested.add(type) }
        fun addMethod(method: MethodInfo) = apply { methods.add(method) }
        fun addField(field: FieldInfo) = apply { fields.add(field) }
        fun addProperty(prop: PropertyInfo) = apply { properties.add(prop) }
        fun addEvent(event: EventInfo) = apply { events.add(event) }
        fun addConstructor(ctor: MethodInfo) = apply { constructors.add(ctor) }
        fun addAttribute(attr: AttributeInfo) = apply { attrs.add(attr) }
        fun addGenericArg(arg: TypeRef) = apply { genericArgs.add(arg) }
        fun addFlag(flag: TypeFlag) = apply { flags.add(flag) }
        fun size(size: Int) = apply { instanceSize = size }
        fun packingSize(packing: Int) = apply { this.packing = packing }

        fun build(): TypeInfo = TypeInfo(
            qname = name,
            kind = kind,
            ownerModule = module,
            base = base,
            ifaces = ifaces.toList(),
            nested = nested.toList(),
            methods = methods.toList(),
            fields = fields.toList(),
            properties = properties.toList(),
            events = events.toList(),
            constructors = constructors.toList(),
            attrs = attrs.toList(),
            genericArgs = genericArgs.toList(),
            flags = flags.toSet(),
            instanceSize = instanceSize,
            packing = packing,
        )
    }
}

enum class TypeKind {
    CLASS, INTERFACE, ENUM, STRUCT, DELEGATE, ANNOTATION,
}

enum class TypeFlag {
    PUBLIC, INTERNAL, ABSTRACT, SEALED, STATIC,
}
