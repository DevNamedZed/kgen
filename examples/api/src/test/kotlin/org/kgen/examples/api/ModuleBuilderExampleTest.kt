package org.kgen.examples.api

import org.kgen.ir.Type
import org.kgen.ir.instructions.*
import org.kgen.ir.text.IrPrinter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModuleBuilderExampleTest {

    @Test
    fun addFunctionProducesValidModule() {
        val module = ModuleBuilderExample.buildAddFunction()

        assertEquals("add_example", module.name)
        assertEquals(1, module.functions.size)

        val function = module.functions[0]
        assertEquals("add", function.name)
        assertEquals(Type.I32, function.returnType)
        assertEquals(2, function.params.size)

        val allInstructions = function.blocks.flatMap { it.instructions }
        assertTrue(allInstructions.any { it is Add })
        assertTrue(allInstructions.any { it is Ret })
    }

    @Test
    fun addFunctionIrTextIsWellFormed() {
        val irText = IrPrinter.print(ModuleBuilderExample.buildAddFunction())

        assertTrue(irText.contains("define i32 @add"))
        assertTrue(irText.contains("add i32"))
        assertTrue(irText.contains("ret i32"))
    }

    @Test
    fun absFunctionHasBranching() {
        val module = ModuleBuilderExample.buildAbsFunction()
        val function = module.functions[0]

        assertEquals("abs", function.name)
        assertTrue(function.blocks.size >= 2)

        val allInstructions = function.blocks.flatMap { it.instructions }
        assertTrue(allInstructions.any { it is ICmp })
        assertTrue(allInstructions.any { it is CondBr })
    }

    @Test
    fun globalVariableExampleHasGlobal() {
        val module = ModuleBuilderExample.buildGlobalVariableExample()

        assertTrue(module.globals.isNotEmpty())
        val counter = module.globals.find { it.name == "counter" }
        assertNotNull(counter)
        assertEquals(Type.I32, counter.type)

        val function = module.functions[0]
        assertEquals("increment", function.name)

        val allInstructions = function.blocks.flatMap { it.instructions }
        assertTrue(allInstructions.any { it is Load })
        assertTrue(allInstructions.any { it is Store })
    }

    @Test
    fun functionCallExampleHasTwoFunctions() {
        val module = ModuleBuilderExample.buildFunctionCallExample()

        assertEquals(2, module.functions.size)
        assertEquals("square", module.functions[0].name)
        assertEquals("sum_of_squares", module.functions[1].name)

        val mainFunction = module.functions[1]
        val allInstructions = mainFunction.blocks.flatMap { it.instructions }
        assertTrue(allInstructions.any { it is Call })
        assertTrue(allInstructions.any { it is Add })
    }

    @Test
    fun classExampleProducesClassAndFunctions() {
        val module = ModuleBuilderExample.buildClassExample()

        assertTrue(module.classes.isNotEmpty())
        val cls = module.classes.find { it.name == "Counter" }
        assertNotNull(cls)
        assertEquals(1, cls.fields.size)
        assertEquals("value", cls.fields[0].name)

        assertTrue(module.functions.any { it.name == "Counter_create" })
        assertTrue(module.functions.any { it.name == "Counter_increment" })
    }

    @Test
    fun loopExampleProducesLoopStructure() {
        val module = ModuleBuilderExample.buildLoopExample()

        val function = module.functions[0]
        assertEquals("sum_to_n", function.name)

        assertTrue(function.blocks.size >= 3)

        val allInstructions = function.blocks.flatMap { it.instructions }
        assertTrue(allInstructions.any { it is Alloca })
        assertTrue(allInstructions.any { it is ICmp })
        assertTrue(allInstructions.any { it is CondBr })
    }
}
