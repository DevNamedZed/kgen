package org.kgen.integration.loader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.jit.FfmNativeLoader

class NativeLoaderTest {

    private val loader = FfmNativeLoader()
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    private fun generateObjectFile(block: IrBuilder.() -> Unit): org.kgen.binary.ObjectFile {
        val ir = IrBuilder("jit_test", Target.x86_64())
        if (isWindows) ir.targetTriple = "x86_64-unknown-windows-msvc"
        ir.block()
        val module = ir.build()
        return X86CodeGenerator().generateObjectFile(module)
    }

    @Test
    fun addTwoNumbers() {
        val obj = generateObjectFile {
            val params = createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val sum = add(params[0], params[1])
            ret(sum)
            finalizeFunction()
        }

        loader.loadObjectFile(obj).use { module ->
            val addFn = module.findFunction("add")
            assertNotNull(addFn)
            assertEquals(7L, addFn!!.callLong(3, 4))
            assertEquals(0L, addFn.callLong(0, 0))
            assertEquals(-1L, addFn.callLong(5, -6))
            assertEquals(100L, addFn.callLong(42, 58))
        }
    }

    @Test
    fun subtractTwoNumbers() {
        val obj = generateObjectFile {
            val params = createFunction("subtract", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val diff = sub(params[0], params[1])
            ret(diff)
            finalizeFunction()
        }

        loader.loadObjectFile(obj).use { module ->
            val fn = module.findFunction("subtract")!!
            assertEquals(2L, fn.callLong(5, 3))
            assertEquals(-3L, fn.callLong(0, 3))
        }
    }

    @Test
    fun multiplyTwoNumbers() {
        val obj = generateObjectFile {
            val params = createFunction("multiply", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val product = mul(params[0], params[1])
            ret(product)
            finalizeFunction()
        }

        loader.loadObjectFile(obj).use { module ->
            val fn = module.findFunction("multiply")!!
            assertEquals(12L, fn.callLong(3, 4))
            assertEquals(0L, fn.callLong(0, 999))
            assertEquals(-15L, fn.callLong(5, -3))
        }
    }

    @Test
    fun returnConstant() {
        val obj = generateObjectFile {
            createFunction("answer", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I64(42))
            finalizeFunction()
        }

        loader.loadObjectFile(obj).use { module ->
            val fn = module.findFunction("answer")!!
            assertEquals(42L, fn.callLong())
        }
    }

    @Test
    fun bitwiseOperations() {
        val obj = generateObjectFile {
            val params = createFunction("bitwise", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r1 = and(params[0], params[1])
            val r2 = or(r1, params[1])
            val r3 = xor(r2, params[0])
            ret(r3)
            finalizeFunction()
        }

        loader.loadObjectFile(obj).use { module ->
            val fn = module.findFunction("bitwise")!!
            assertEquals(0xF0L, fn.callLong(0xFF, 0x0F))
        }
    }

    @Test
    fun conditionalBranch() {
        val obj = generateObjectFile {
            val params = createFunction("max", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            condBr(cond, "ret_a", "ret_b")

            positionAtEnd(appendBlock("ret_a"))
            ret(params[0])

            positionAtEnd(appendBlock("ret_b"))
            ret(params[1])

            finalizeFunction()
        }

        loader.loadObjectFile(obj).use { module ->
            val fn = module.findFunction("max")!!
            assertEquals(5L, fn.callLong(5, 3))
            assertEquals(5L, fn.callLong(3, 5))
            assertEquals(0L, fn.callLong(0, 0))
            assertEquals(100L, fn.callLong(100, -1))
        }
    }

    @Test
    fun multipleFunction() {
        val obj = generateObjectFile {
            val addParams = createFunction("add2", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(add(addParams[0], addParams[1]))
            finalizeFunction()

            val mulParams = createFunction("mul2", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(mul(mulParams[0], mulParams[1]))
            finalizeFunction()
        }

        loader.loadObjectFile(obj).use { module ->
            val addFn = module.findFunction("add2")!!
            val mulFn = module.findFunction("mul2")!!
            assertEquals(7L, addFn.callLong(3, 4))
            assertEquals(12L, mulFn.callLong(3, 4))
        }
    }

    @Test
    fun loadCodeRaw() {
        val code = if (isWindows) {
            byteArrayOf(
                0x48.toByte(), 0x89.toByte(), 0xC8.toByte(),
                0x48.toByte(), 0x01.toByte(), 0xD0.toByte(),
                0xC3.toByte(),
            )
        } else {
            byteArrayOf(
                0x48.toByte(), 0x89.toByte(), 0xF8.toByte(),
                0x48.toByte(), 0x01.toByte(), 0xF0.toByte(),
                0xC3.toByte(),
            )
        }

        loader.loadCode(code, mapOf("add" to 0L)).use { module ->
            val fn = module.findFunction("add")!!
            assertEquals(10L, fn.callLong(4, 6))
        }
    }

    @Test
    fun symbolDiscovery() {
        val obj = generateObjectFile {
            createFunction("foo", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I64(1))
            finalizeFunction()

            createFunction("bar", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I64(2))
            finalizeFunction()
        }

        loader.loadObjectFile(obj).use { module ->
            val syms = module.symbols()
            assertTrue("foo" in syms)
            assertTrue("bar" in syms)
            assertNotNull(module.findSymbol("foo"))
            assertNotNull(module.findSymbol("bar"))
            assertNull(module.findSymbol("nonexistent"))
            assertNull(module.findFunction("nonexistent"))
        }
    }
}
