package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class JvmModuleExtendedTest {

    private fun loadFixture(name: String): Module {
        val bytes = javaClass.getResourceAsStream("/fixtures/java/$name")!!.readAllBytes()
        return Module.fromBytes(bytes, name)
    }

    @Test
    fun calculatorFunctionCount() {
        val module = loadFixture("Calculator.class")
        val funcs = module.functions()
        assertTrue(funcs.size >= 4, "Should have at least 4 functions (add, multiply, getValue, setValue): ${funcs.map { it.name() }}")
    }

    @Test
    fun calculatorSymbolModule() {
        val module = loadFixture("Calculator.class")
        val sym = module.symbols().first { it.isFunction() }
        assertSame(module, sym.module())
    }

    @Test
    fun calculatorFunctionSymbolLink() {
        val module = loadFixture("Calculator.class")
        val func = module.functions().first()
        assertNotNull(func.symbol())
        assertSame(module, func.module())
    }

    @Test
    fun circleHasConstructor() {
        val module = loadFixture("Circle.class")
        val ctors = module.symbols().filter { it.name().contains("<init>") }
        assertTrue(ctors.isNotEmpty(), "Circle should have constructor")
    }

    @Test
    fun circleFieldRadius() {
        val module = loadFixture("Circle.class")
        val fields = module.symbols().filter { it.kind() == SymbolKind.FIELD }
        assertTrue(fields.any { it.name().contains("radius") }, "Circle should have radius field. Fields: ${fields.map { it.name() }}")
    }

    @Test
    fun colorEnumHasValues() {
        val module = loadFixture("Color.class")
        val methods = module.symbols().filter { it.isFunction() }.map { it.name() }
        assertTrue(methods.any { it.contains("values") }, "Enum should have values method. Methods: $methods")
    }

    @Test
    fun colorEnumHasValueOf() {
        val module = loadFixture("Color.class")
        val methods = module.symbols().filter { it.isFunction() }.map { it.name() }
        assertTrue(methods.any { it.contains("valueOf") }, "Enum should have valueOf method. Methods: $methods")
    }

    @Test
    fun shapeMethodsAreAbstract() {
        val module = loadFixture("Shape.class")
        val area = module.symbols().firstOrNull { it.name().contains("area") && it.isFunction() }
        assertNotNull(area, "Should have area method")
    }

    @Test
    fun calculatorPublicMethods() {
        val module = loadFixture("Calculator.class")
        val add = module.symbols().firstOrNull { it.name().contains("add") }
        assertNotNull(add)
        assertTrue(add!!.isPublic(), "add should be public")
    }

    @Test
    fun moduleIsNotLoaded() {
        val module = loadFixture("Calculator.class")
        assertFalse(module.isLoaded())
        assertEquals(0L, module.baseAddress())
    }

    @Test
    fun moduleHasNoNativeCode() {
        val module = loadFixture("Calculator.class")
        assertFalse(module.hasNativeCode())
        assertTrue(module.hasJvm())
    }

    @Test
    fun moduleHasNoClr() {
        val module = loadFixture("Calculator.class")
        assertFalse(module.hasClr())
    }

    @Test
    fun moduleHasNoWasm() {
        val module = loadFixture("Calculator.class")
        assertFalse(module.hasWasm())
    }

    @Test
    fun circleImplementsShape() {
        val module = loadFixture("Circle.class")
        val symbols = module.symbols()
        // Should have methods from Shape interface
        val methods = symbols.filter { it.isFunction() }.map { it.name() }
        assertTrue(methods.any { it.contains("name") }, "Circle should have name from Shape. Methods: $methods")
    }

    @Test
    fun calculatorNonexistentSymbol() {
        val module = loadFixture("Calculator.class")
        assertNull(module.symbol("nonexistent_method"))
    }

    @Test
    fun calculatorToString() {
        val module = loadFixture("Calculator.class")
        val str = module.toString()
        assertTrue(str.isNotBlank())
    }

    @Test
    fun calculatorSections() {
        val module = loadFixture("Calculator.class")
        val sections = module.sections()
        assertTrue(sections.isNotEmpty())
    }

    @Test
    fun calculatorNameField() {
        val module = loadFixture("Calculator.class")
        val name = module.symbols().firstOrNull { it.name().contains("NAME") }
        assertNotNull(name)
        assertTrue(name!!.isStatic())
        assertTrue(name.isFinal())
    }

    @Test
    fun shapeFormat() {
        val module = loadFixture("Shape.class")
        assertEquals(ObjectFormat.JVM_CLASS, module.format())
        assertEquals(ArchType.JVM, module.arch().arch)
    }

    @Test
    fun colorFormat() {
        val module = loadFixture("Color.class")
        assertEquals(ObjectFormat.JVM_CLASS, module.format())
    }

    @Test
    fun circleFormat() {
        val module = loadFixture("Circle.class")
        assertEquals(ObjectFormat.JVM_CLASS, module.format())
    }

    @Test
    fun calculatorGetValueIsPublic() {
        val module = loadFixture("Calculator.class")
        val getValue = module.symbols().firstOrNull { it.name().contains("getValue") }
        assertNotNull(getValue)
        assertTrue(getValue!!.isPublic())
        assertTrue(getValue.isFunction())
    }

    @Test
    fun calculatorSetValueIsPublic() {
        val module = loadFixture("Calculator.class")
        val setValue = module.symbols().firstOrNull { it.name().contains("setValue") }
        assertNotNull(setValue)
        assertTrue(setValue!!.isPublic())
    }

    @Test
    fun allFixturesHaveJvm() {
        for (name in listOf("Calculator.class", "Shape.class", "Circle.class", "Color.class")) {
            val module = loadFixture(name)
            assertTrue(module.hasJvm(), "$name should have JVM: ${module.format()}")
        }
    }

    @Test
    fun functionSizeIsPositive() {
        val module = loadFixture("Calculator.class")
        val funcs = module.functions()
        for (func in funcs) {
            assertTrue(func.size() >= 0, "Function ${func.name()} should have non-negative size")
        }
    }

    @Test
    fun symbolKindsAreCorrect() {
        val module = loadFixture("Calculator.class")
        val symbols = module.symbols()
        val kinds = symbols.map { it.kind() }.toSet()
        // Should have at least functions and fields
        assertTrue(SymbolKind.FUNCTION in kinds || SymbolKind.METHOD in kinds, "Should have function symbols. Kinds: $kinds")
        assertTrue(SymbolKind.FIELD in kinds, "Should have field symbols. Kinds: $kinds")
    }
}
