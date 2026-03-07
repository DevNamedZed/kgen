package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

/**
 * End-to-end tests: .class fixture files → Module → reflect API.
 */
class JvmModuleTest {

    private fun loadFixture(name: String): Module {
        val bytes = javaClass.getResourceAsStream("/fixtures/java/$name")!!.readAllBytes()
        return Module.fromBytes(bytes, name)
    }

    @Test
    fun loadCalculatorClass() {
        val module = loadFixture("Calculator.class")
        assertEquals("Calculator.class", module.name())
        assertEquals(ObjectFormat.JVM_CLASS, module.format())
        assertTrue(module.hasJvm())
        assertFalse(module.hasClr())
        assertFalse(module.hasNativeCode())
    }

    @Test
    fun calculatorSymbols() {
        val module = loadFixture("Calculator.class")
        val symbols = module.symbols()
        assertTrue(symbols.isNotEmpty())

        // Class symbol
        val classSym = symbols.firstOrNull { it.name() == "fixtures.java.Calculator" }
        assertNotNull(classSym, "Should have class symbol. Symbols: ${symbols.map { it.name() }}")
    }

    @Test
    fun calculatorMethods() {
        val module = loadFixture("Calculator.class")
        val symbols = module.symbols()
        val methodNames = symbols.filter { it.isFunction() }.map { it.name() }

        assertTrue(methodNames.any { it.contains("add") }, "Should have add method. Methods: $methodNames")
        assertTrue(methodNames.any { it.contains("multiply") }, "Should have multiply method. Methods: $methodNames")
        assertTrue(methodNames.any { it.contains("getValue") }, "Should have getValue method. Methods: $methodNames")
        assertTrue(methodNames.any { it.contains("setValue") }, "Should have setValue method. Methods: $methodNames")
    }

    @Test
    fun calculatorFields() {
        val module = loadFixture("Calculator.class")
        val symbols = module.symbols()
        val fieldSyms = symbols.filter { it.kind() == SymbolKind.FIELD }
        val fieldNames = fieldSyms.map { it.name() }

        assertTrue(fieldNames.any { it.contains("value") }, "Should have value field. Fields: $fieldNames")
        assertTrue(fieldNames.any { it.contains("MAX_VALUE") }, "Should have MAX_VALUE field. Fields: $fieldNames")
        assertTrue(fieldNames.any { it.contains("NAME") }, "Should have NAME field. Fields: $fieldNames")
    }

    @Test
    fun calculatorStaticMethod() {
        val module = loadFixture("Calculator.class")
        val multiply = module.symbols().firstOrNull { it.name().contains("multiply") }
        assertNotNull(multiply)
        assertTrue(multiply!!.isStatic(), "multiply should be static")
        assertTrue(multiply.isPublic(), "multiply should be public")
    }

    @Test
    fun calculatorConstructors() {
        val module = loadFixture("Calculator.class")
        val ctors = module.symbols().filter { it.name().contains("<init>") }
        assertTrue(ctors.isNotEmpty(), "Should have constructors")
    }

    @Test
    fun loadShapeInterface() {
        val module = loadFixture("Shape.class")
        assertTrue(module.hasJvm())
        val classSym = module.symbols().firstOrNull { it.kind() == SymbolKind.INTERFACE }
        assertNotNull(classSym, "Shape should be an interface. Symbols: ${module.symbols().map { "${it.name()}:${it.kind()}" }}")
    }

    @Test
    fun shapeInterfaceMethods() {
        val module = loadFixture("Shape.class")
        val methods = module.symbols().filter { it.isFunction() }.map { it.name() }
        assertTrue(methods.any { it.contains("area") }, "Should have area. Methods: $methods")
        assertTrue(methods.any { it.contains("perimeter") }, "Should have perimeter. Methods: $methods")
        assertTrue(methods.any { it.contains("name") }, "Should have name. Methods: $methods")
    }

    @Test
    fun loadCircleClass() {
        val module = loadFixture("Circle.class")
        assertTrue(module.hasJvm())
        val symbols = module.symbols()

        // Has methods from implementing Shape
        val methods = symbols.filter { it.isFunction() }.map { it.name() }
        assertTrue(methods.any { it.contains("area") }, "Should have area. Methods: $methods")
        assertTrue(methods.any { it.contains("perimeter") }, "Should have perimeter. Methods: $methods")
        assertTrue(methods.any { it.contains("getRadius") }, "Should have getRadius. Methods: $methods")
    }

    @Test
    fun loadColorEnum() {
        val module = loadFixture("Color.class")
        assertTrue(module.hasJvm())

        // Enum fields
        val fields = module.symbols().filter { it.kind() == SymbolKind.FIELD }
        val fieldNames = fields.map { it.name() }
        assertTrue(fieldNames.any { it.contains("RED") }, "Should have RED. Fields: $fieldNames")
        assertTrue(fieldNames.any { it.contains("GREEN") }, "Should have GREEN. Fields: $fieldNames")
        assertTrue(fieldNames.any { it.contains("BLUE") }, "Should have BLUE. Fields: $fieldNames")
    }

    @Test
    fun privateFieldIsPrivate() {
        val module = loadFixture("Calculator.class")
        val valueField = module.symbols().firstOrNull { it.name().endsWith(".value") }
        assertNotNull(valueField, "Should have value field")
        assertTrue(valueField!!.isPrivate(), "value should be private")
    }

    @Test
    fun staticFinalField() {
        val module = loadFixture("Calculator.class")
        val maxVal = module.symbols().firstOrNull { it.name().contains("MAX_VALUE") }
        assertNotNull(maxVal)
        assertTrue(maxVal!!.isStatic(), "MAX_VALUE should be static")
        assertTrue(maxVal.isFinal(), "MAX_VALUE should be final")
        assertTrue(maxVal.isPublic(), "MAX_VALUE should be public")
    }

    @Test
    fun jvmArchitecture() {
        val module = loadFixture("Calculator.class")
        assertEquals(ArchType.JVM, module.arch().arch)
    }

    @Test
    fun functionNavigationFromModule() {
        val module = loadFixture("Calculator.class")
        val funcs = module.functions()
        assertTrue(funcs.isNotEmpty(), "Should have functions")
        val addFunc = funcs.firstOrNull { it.name().contains("add") }
        assertNotNull(addFunc, "Should have add function. Funcs: ${funcs.map { it.name() }}")
    }
}
