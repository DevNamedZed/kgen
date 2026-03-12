package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class QualifiedNameExtendedTest {

    // --- Simple names ---

    @Test
    fun singleCharName() {
        val name = QualifiedName.parse("x")
        assertEquals("x", name.name())
        assertNull(name.namespace())
    }

    @Test
    fun underscoreName() {
        val name = QualifiedName.parse("_start")
        assertEquals("_start", name.name())
        assertNull(name.namespace())
    }

    @Test
    fun nameWithDigits() {
        val name = QualifiedName.parse("func123")
        assertEquals("func123", name.name())
    }

    // --- CLR names ---

    @Test
    fun clrSingleNamespace() {
        val name = QualifiedName.parse("Foo.Bar")
        assertEquals("Bar", name.name())
        assertEquals("Foo", name.namespace())
    }

    @Test
    fun clrVeryDeepNamespace() {
        val name = QualifiedName.parse("A.B.C.D.E.F.G")
        assertEquals("G", name.name())
        assertEquals("A.B.C.D.E.F", name.namespace())
        assertEquals(7, name.segments().size)
    }

    @Test
    fun clrGenericArity3() {
        val name = QualifiedName.parse("System.Tuple`3")
        assertEquals("Tuple", name.name())
        assertEquals(3, name.genericArity())
        assertTrue(name.isGeneric())
    }

    @Test
    fun clrGenericArity0() {
        val name = QualifiedName.parse("System.String")
        assertFalse(name.isGeneric())
        assertEquals(0, name.genericArity())
    }

    @Test
    fun clrNestedGeneric() {
        val name = QualifiedName.parse("System.Collections.Generic.Dictionary`2+Enumerator")
        assertTrue(name.isNested())
        assertEquals("Enumerator", name.name())
        val outer = name.outerType()!!
        assertEquals("Dictionary`2", outer.rawName())
        assertEquals("Dictionary", outer.name())
        assertEquals(2, outer.genericArity())
    }

    @Test
    fun clrTripleNesting() {
        val name = QualifiedName.parse("NS.A+B+C+D")
        assertTrue(name.isNested())
        assertEquals("D", name.name())
        val c = name.outerType()!!
        assertEquals("C", c.name())
        val b = c.outerType()!!
        assertEquals("B", b.name())
        val a = b.outerType()!!
        assertEquals("A", a.name())
        assertEquals("NS", a.namespace())
    }

    // --- JVM names ---

    @Test
    fun jvmSinglePackage() {
        val name = QualifiedName.parse("com/Foo")
        assertEquals("Foo", name.name())
        assertEquals("com", name.namespace())
    }

    @Test
    fun jvmDeepPackage() {
        val name = QualifiedName.parse("org/apache/commons/lang3/StringUtils")
        assertEquals("StringUtils", name.name())
        assertEquals("org.apache.commons.lang3", name.namespace())
    }

    @Test
    fun jvmDoubleInnerClass() {
        // JVM inner class nesting only splits on first $
        val name = QualifiedName.parse("com/example/Outer\$Inner")
        assertTrue(name.isNested())
        assertEquals("Inner", name.name())
        val outer = name.outerType()!!
        assertEquals("Outer", outer.name())
        assertEquals("com.example", outer.namespace())
    }

    // --- C++ names ---

    @Test
    fun cppSingleName() {
        val name = QualifiedName.parse("std::cout")
        assertEquals("cout", name.name())
        assertEquals("std", name.namespace())
    }

    @Test
    fun cppTemplateMultipleArgs() {
        val name = QualifiedName.parse("std::unordered_map<std::string, std::vector<int>>")
        assertEquals("unordered_map", name.name())
        assertEquals(2, name.genericArity())
        val args = name.genericArguments()
        assertEquals("string", args[0].name())
        assertEquals("vector", args[1].name())
        assertEquals(1, args[1].genericArity())
    }

    @Test
    fun cppSimpleTemplateInt() {
        val name = QualifiedName.parse("std::array<int>")
        assertTrue(name.isGeneric())
        assertEquals(1, name.genericArity())
        assertEquals("int", name.genericArguments()[0].name())
    }

    // --- Factory methods ---

    @Test
    fun ofWithNamespaceAndGeneric() {
        val name = QualifiedName.of("System.Collections.Generic", "HashSet`1")
        assertEquals("HashSet", name.name())
        assertEquals(1, name.genericArity())
        assertEquals("System.Collections.Generic", name.namespace())
    }

    @Test
    fun ofWithEmptyNamespace() {
        val name = QualifiedName.of("", "main")
        assertEquals("main", name.name())
        assertNull(name.namespace())
    }

    // --- Matching / equality ---

    @Test
    fun matchingJvmToDot() {
        val jvm = QualifiedName.parse("java/util/List")
        val dot = QualifiedName.of("java.util", "List")
        assertEquals(jvm, dot)
    }

    @Test
    fun notMatchingDifferentNamespace() {
        val a = QualifiedName.parse("java/util/List")
        val b = QualifiedName.parse("java/awt/List")
        assertNotEquals(a, b)
    }

    @Test
    fun notMatchingDifferentName() {
        val a = QualifiedName.parse("System.String")
        val b = QualifiedName.parse("System.Object")
        assertNotEquals(a, b)
    }

    @Test
    fun equalityHashCodeConsistency() {
        val names = listOf(
            QualifiedName.parse("System.String"),
            QualifiedName.parse("java/lang/String"),
            QualifiedName.parse("std::string"),
            QualifiedName.parse("main"),
        )
        for (n in names) {
            val n2 = QualifiedName.parse(n.toString())
            assertEquals(n, n2)
            assertEquals(n.hashCode(), n2.hashCode())
        }
    }

    // --- Segments ---

    @Test
    fun segmentsCpp() {
        val name = QualifiedName.parse("boost::asio::ip::tcp")
        val segs = name.segments()
        assertTrue(segs.size >= 2)
        assertEquals("tcp", segs.last())
    }

    @Test
    fun segmentsSingle() {
        val name = QualifiedName.parse("main")
        assertEquals(listOf("main"), name.segments())
    }

    // --- JVM descriptors ---

    @Test
    fun jvmDescriptorAllPrimitives() {
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
    fun jvmDescriptor3DArray() {
        val name = QualifiedName.fromDescriptor("[[[I")
        assertTrue(name.isArray())
        assertEquals(3, name.arrayDimensions())
        assertEquals(QualifiedName.INT, name.elementType())
    }

    @Test
    fun jvmDescriptorObjectArrayMultiDim() {
        val name = QualifiedName.fromDescriptor("[[Ljava/lang/Object;")
        assertTrue(name.isArray())
        assertEquals(2, name.arrayDimensions())
        assertEquals("java.lang.Object", name.elementType()!!.fullName())
    }

    @Test
    fun jvmDescriptorInvalidCases() {
        assertThrows<IllegalArgumentException> { QualifiedName.fromDescriptor("") }
        assertThrows<IllegalArgumentException> { QualifiedName.fromDescriptor("Q") }
        assertThrows<IllegalArgumentException> { QualifiedName.fromDescriptor("Ljava/lang/String") }
    }

    // --- Primitives ---

    @Test
    fun primitivesArePrimitive() {
        val prims = listOf(
            QualifiedName.INT, QualifiedName.LONG, QualifiedName.SHORT,
            QualifiedName.BYTE, QualifiedName.CHAR, QualifiedName.BOOLEAN,
            QualifiedName.FLOAT, QualifiedName.DOUBLE, QualifiedName.VOID,
        )
        for (p in prims) {
            assertTrue(p.isPrimitive(), "${p.fullName()} should be primitive")
            assertNull(p.namespace())
        }
    }

    @Test
    fun nonPrimitivesAreNotPrimitive() {
        assertFalse(QualifiedName.parse("System.Collections.Generic.List`1").isPrimitive())
        assertFalse(QualifiedName.parse("java/util/ArrayList").isPrimitive())
    }

    // --- CLR aliases ---

    @Test
    fun clrAllAliases() {
        assertNotNull(QualifiedName.fromClrAlias("int"))
        assertNotNull(QualifiedName.fromClrAlias("uint"))
        assertNotNull(QualifiedName.fromClrAlias("long"))
        assertNotNull(QualifiedName.fromClrAlias("ulong"))
        assertNotNull(QualifiedName.fromClrAlias("short"))
        assertNotNull(QualifiedName.fromClrAlias("ushort"))
        assertNotNull(QualifiedName.fromClrAlias("byte"))
        assertNotNull(QualifiedName.fromClrAlias("sbyte"))
        assertNotNull(QualifiedName.fromClrAlias("bool"))
        assertNotNull(QualifiedName.fromClrAlias("char"))
        assertNotNull(QualifiedName.fromClrAlias("float"))
        assertNotNull(QualifiedName.fromClrAlias("double"))
        assertNotNull(QualifiedName.fromClrAlias("string"))
        assertNotNull(QualifiedName.fromClrAlias("object"))
        assertNotNull(QualifiedName.fromClrAlias("void"))
    }

    @Test
    fun clrAliasUnknown() {
        assertNull(QualifiedName.fromClrAlias("potato"))
        assertNull(QualifiedName.fromClrAlias(""))
    }

    // --- JVM well-known types ---

    @Test
    fun jvmWellKnownDetails() {
        assertEquals("java.lang", QualifiedName.JVM_STRING.namespace())
        assertEquals("String", QualifiedName.JVM_STRING.name())
        assertEquals("java.lang", QualifiedName.JVM_OBJECT.namespace())
        assertEquals("Object", QualifiedName.JVM_OBJECT.name())
    }

    // --- CLR well-known types ---

    @Test
    fun clrWellKnownDetails() {
        assertEquals("System", QualifiedName.CLR_INT32.namespace())
        assertEquals("Int32", QualifiedName.CLR_INT32.name())
        assertEquals("System", QualifiedName.CLR_STRING.namespace())
        assertEquals("String", QualifiedName.CLR_STRING.name())
    }

    // --- toString ---

    @Test
    fun toStringSimple() {
        assertEquals("strlen", QualifiedName.parse("strlen").toString())
    }

    @Test
    fun toStringClr() {
        assertEquals("System.String", QualifiedName.parse("System.String").toString())
    }

    @Test
    fun toStringJvm() {
        assertEquals("java.lang.String", QualifiedName.parse("java/lang/String").toString())
    }

    // --- Array type ---

    @Test
    fun nonArrayIsNotArray() {
        assertFalse(QualifiedName.parse("System.String").isArray())
        assertEquals(0, QualifiedName.parse("System.String").arrayDimensions())
        assertNull(QualifiedName.parse("System.String").elementType())
    }

    @Test
    fun arrayFullName() {
        val name = QualifiedName.fromDescriptor("[I")
        assertEquals("int[]", name.fullName())
    }

    @Test
    fun multiDimArrayFullName() {
        val name = QualifiedName.fromDescriptor("[[D")
        assertEquals("double[][]", name.fullName())
    }

    // --- isNested edge cases ---

    @Test
    fun simpleNameNotNested() {
        assertFalse(QualifiedName.parse("main").isNested())
        assertNull(QualifiedName.parse("main").outerType())
    }

    @Test
    fun dottedNameNotNested() {
        assertFalse(QualifiedName.parse("System.String").isNested())
    }
}
