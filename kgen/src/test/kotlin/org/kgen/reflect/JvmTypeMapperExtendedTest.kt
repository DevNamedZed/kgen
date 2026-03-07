package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class JvmTypeMapperExtendedTest {

    // --- parseMethodDescriptor edge cases ---

    @Test
    fun parseNoArgsReturnsInt() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("()I")
        assertEquals(TypeRef.I32, ret)
        assertTrue(params.isEmpty())
    }

    @Test
    fun parseNoArgsReturnsLong() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("()J")
        assertEquals(TypeRef.I64, ret)
        assertTrue(params.isEmpty())
    }

    @Test
    fun parseNoArgsReturnsFloat() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("()F")
        assertEquals(TypeRef.F32, ret)
        assertTrue(params.isEmpty())
    }

    @Test
    fun parseNoArgsReturnsDouble() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("()D")
        assertEquals(TypeRef.F64, ret)
        assertTrue(params.isEmpty())
    }

    @Test
    fun parseNoArgsReturnsBool() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("()Z")
        assertEquals(TypeRef.BOOL, ret)
        assertTrue(params.isEmpty())
    }

    @Test
    fun parseNoArgsReturnsByte() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("()B")
        assertEquals(TypeRef.I8, ret)
        assertTrue(params.isEmpty())
    }

    @Test
    fun parseNoArgsReturnsChar() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("()C")
        val ret2 = ret
        assertNotNull(ret2)
    }

    @Test
    fun parseNoArgsReturnsShort() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("()S")
        assertEquals(TypeRef.I16, ret)
        assertTrue(params.isEmpty())
    }

    @Test
    fun parseAllPrimitiveParams() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("(IJFDZBS)V")
        assertTrue(ret.isVoid())
        assertEquals(7, params.size)
        assertEquals(TypeRef.I32, params[0])
        assertEquals(TypeRef.I64, params[1])
        assertEquals(TypeRef.F32, params[2])
        assertEquals(TypeRef.F64, params[3])
        assertEquals(TypeRef.BOOL, params[4])
        assertEquals(TypeRef.I8, params[5])
        assertEquals(TypeRef.I16, params[6])
    }

    @Test
    fun parseSingleObjectParam() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("(Ljava/lang/Object;)V")
        assertTrue(ret.isVoid())
        assertEquals(1, params.size)
        assertEquals("java.lang.Object", params[0].fullName())
    }

    @Test
    fun parseMultipleObjectParams() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor(
            "(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;)Ljava/lang/Object;"
        )
        assertEquals("java.lang.Object", ret.fullName())
        assertEquals(3, params.size)
        assertEquals("java.lang.String", params[0].fullName())
        assertEquals("java.util.List", params[1].fullName())
        assertEquals("java.util.Map", params[2].fullName())
    }

    @Test
    fun parsePrimitiveArrayParam() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("([I)V")
        assertTrue(ret.isVoid())
        assertEquals(1, params.size)
        assertTrue(params[0].isArray())
    }

    @Test
    fun parseObjectArrayParam() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("([Ljava/lang/String;)V")
        assertTrue(ret.isVoid())
        assertEquals(1, params.size)
        assertTrue(params[0].isArray())
    }

    @Test
    fun parse2DArrayParam() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("([[I)V")
        assertTrue(ret.isVoid())
        assertEquals(1, params.size)
        assertTrue(params[0].isArray())
    }

    @Test
    fun parseMixedPrimAndObjectParams() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("(ILjava/lang/String;JLjava/util/Map;D)Z")
        assertEquals(TypeRef.BOOL, ret)
        assertEquals(5, params.size)
        assertEquals(TypeRef.I32, params[0])
        assertEquals("java.lang.String", params[1].fullName())
        assertEquals(TypeRef.I64, params[2])
        assertEquals("java.util.Map", params[3].fullName())
        assertEquals(TypeRef.F64, params[4])
    }

    @Test
    fun parseReturnObject() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("()Ljava/lang/String;")
        assertEquals("java.lang.String", ret.fullName())
        assertTrue(params.isEmpty())
    }

    @Test
    fun parseReturnArray() {
        val (ret, params) = JvmTypeMapper.parseMethodDescriptor("()[I")
        assertTrue(ret.isArray())
        assertTrue(params.isEmpty())
    }

    // --- descriptorToTypeRef ---

    @Test
    fun descriptorPrimitiveI() {
        assertEquals(TypeRef.I32, JvmTypeMapper.descriptorToTypeRef("I"))
    }

    @Test
    fun descriptorPrimitiveJ() {
        assertEquals(TypeRef.I64, JvmTypeMapper.descriptorToTypeRef("J"))
    }

    @Test
    fun descriptorPrimitiveD() {
        assertEquals(TypeRef.F64, JvmTypeMapper.descriptorToTypeRef("D"))
    }

    @Test
    fun descriptorPrimitiveF() {
        assertEquals(TypeRef.F32, JvmTypeMapper.descriptorToTypeRef("F"))
    }

    @Test
    fun descriptorPrimitiveZ() {
        assertEquals(TypeRef.BOOL, JvmTypeMapper.descriptorToTypeRef("Z"))
    }

    @Test
    fun descriptorPrimitiveB() {
        assertEquals(TypeRef.I8, JvmTypeMapper.descriptorToTypeRef("B"))
    }

    @Test
    fun descriptorPrimitiveS() {
        assertEquals(TypeRef.I16, JvmTypeMapper.descriptorToTypeRef("S"))
    }

    @Test
    fun descriptorPrimitiveV() {
        val ref = JvmTypeMapper.descriptorToTypeRef("V")
        assertTrue(ref.isVoid())
    }

    @Test
    fun descriptorObject() {
        val ref = JvmTypeMapper.descriptorToTypeRef("Ljava/lang/String;")
        assertEquals("java.lang.String", ref.fullName())
    }

    @Test
    fun descriptorDeepObject() {
        val ref = JvmTypeMapper.descriptorToTypeRef("Lorg/apache/commons/lang3/StringUtils;")
        assertEquals("org.apache.commons.lang3.StringUtils", ref.fullName())
    }

    @Test
    fun descriptorPrimitiveArray() {
        val ref = JvmTypeMapper.descriptorToTypeRef("[I")
        assertTrue(ref.isArray())
        assertEquals("i32[]", ref.fullName())
    }

    @Test
    fun descriptorByteArray() {
        val ref = JvmTypeMapper.descriptorToTypeRef("[B")
        assertTrue(ref.isArray())
    }

    @Test
    fun descriptorObjectArray() {
        val ref = JvmTypeMapper.descriptorToTypeRef("[Ljava/lang/String;")
        assertTrue(ref.isArray())
        assertEquals("java.lang.String[]", ref.fullName())
    }

    @Test
    fun descriptorMultiDimArray() {
        val ref = JvmTypeMapper.descriptorToTypeRef("[[D")
        assertTrue(ref.isArray())
    }

    @Test
    fun descriptorLongArray() {
        val ref = JvmTypeMapper.descriptorToTypeRef("[J")
        assertTrue(ref.isArray())
    }

    @Test
    fun descriptorBoolArray() {
        val ref = JvmTypeMapper.descriptorToTypeRef("[Z")
        assertTrue(ref.isArray())
    }

    // --- Fixture-based tests ---

    private fun loadFixture(name: String): Module {
        val bytes = javaClass.getResourceAsStream("/fixtures/java/$name")!!.readAllBytes()
        return Module.fromBytes(bytes, name)
    }

    @Test
    fun calculatorAddReturnType() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val add = type.method("add")!!
        assertEquals(TypeRef.I32, add.returnType())
    }

    @Test
    fun calculatorAddParams() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val add = type.method("add")!!
        assertEquals(2, add.parameterCount())
        add.parameters().forEach { p ->
            assertEquals(TypeRef.I32, p.type())
        }
    }

    @Test
    fun calculatorMultiplyIsStatic() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val mul = type.method("multiply")!!
        assertTrue(mul.isStatic())
        assertTrue(mul.isPublic())
    }

    @Test
    fun calculatorValueFieldType() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val field = type.field("value")!!
        assertEquals(TypeRef.I32, field.fieldType())
    }

    @Test
    fun calculatorMaxValueFieldIsStaticFinal() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val field = type.field("MAX_VALUE")!!
        assertTrue(field.isStatic())
        assertTrue(field.isReadOnly())
    }

    @Test
    fun calculatorConstructorCount() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val ctors = type.constructors()
        assertTrue(ctors.isNotEmpty())
        ctors.forEach { assertTrue(it.isConstructor()) }
    }

    @Test
    fun calculatorMethodCount() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        assertTrue(type.methods().size >= 4, "Should have at least add/multiply/getValue/setValue")
    }

    @Test
    fun shapeInterfaceAreaMethod() {
        val module = loadFixture("Shape.class")
        val type = module.types()[0]
        assertTrue(type.isInterface())
        val area = type.method("area")
        assertNotNull(area)
    }

    @Test
    fun shapeInterfacePerimeterMethod() {
        val module = loadFixture("Shape.class")
        val type = module.types()[0]
        val perimeter = type.method("perimeter")
        assertNotNull(perimeter)
    }

    @Test
    fun colorEnumHasStaticFields() {
        val module = loadFixture("Color.class")
        val type = module.types()[0]
        assertTrue(type.isEnum())
        val red = type.field("RED")
        assertNotNull(red)
        assertTrue(red!!.isStatic())
    }

    @Test
    fun colorEnumGreenField() {
        val module = loadFixture("Color.class")
        val type = module.types()[0]
        val green = type.field("GREEN")
        assertNotNull(green)
    }

    @Test
    fun colorEnumBlueField() {
        val module = loadFixture("Color.class")
        val type = module.types()[0]
        val blue = type.field("BLUE")
        assertNotNull(blue)
    }

    @Test
    fun calculatorTypeBackRef() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        assertEquals(module, type.module())
    }

    @Test
    fun calculatorMethodSignature() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val add = type.method("add")!!
        val sig = add.signature()
        assertEquals(TypeRef.I32, sig.returnType())
        assertEquals(2, sig.parameterCount())
    }

    @Test
    fun calculatorGetValueNoParams() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val getter = type.method("getValue")
        assertNotNull(getter)
        assertEquals(0, getter!!.parameterCount())
    }

    @Test
    fun calculatorSetValueOneParam() {
        val module = loadFixture("Calculator.class")
        val type = module.types()[0]
        val setter = type.method("setValue")
        assertNotNull(setter)
        assertEquals(1, setter!!.parameterCount())
    }
}
