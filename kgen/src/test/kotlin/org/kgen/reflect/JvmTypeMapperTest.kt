package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class JvmTypeMapperTest {

    private fun loadFixture(name: String): Module {
        val bytes = javaClass.getResourceAsStream("/fixtures/java/$name")!!.readAllBytes()
        return Module.fromBytes(bytes, name)
    }

    // -- Format-specific accessors --

    @Test
    fun classFileAccessor() {
        val module = loadFixture("Calculator.class")
        val cf = module.classFile()
        assertNotNull(cf, "Should have classFile accessor")
        assertTrue(cf!!.thisClassName.contains("Calculator"))
    }

    @Test
    fun elfAccessorNull() {
        val module = loadFixture("Calculator.class")
        assertNull(module.elf(), "JVM class should not have ELF model")
        assertNull(module.pe(), "JVM class should not have PE model")
        assertNull(module.machO(), "JVM class should not have Mach-O model")
        assertNull(module.clr(), "JVM class should not have CLR metadata")
        assertNull(module.wasm(), "JVM class should not have WASM model")
    }

    // -- Module.types() for JVM --

    @Test
    fun calculatorTypeDiscovery() {
        val module = loadFixture("Calculator.class")
        val types = module.types()
        assertEquals(1, types.size, "Should have exactly one type")
        val type = types[0]
        assertTrue(type.fullName().contains("Calculator"), "Type name: ${type.fullName()}")
    }

    @Test
    fun calculatorTypeKind() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        assertTrue(type.isClass())
        assertFalse(type.isInterface())
        assertFalse(type.isEnum())
    }

    @Test
    fun calculatorTypeIsPublic() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        assertTrue(type.isPublic())
    }

    @Test
    fun calculatorMethods() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val methods = type.methods()
        val methodNames = methods.map { it.name() }

        assertTrue(methodNames.contains("add"), "Should have add. Methods: $methodNames")
        assertTrue(methodNames.contains("multiply"), "Should have multiply. Methods: $methodNames")
        assertTrue(methodNames.contains("getValue"), "Should have getValue. Methods: $methodNames")
        assertTrue(methodNames.contains("setValue"), "Should have setValue. Methods: $methodNames")
        assertTrue(methodNames.contains("oldMethod"), "Should have oldMethod. Methods: $methodNames")
    }

    @Test
    fun calculatorStaticMethod() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val multiply = type.method("multiply")
        assertNotNull(multiply, "Should have multiply method")
        assertTrue(multiply!!.isPublic())
        assertTrue(multiply.isStatic())
    }

    @Test
    fun calculatorInstanceMethod() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val add = type.method("add")
        assertNotNull(add, "Should have add method")
        assertTrue(add!!.isPublic())
        assertFalse(add.isStatic())
    }

    @Test
    fun calculatorConstructors() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val ctors = type.constructors()
        assertTrue(ctors.isNotEmpty(), "Should have constructors")
        assertTrue(ctors.all { it.isConstructor() })
    }

    @Test
    fun calculatorFields() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val fields = type.fields()
        val fieldNames = fields.map { it.name() }
        assertTrue(fieldNames.contains("value"), "Should have value field. Fields: $fieldNames")
        assertTrue(fieldNames.contains("MAX_VALUE"), "Should have MAX_VALUE. Fields: $fieldNames")
    }

    @Test
    fun calculatorPrivateField() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val valueField = type.field("value")
        assertNotNull(valueField)
        assertTrue(valueField!!.isPrivate())
        assertFalse(valueField.isStatic())
    }

    @Test
    fun calculatorConstField() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val maxVal = type.field("MAX_VALUE")
        assertNotNull(maxVal)
        assertTrue(maxVal!!.isPublic())
        assertTrue(maxVal.isStatic())
        assertTrue(maxVal.isReadOnly(), "final field should be readonly")
    }

    @Test
    fun shapeInterface() {
        val module = loadFixture("Shape.class")
        val types = module.types()
        assertEquals(1, types.size)
        val type = types[0]
        assertTrue(type.isInterface(), "Shape should be interface. Kind: ${type.kind()}")
        assertTrue(type.isPublic())
    }

    @Test
    fun shapeInterfaceMethods() {
        val module = loadFixture("Shape.class")
        val type = module.types()[0]
        val methods = type.methods()
        val names = methods.map { it.name() }
        assertTrue(names.contains("area"), "Should have area. Methods: $names")
        assertTrue(names.contains("perimeter"), "Should have perimeter. Methods: $names")
    }

    @Test
    fun colorEnum() {
        val module = loadFixture("Color.class")
        val types = module.types()
        assertEquals(1, types.size)
        val type = types[0]
        assertTrue(type.isEnum(), "Color should be enum. Kind: ${type.kind()}")
    }

    @Test
    fun colorEnumFields() {
        val module = loadFixture("Color.class")
        val type = module.types()[0]
        val fields = type.fields()
        val names = fields.map { it.name() }
        assertTrue(names.contains("RED"), "Should have RED. Fields: $names")
        assertTrue(names.contains("GREEN"), "Should have GREEN. Fields: $names")
        assertTrue(names.contains("BLUE"), "Should have BLUE. Fields: $names")
    }

    @Test
    fun typeLookupByName() {
        val module = loadFixture("Calculator.class")
        val type = module.type(module.types()[0].fullName())
        assertNotNull(type, "Should find type by name")
    }

    // -- Method descriptor parsing --

    @Test
    fun parseVoidNoArgs() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("()V")
        assertTrue(ret.isVoid())
        assertTrue(params.isEmpty())
    }

    @Test
    fun parseIntIntToInt() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("(II)I")
        assertEquals(TypeRef.I32, ret)
        assertEquals(2, params.size)
        assertEquals(TypeRef.I32, params[0])
        assertEquals(TypeRef.I32, params[1])
    }

    @Test
    fun parseLongToLong() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("(J)J")
        assertEquals(TypeRef.I64, ret)
        assertEquals(1, params.size)
        assertEquals(TypeRef.I64, params[0])
    }

    @Test
    fun parseObjectParam() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("(Ljava/lang/String;)V")
        assertTrue(ret.isVoid())
        assertEquals(1, params.size)
        assertEquals("java.lang.String", params[0].fullName())
    }

    @Test
    fun parseArrayParam() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("([I)V")
        assertTrue(ret.isVoid())
        assertEquals(1, params.size)
        assertTrue(params[0].isArray())
        assertEquals("i32[]", params[0].fullName())
    }

    @Test
    fun parseMixedParams() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("(ILjava/lang/String;D)Z")
        assertEquals(TypeRef.BOOL, ret)
        assertEquals(3, params.size)
        assertEquals(TypeRef.I32, params[0])
        assertEquals("java.lang.String", params[1].fullName())
        assertEquals(TypeRef.F64, params[2])
    }

    @Test
    fun descriptorToTypeRefPrimitives() {
        assertEquals(TypeRef.I32, JvmTypeMapper.descriptorToTypeRef("I"))
        assertEquals(TypeRef.I64, JvmTypeMapper.descriptorToTypeRef("J"))
        assertEquals(TypeRef.F64, JvmTypeMapper.descriptorToTypeRef("D"))
        assertEquals(TypeRef.F32, JvmTypeMapper.descriptorToTypeRef("F"))
        assertEquals(TypeRef.BOOL, JvmTypeMapper.descriptorToTypeRef("Z"))
        assertEquals(TypeRef.I8, JvmTypeMapper.descriptorToTypeRef("B"))
        assertEquals(TypeRef.I16, JvmTypeMapper.descriptorToTypeRef("S"))
    }

    @Test
    fun descriptorToTypeRefObject() {
        val ref = JvmTypeMapper.descriptorToTypeRef("Ljava/util/List;")
        assertEquals("java.util.List", ref.fullName())
    }

    @Test
    fun descriptorToTypeRefArray() {
        val ref = JvmTypeMapper.descriptorToTypeRef("[Ljava/lang/String;")
        assertTrue(ref.isArray())
        assertEquals("java.lang.String[]", ref.fullName())
    }

    @Test
    fun methodReturnType() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val add = type.method("add")
        assertNotNull(add)
        assertEquals(TypeRef.I32, add!!.returnType())
    }

    @Test
    fun methodParameters() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val add = type.method("add")
        assertNotNull(add)
        assertEquals(2, add!!.parameterCount())
        assertEquals(TypeRef.I32, add.parameters()[0].type())
        assertEquals(TypeRef.I32, add.parameters()[1].type())
    }

    @Test
    fun methodSignature() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val add = type.method("add")
        assertNotNull(add)
        val sig = add!!.signature()
        assertEquals(TypeRef.I32, sig.returnType())
        assertEquals(2, sig.parameterCount())
    }

    @Test
    fun moduleOwnership() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        assertEquals(module, type.module())
    }
}
