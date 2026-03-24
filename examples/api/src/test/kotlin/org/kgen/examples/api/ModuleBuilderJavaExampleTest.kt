package org.kgen.examples.api

import org.kgen.ir.Type
import org.kgen.ir.instructions.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModuleBuilderJavaExampleTest {

    @Test
    fun addFunctionMatchesKotlinVersion() {
        val javaModule = ModuleBuilderJavaExample.buildAddFunction()
        val kotlinModule = ModuleBuilderExample.buildAddFunction()

        assertEquals(kotlinModule.name, javaModule.name)
        assertEquals(kotlinModule.functions.size, javaModule.functions.size)
        assertEquals(kotlinModule.functions[0].name, javaModule.functions[0].name)
    }

    @Test
    fun absFunctionUsesSelect() {
        val module = ModuleBuilderJavaExample.buildAbsFunction()
        val function = module.functions[0]

        assertEquals("abs", function.name)

        val allInstructions = function.blocks.flatMap { it.instructions }
        assertTrue(allInstructions.any { it is ICmp })
        assertTrue(allInstructions.any { it is Select })
    }

    @Test
    fun globalVariableExampleHasGlobal() {
        val module = ModuleBuilderJavaExample.buildGlobalVariableExample()

        val counter = module.globals.find { it.name == "counter" }
        assertNotNull(counter)
        assertEquals(Type.I32, counter.type)
    }

    @Test
    fun classExampleProducesClassAndFunctions() {
        val module = ModuleBuilderJavaExample.buildClassExample()

        assertTrue(module.classes.isNotEmpty())
        assertNotNull(module.classes.find { it.name == "Counter" })
        assertTrue(module.functions.any { it.name == "Counter_increment" })
    }

    @Test
    fun loopExampleProducesLoopStructure() {
        val module = ModuleBuilderJavaExample.buildLoopExample()
        val function = module.functions[0]

        assertEquals("sum_to_n", function.name)
        assertTrue(function.blocks.size >= 3)

        val allInstructions = function.blocks.flatMap { it.instructions }
        assertTrue(allInstructions.any { it is Alloca })
        assertTrue(allInstructions.any { it is ICmp })
        assertTrue(allInstructions.any { it is CondBr })
    }

    @Test
    fun profileExample() {
        val module = ModuleBuilderJavaExample.buildWithProfile()
        assertEquals(1, module.functions.size)
        assertEquals("identity", module.functions[0].name)
    }
}
