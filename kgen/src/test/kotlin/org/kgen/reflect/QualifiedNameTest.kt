package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class QualifiedNameTest {

    // -- Simple names --

    @Test
    fun simpleName() {
        val name = QualifiedName.parse("strlen")
        assertEquals("strlen", name.name())
        assertEquals("strlen", name.simpleName())
        assertNull(name.namespace())
        assertEquals("strlen", name.fullName())
        assertFalse(name.isGeneric())
        assertFalse(name.isNested())
        assertEquals(0, name.genericArity())
    }

    @Test
    fun emptyThrows() {
        assertThrows<IllegalArgumentException> { QualifiedName.parse("") }
        assertThrows<IllegalArgumentException> { QualifiedName.parse("   ") }
    }

    // -- CLR dot-separated names --

    @Test
    fun clrSimple() {
        val name = QualifiedName.parse("System.String")
        assertEquals("String", name.name())
        assertEquals("String", name.simpleName())
        assertEquals("System", name.namespace())
        assertEquals("System.String", name.fullName())
    }

    @Test
    fun clrDeepNamespace() {
        val name = QualifiedName.parse("System.Collections.Generic.Dictionary")
        assertEquals("Dictionary", name.name())
        assertEquals("System.Collections.Generic", name.namespace())
        assertEquals("System.Collections.Generic.Dictionary", name.fullName())
    }

    @Test
    fun clrGenericArity() {
        val name = QualifiedName.parse("System.Collections.Generic.List`1")
        assertEquals("List`1", name.name())
        assertEquals("List", name.simpleName())
        assertEquals("System.Collections.Generic", name.namespace())
        assertTrue(name.isGeneric())
        assertEquals(1, name.genericArity())
    }

    @Test
    fun clrGenericArityTwo() {
        val name = QualifiedName.parse("System.Collections.Generic.Dictionary`2")
        assertEquals("Dictionary`2", name.name())
        assertEquals("Dictionary", name.simpleName())
        assertEquals(2, name.genericArity())
    }

    @Test
    fun clrNestedType() {
        val name = QualifiedName.parse("System.Environment+SpecialFolder")
        assertTrue(name.isNested())
        assertEquals("SpecialFolder", name.name())
        assertEquals("SpecialFolder", name.innerName())

        val outer = name.outerType()!!
        assertEquals("Environment", outer.name())
        assertEquals("System", outer.namespace())
        assertEquals("System.Environment", outer.fullName())
    }

    @Test
    fun clrDeeplyNested() {
        val name = QualifiedName.parse("A.B+C+D")
        assertTrue(name.isNested())
        assertEquals("D", name.name())

        val mid = name.outerType()!!
        assertTrue(mid.isNested())
        assertEquals("C", mid.name())

        val outer = mid.outerType()!!
        assertFalse(outer.isNested())
        assertEquals("B", outer.name())
        assertEquals("A", outer.namespace())
    }

    // -- JVM slash-separated names --

    @Test
    fun jvmSimple() {
        val name = QualifiedName.parse("java/lang/String")
        assertEquals("String", name.name())
        assertEquals("java.lang", name.namespace())
        assertEquals("java.lang.String", name.fullName())
    }

    @Test
    fun jvmInnerClass() {
        val name = QualifiedName.parse("java/util/Map\$Entry")
        assertTrue(name.isNested())
        assertEquals("Entry", name.name())

        val outer = name.outerType()!!
        assertEquals("Map", outer.name())
        assertEquals("java.util", outer.namespace())
    }

    @Test
    fun jvmDefaultPackage() {
        val name = QualifiedName.parse("MyClass")
        assertEquals("MyClass", name.name())
        assertNull(name.namespace())
    }

    // -- C++ names --

    @Test
    fun cppNamespace() {
        val name = QualifiedName.parse("std::vector")
        assertEquals("vector", name.name())
        assertEquals("std", name.namespace())
        assertEquals("std.vector", name.fullName())
    }

    @Test
    fun cppNestedNamespace() {
        val name = QualifiedName.parse("boost::asio::ip::tcp")
        assertEquals("tcp", name.name())
        assertEquals("boost::asio::ip", name.namespace())
    }

    @Test
    fun cppTemplate() {
        val name = QualifiedName.parse("std::vector<int>")
        assertEquals("vector", name.name())
        assertEquals("std", name.namespace())
        assertTrue(name.isGeneric())
        assertEquals(1, name.genericArity())

        val args = name.genericArguments()
        assertEquals(1, args.size)
        assertEquals("int", args[0].name())
    }

    @Test
    fun cppNestedTemplate() {
        val name = QualifiedName.parse("std::map<std::string, int>")
        assertEquals("map", name.name())
        assertEquals(2, name.genericArity())

        val args = name.genericArguments()
        assertEquals("string", args[0].name())
        assertEquals("std", args[0].namespace())
        assertEquals("int", args[1].name())
    }

    @Test
    fun cppTemplateOfTemplates() {
        val name = QualifiedName.parse("std::vector<std::pair<int, double>>")
        assertEquals("vector", name.name())
        assertEquals(1, name.genericArity())

        val inner = name.genericArguments()[0]
        assertEquals("pair", inner.name())
        assertEquals(2, inner.genericArity())
        assertEquals("int", inner.genericArguments()[0].name())
        assertEquals("double", inner.genericArguments()[1].name())
    }

    // -- Factory methods --

    @Test
    fun ofNamespaceAndName() {
        val name = QualifiedName.of("System.Collections.Generic", "List`1")
        assertEquals("List`1", name.name())
        assertEquals("List", name.simpleName())
        assertEquals("System.Collections.Generic", name.namespace())
        assertEquals(1, name.genericArity())
    }

    @Test
    fun ofFullName() {
        val name = QualifiedName.of("System.String")
        assertEquals("String", name.name())
        assertEquals("System", name.namespace())
    }

    @Test
    fun ofSimpleName() {
        val name = QualifiedName.of("strlen")
        assertEquals("strlen", name.name())
        assertNull(name.namespace())
    }

    // -- Matching --

    @Test
    fun matchingSameFormat() {
        val a = QualifiedName.parse("System.String")
        val b = QualifiedName.parse("System.String")
        assertTrue(a.matches(b))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun matchingCrossFormat() {
        val jvm = QualifiedName.parse("java/lang/String")
        val dot = QualifiedName.of("java.lang", "String")
        assertTrue(jvm.matches(dot))
        assertEquals(jvm, dot)
    }

    @Test
    fun notMatching() {
        val a = QualifiedName.parse("System.String")
        val b = QualifiedName.parse("System.Int32")
        assertFalse(a.matches(b))
        assertNotEquals(a, b)
    }

    // -- Segments --

    @Test
    fun segmentsClr() {
        val name = QualifiedName.parse("System.Collections.Generic.List`1")
        assertEquals(listOf("System", "Collections", "Generic", "List`1"), name.segments())
    }

    @Test
    fun segmentsJvm() {
        val name = QualifiedName.parse("java/lang/String")
        assertEquals(listOf("java", "lang", "String"), name.segments())
    }

    @Test
    fun segmentsSimple() {
        val name = QualifiedName.parse("strlen")
        assertEquals(listOf("strlen"), name.segments())
    }

    // -- JVM descriptors --

    @Test
    fun jvmDescriptorPrimitive() {
        assertEquals(QualifiedName.INT, QualifiedName.fromDescriptor("I"))
        assertEquals(QualifiedName.LONG, QualifiedName.fromDescriptor("J"))
        assertEquals(QualifiedName.BOOLEAN, QualifiedName.fromDescriptor("Z"))
        assertEquals(QualifiedName.BYTE, QualifiedName.fromDescriptor("B"))
        assertEquals(QualifiedName.CHAR, QualifiedName.fromDescriptor("C"))
        assertEquals(QualifiedName.SHORT, QualifiedName.fromDescriptor("S"))
        assertEquals(QualifiedName.FLOAT, QualifiedName.fromDescriptor("F"))
        assertEquals(QualifiedName.DOUBLE, QualifiedName.fromDescriptor("D"))
        assertEquals(QualifiedName.VOID, QualifiedName.fromDescriptor("V"))
    }

    @Test
    fun jvmDescriptorObject() {
        val name = QualifiedName.fromDescriptor("Ljava/lang/String;")
        assertEquals("String", name.name())
        assertEquals("java.lang", name.namespace())
        assertEquals("java.lang.String", name.fullName())
    }

    @Test
    fun jvmDescriptorArray() {
        val name = QualifiedName.fromDescriptor("[I")
        assertTrue(name.isArray())
        assertEquals(1, name.arrayDimensions())
        assertEquals(QualifiedName.INT, name.elementType())
        assertEquals("int[]", name.fullName())
    }

    @Test
    fun jvmDescriptorMultiDimArray() {
        val name = QualifiedName.fromDescriptor("[[D")
        assertTrue(name.isArray())
        assertEquals(2, name.arrayDimensions())
        assertEquals(QualifiedName.DOUBLE, name.elementType())
        assertEquals("double[][]", name.fullName())
    }

    @Test
    fun jvmDescriptorObjectArray() {
        val name = QualifiedName.fromDescriptor("[Ljava/lang/String;")
        assertTrue(name.isArray())
        assertEquals("java.lang.String", name.elementType()!!.fullName())
        assertEquals("java.lang.String[]", name.fullName())
    }

    @Test
    fun jvmDescriptorInvalid() {
        assertThrows<IllegalArgumentException> { QualifiedName.fromDescriptor("") }
        assertThrows<IllegalArgumentException> { QualifiedName.fromDescriptor("X") }
        assertThrows<IllegalArgumentException> { QualifiedName.fromDescriptor("Ljava/lang/String") }
    }

    // -- JVM well-known types --

    @Test
    fun jvmPrimitiveConstants() {
        assertEquals("int", QualifiedName.INT.fullName())
        assertEquals("boolean", QualifiedName.BOOLEAN.fullName())
        assertEquals("void", QualifiedName.VOID.fullName())
        assertTrue(QualifiedName.INT.isPrimitive())
        assertTrue(QualifiedName.BOOLEAN.isPrimitive())
        assertNull(QualifiedName.INT.namespace())
    }

    @Test
    fun jvmWellKnownTypes() {
        assertEquals("java.lang.String", QualifiedName.JVM_STRING.fullName())
        assertEquals("java.lang.Object", QualifiedName.JVM_OBJECT.fullName())
        assertEquals("java.lang.Class", QualifiedName.JVM_CLASS.fullName())
    }

    // -- CLR well-known types --

    @Test
    fun clrPrimitiveConstants() {
        assertEquals("System.Int32", QualifiedName.CLR_INT32.fullName())
        assertEquals("System.Boolean", QualifiedName.CLR_BOOL.fullName())
        assertEquals("System.String", QualifiedName.CLR_STRING.fullName())
        assertEquals("System.Void", QualifiedName.CLR_VOID.fullName())
        assertTrue(QualifiedName.CLR_INT32.isPrimitive())
    }

    @Test
    fun clrAliasResolution() {
        assertEquals(QualifiedName.CLR_INT32, QualifiedName.fromClrAlias("int"))
        assertEquals(QualifiedName.CLR_BOOL, QualifiedName.fromClrAlias("bool"))
        assertEquals(QualifiedName.CLR_STRING, QualifiedName.fromClrAlias("string"))
        assertEquals(QualifiedName.CLR_DOUBLE, QualifiedName.fromClrAlias("double"))
        assertEquals(QualifiedName.CLR_VOID, QualifiedName.fromClrAlias("void"))
        assertEquals(QualifiedName.CLR_INTPTR, QualifiedName.fromClrAlias("nint"))
        assertNull(QualifiedName.fromClrAlias("notareal"))
    }

    // -- Array support --

    @Test
    fun arrayNameNotConfusedWithParse() {
        val arr = QualifiedName.fromDescriptor("[Ljava/util/List;")
        assertTrue(arr.isArray())
        assertFalse(arr.isNested())
        assertEquals("java.util.List[]", arr.fullName())
    }

    // -- toString --

    @Test
    fun toStringIsFullName() {
        val name = QualifiedName.parse("System.String")
        assertEquals("System.String", name.toString())
    }
}
