package org.kgen.examples.api

import org.kgen.ir.Type
import org.kgen.ir.instructions.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IrBuilderJavaExampleTest {

    @Test
    fun addFunctionMatchesKotlinVersion() {
        val javaModule = IrBuilderJavaExample.buildAddFunction()
        val kotlinModule = IrBuilderExample.buildAddFunction()

        assertEquals(kotlinModule.name, javaModule.name)
        assertEquals(kotlinModule.functions.size, javaModule.functions.size)
        assertEquals(kotlinModule.functions[0].name, javaModule.functions[0].name)
    }

    @Test
    fun absFunctionHasBranching() {
        val module = IrBuilderJavaExample.buildAbsFunction()
        val function = module.functions[0]

        assertEquals("abs", function.name)
        assertTrue(function.blocks.size >= 4)

        val allInstructions = function.blocks.flatMap { it.instructions }
        assertTrue(allInstructions.any { it is ICmp })
        assertTrue(allInstructions.any { it is CondBr })
        assertTrue(allInstructions.any { it is Phi })
    }

    @Test
    fun globalVariableExampleHasGlobal() {
        val module = IrBuilderJavaExample.buildGlobalVariableExample()

        val counter = module.globals.find { it.name == "counter" }
        assertNotNull(counter)
        assertEquals(Type.I32, counter.type)
    }

    @Test
    fun functionCallExampleHasTwoFunctions() {
        val module = IrBuilderJavaExample.buildFunctionCallExample()

        assertEquals(2, module.functions.size)
        assertEquals("square", module.functions[0].name)
        assertEquals("sum_of_squares", module.functions[1].name)
    }
}
