package org.kgen.examples.api

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClassBuilderExampleTest {

    @Test
    fun nativeClassHasExpectedStructure() {
        val module = ClassBuilderExample.nativeClass()

        val cls = module.classes.find { it.name == "Vec2" }
        assertNotNull(cls)
        assertEquals(2, cls.fields.size)
        assertTrue(cls.methods.any { it.name == "length" })
        assertTrue(cls.methods.any { it.name == "op_plus" })
        assertTrue(cls.methods.any { it.name == "get_x" })
        assertTrue(cls.methods.any { it.name == "set_x" })
        assertTrue(cls.staticMethods.any { it.name == "zero" })
        assertEquals(1, cls.constructors.size)

        assertTrue(module.functions.any { it.name == "Vec2_init" })
        assertTrue(module.functions.any { it.name == "Vec2_length" })
        assertTrue(module.functions.any { it.name == "Vec2_op_plus" })
        assertTrue(module.functions.any { it.name == "Vec2_get_x" })
        assertTrue(module.functions.any { it.name == "Vec2_set_x" })
    }

    @Test
    fun managedClassHasObjectInstructions() {
        val module = ClassBuilderExample.managedClass()

        val cls = module.classes.find { it.name == "User" }
        assertNotNull(cls)
        assertEquals("Object", cls.superClass)
        assertEquals(2, cls.fields.size)
        assertTrue(cls.methods.any { it.name == "getName" })
        assertTrue(cls.methods.any { it.name == "isAdult" })
    }

    @Test
    fun classHierarchyHasInheritance() {
        val module = ClassBuilderExample.classHierarchy()

        assertEquals(2, module.classes.size)
        val shape = module.classes.find { it.name == "Shape" }
        val circle = module.classes.find { it.name == "Circle" }
        assertNotNull(shape)
        assertNotNull(circle)
        assertTrue(shape.isAbstract)
        assertTrue(circle.isFinal)
        assertEquals("Shape", circle.superClass)
    }

    @Test
    fun imperativeStyleWorks() {
        val module = ClassBuilderExample.imperativeStyle()
        assertTrue(module.functions.any { it.name == "Counter_increment" })
    }
}
