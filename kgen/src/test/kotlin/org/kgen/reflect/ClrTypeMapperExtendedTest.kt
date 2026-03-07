package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class ClrTypeMapperExtendedTest {

    private fun loadFixture(name: String): Module {
        val bytes = javaClass.getResourceAsStream("/fixtures/clr/$name")!!.readAllBytes()
        return Module.fromBytes(bytes, name)
    }

    // -- Type discovery from live fixture --

    @Test
    fun typesAreNonEmpty() {
        val module = loadFixture("TestLib.dll")
        assertTrue(module.types().isNotEmpty())
    }

    @Test
    fun noModulePseudoType() {
        val module = loadFixture("TestLib.dll")
        assertTrue(module.types().none { it.fullName() == "<Module>" })
    }

    @Test
    fun allTypesHaveNonEmptyNames() {
        val module = loadFixture("TestLib.dll")
        for (type in module.types()) {
            assertTrue(type.name().isNotEmpty())
            assertTrue(type.fullName().isNotEmpty())
        }
    }

    @Test
    fun allTypesHaveKind() {
        val module = loadFixture("TestLib.dll")
        for (type in module.types()) {
            assertNotNull(type.kind())
        }
    }

    @Test
    fun allTypesReferenceOwnerModule() {
        val module = loadFixture("TestLib.dll")
        for (type in module.types()) {
            assertEquals(module, type.module())
        }
    }

    @Test
    fun atLeastOneTypeHasMethods() {
        val module = loadFixture("TestLib.dll")
        assertTrue(module.types().any { it.methods().isNotEmpty() })
    }

    @Test
    fun atLeastOneTypeHasConstructors() {
        val module = loadFixture("TestLib.dll")
        assertTrue(module.types().any { it.constructors().isNotEmpty() })
    }

    @Test
    fun methodsHaveNames() {
        val module = loadFixture("TestLib.dll")
        for (type in module.types()) {
            for (method in type.methods()) {
                assertTrue(method.name().isNotEmpty())
            }
        }
    }

    @Test
    fun constructorsHaveConstructorFlag() {
        val module = loadFixture("TestLib.dll")
        for (type in module.types()) {
            for (ctor in type.constructors()) {
                assertTrue(ctor.isConstructor(), "Constructor ${ctor.name()} should have CONSTRUCTOR flag")
            }
        }
    }

    @Test
    fun typeLookupByFirstType() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        if (types.isNotEmpty()) {
            val name = types[0].fullName()
            val found = module.type(name)
            assertNotNull(found)
            assertEquals(name, found!!.fullName())
        }
    }

    @Test
    fun typeLookupMissingReturnsNull() {
        val module = loadFixture("TestLib.dll")
        assertNull(module.type("This.Type.Does.Not.Exist"))
    }

    @Test
    fun typeFieldsAccessible() {
        val module = loadFixture("TestLib.dll")
        for (type in module.types()) {
            assertNotNull(type.fields())
        }
    }

    // -- Synthetic ClrTypeMapper unit tests --

    @Test
    fun mapEmptyMetadataProducesNoTypes() {
        val clr = org.kgen.binary.pe.clr.ClrMetadata(
            majorRuntimeVersion = 2,
            minorRuntimeVersion = 5,
            flags = 1,
            entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = org.kgen.binary.pe.clr.ClrTables(),
            strings = org.kgen.binary.pe.clr.ClrStringHeap(ByteArray(1)),
            blobs = org.kgen.binary.pe.clr.ClrBlobHeap(ByteArray(1)),
            guids = org.kgen.binary.pe.clr.ClrGuidHeap(ByteArray(0)),
            userStrings = org.kgen.binary.pe.clr.ClrUserStringHeap(ByteArray(1)),
        )
        assertTrue(ClrTypeMapper.map(clr, null).isEmpty())
    }

    @Test
    fun mapWithNullModule() {
        val clr = org.kgen.binary.pe.clr.ClrMetadata(
            majorRuntimeVersion = 2,
            minorRuntimeVersion = 5,
            flags = 1,
            entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = org.kgen.binary.pe.clr.ClrTables(),
            strings = org.kgen.binary.pe.clr.ClrStringHeap(ByteArray(1)),
            blobs = org.kgen.binary.pe.clr.ClrBlobHeap(ByteArray(1)),
            guids = org.kgen.binary.pe.clr.ClrGuidHeap(ByteArray(0)),
            userStrings = org.kgen.binary.pe.clr.ClrUserStringHeap(ByteArray(1)),
        )
        val types = ClrTypeMapper.map(clr, null)
        assertTrue(types.isEmpty())
        // Ensure no NPE with null module
    }

    // -- TypeInfo builder tests (unit tests for the reflect model) --

    @Test
    fun typeInfoBuilderBasic() {
        val info = TypeInfo.builder("System.String")
            .kind(TypeKind.CLASS)
            .addFlag(TypeFlag.PUBLIC)
            .addFlag(TypeFlag.SEALED)
            .build()
        assertEquals("System.String", info.fullName())
        assertEquals("String", info.name())
        assertEquals("System", info.namespace())
        assertTrue(info.isClass())
        assertTrue(info.isPublic())
        assertTrue(info.isSealed())
    }

    @Test
    fun typeInfoBuilderInterface() {
        val info = TypeInfo.builder("System.IDisposable")
            .kind(TypeKind.INTERFACE)
            .addFlag(TypeFlag.PUBLIC)
            .addFlag(TypeFlag.ABSTRACT)
            .build()
        assertTrue(info.isInterface())
        assertTrue(info.isAbstract())
        assertFalse(info.isClass())
    }

    @Test
    fun typeInfoBuilderEnum() {
        val info = TypeInfo.builder("System.DayOfWeek")
            .kind(TypeKind.ENUM)
            .build()
        assertTrue(info.isEnum())
        assertFalse(info.isStruct())
    }

    @Test
    fun typeInfoBuilderStruct() {
        val info = TypeInfo.builder("System.Int32")
            .kind(TypeKind.STRUCT)
            .build()
        assertTrue(info.isStruct())
        assertFalse(info.isClass())
    }

    @Test
    fun typeInfoBuilderWithMethods() {
        val method = MethodInfo("ToString", returnType = TypeRef.of("System.String"))
        val info = TypeInfo.builder("MyType")
            .addMethod(method)
            .build()
        assertEquals(1, info.methods().size)
        assertEquals("ToString", info.methods()[0].name())
        assertNotNull(info.method("ToString"))
        assertNull(info.method("NonExistent"))
    }

    @Test
    fun typeInfoBuilderWithFields() {
        val field = FieldInfo("value", TypeRef.I32, flags = setOf(FieldFlag.PRIVATE))
        val info = TypeInfo.builder("MyType")
            .addField(field)
            .build()
        assertEquals(1, info.fields().size)
        assertEquals("value", info.fields()[0].name())
        assertNotNull(info.field("value"))
        assertNull(info.field("missing"))
    }

    @Test
    fun typeInfoBuilderWithConstructors() {
        val ctor = MethodInfo(".ctor", flags = setOf(MethodFlag.PUBLIC, MethodFlag.CONSTRUCTOR))
        val info = TypeInfo.builder("MyType")
            .addConstructor(ctor)
            .build()
        assertEquals(1, info.constructors().size)
        assertTrue(info.constructors()[0].isConstructor())
    }

    @Test
    fun typeInfoBuilderWithBaseType() {
        val baseType = TypeInfo.builder("System.Object").build()
        val info = TypeInfo.builder("MyType")
            .baseType(baseType)
            .build()
        assertNotNull(info.baseType())
        assertEquals("System.Object", info.baseType()!!.fullName())
    }

    @Test
    fun typeInfoBuilderWithInterfaces() {
        val iface = TypeInfo.builder("System.IDisposable")
            .kind(TypeKind.INTERFACE)
            .build()
        val info = TypeInfo.builder("MyType")
            .addInterface(iface)
            .build()
        assertEquals(1, info.interfaces().size)
        assertEquals("System.IDisposable", info.interfaces()[0].fullName())
    }

    @Test
    fun typeInfoBuilderWithGenericArgs() {
        val info = TypeInfo.builder("System.Collections.Generic.List`1")
            .addGenericArg(TypeRef.genericParam("T"))
            .build()
        assertTrue(info.isGeneric())
        assertEquals(1, info.genericArguments().size)
    }

    @Test
    fun typeInfoToTypeRef() {
        val info = TypeInfo.builder("System.String").build()
        val ref = info.toTypeRef()
        assertEquals("System.String", ref.fullName())
    }

    @Test
    fun typeInfoEquality() {
        val a = TypeInfo.builder("System.String").build()
        val b = TypeInfo.builder("System.String").build()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun typeInfoInequality() {
        val a = TypeInfo.builder("System.String").build()
        val b = TypeInfo.builder("System.Int32").build()
        assertNotEquals(a, b)
    }

    @Test
    fun typeInfoToString() {
        val info = TypeInfo.builder("System.String").build()
        assertEquals("System.String", info.toString())
    }

    @Test
    fun typeInfoSize() {
        val info = TypeInfo.builder("MyStruct")
            .kind(TypeKind.STRUCT)
            .size(16)
            .packingSize(4)
            .build()
        assertEquals(16, info.size())
        assertEquals(4, info.packingSize())
    }
}
