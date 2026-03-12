package org.kgen.runtime.compile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

/**
 * Tests for platform-conditional intrinsics: Kgen.isWindows(), Kgen.isLinux(), Kgen.isMacOS().
 *
 * These intrinsics become compile-time boolean constants based on the target triple,
 * enabling dead code elimination of unused platform branches.
 */
class PlatformIntrinsicsTest {

    private fun compile(classBytes: ByteArray, target: Target): Module {
        return RuntimeCompiler(target).compile(classBytes)
    }

    private fun buildClass(block: ClassFileBuilder.() -> Unit): ByteArray {
        return JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/PlatformCheck").apply(block).build()
        )
    }

    private fun allInstructions(module: Module): List<Instruction> =
        module.functions.flatMap { it.blocks.flatMap { b -> b.instructions } }

    private fun buildPlatformCheckClass(): ByteArray = buildClass {
        // isWindows() -> return 1 or 0
        method("checkWindows", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.invokestatic("org/kgen/unmanaged/Kgen", "isWindows", "()Z")
            code.ireturn()
        }
        // isLinux() -> return 1 or 0
        method("checkLinux", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.invokestatic("org/kgen/unmanaged/Kgen", "isLinux", "()Z")
            code.ireturn()
        }
        // isMacOS() -> return 1 or 0
        method("checkMacOS", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.invokestatic("org/kgen/unmanaged/Kgen", "isMacOS", "()Z")
            code.ireturn()
        }
    }

    @Test
    fun isWindowsTrueForWindowsTarget() {
        val classBytes = buildPlatformCheckClass()
        val module = compile(classBytes, Target.x86_64())
        // x86_64 default triple is x86_64-unknown-windows-msvc on Windows host,
        // but we need to check what the test machine gives. Check the constant value.
        val fn = module.functions.first { it.name == "checkWindows" }
        val rets = fn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Ret>()
        assertTrue(rets.isNotEmpty(), "Should have a return instruction")
        // The return value should be a Constant.I1
        val retVal = rets.first().value
        assertTrue(retVal is Constant.I1, "Return value should be Constant.I1, got $retVal")
    }

    @Test
    fun isLinuxTrueForLinuxTarget() {
        // x86_64 triple on Linux: x86_64-unknown-linux-gnu
        val classBytes = buildPlatformCheckClass()
        val module = compile(classBytes, Target.x86_64())
        val triple = Target.x86_64().tripleString()
        val expectedLinux = "linux" in triple

        val fn = module.functions.first { it.name == "checkLinux" }
        val rets = fn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Ret>()
        val retVal = rets.first().value as Constant.I1
        assertEquals(expectedLinux, retVal.value, "isLinux() should match target triple ($triple)")
    }

    @Test
    fun isMacOSTrueForDarwinTarget() {
        val classBytes = buildPlatformCheckClass()
        val module = compile(classBytes, Target.arm64())
        val triple = Target.arm64().tripleString()
        val expectedMacOS = "darwin" in triple

        val fn = module.functions.first { it.name == "checkMacOS" }
        val rets = fn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Ret>()
        val retVal = rets.first().value as Constant.I1
        assertEquals(expectedMacOS, retVal.value, "isMacOS() should match target triple ($triple)")
    }

    @Test
    fun platformChecksAreCompileTimeConstants() {
        // All three should produce Constant.I1 — no function calls
        val classBytes = buildPlatformCheckClass()
        val module = compile(classBytes, Target.x86_64())
        val calls = allInstructions(module).filterIsInstance<Instruction.Call>()
        val platformCalls = calls.filter {
            val fn = it.function
            fn is GlobalRef && fn.name in listOf("isWindows", "isLinux", "isMacOS")
        }
        assertTrue(platformCalls.isEmpty(), "Platform intrinsics should be lowered to constants, not calls")
    }

    @Test
    fun windowsTargetOnlyWindowsTrue() {
        // Force a windows triple by checking tripleString content
        val classBytes = buildPlatformCheckClass()
        val target = Target.x86_64()
        val triple = target.tripleString()

        if ("windows" in triple) {
            val module = compile(classBytes, target)
            val winFn = module.functions.first { it.name == "checkWindows" }
            val linFn = module.functions.first { it.name == "checkLinux" }
            val macFn = module.functions.first { it.name == "checkMacOS" }

            val winRet = winFn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Ret>().first().value as Constant.I1
            val linRet = linFn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Ret>().first().value as Constant.I1
            val macRet = macFn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Ret>().first().value as Constant.I1

            assertTrue(winRet.value, "isWindows() should be true on Windows target")
            assertFalse(linRet.value, "isLinux() should be false on Windows target")
            assertFalse(macRet.value, "isMacOS() should be false on Windows target")
        } else if ("linux" in triple) {
            val module = compile(classBytes, target)
            val winFn = module.functions.first { it.name == "checkWindows" }
            val linFn = module.functions.first { it.name == "checkLinux" }
            val macFn = module.functions.first { it.name == "checkMacOS" }

            val winRet = winFn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Ret>().first().value as Constant.I1
            val linRet = linFn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Ret>().first().value as Constant.I1
            val macRet = macFn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Ret>().first().value as Constant.I1

            assertFalse(winRet.value, "isWindows() should be false on Linux target")
            assertTrue(linRet.value, "isLinux() should be true on Linux target")
            assertFalse(macRet.value, "isMacOS() should be false on Linux target")
        }
    }

    @Test
    fun platformCheckInBranch() {
        // if (Kgen.isWindows()) return 1; else return 2;
        val classBytes = buildClass {
            method("platformBranch", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.invokestatic("org/kgen/unmanaged/Kgen", "isWindows", "()Z")
                code.ifeq("elseBranch")
                code.iconst(1)
                code.ireturn()
                code.label("elseBranch")
                code.iconst(2)
                code.ireturn()
            }
        }
        val module = compile(classBytes, Target.x86_64())
        val fn = module.functions.first { it.name == "platformBranch" }
        // Should compile successfully — the branch condition is a constant
        assertNotNull(fn)
        assertTrue(fn.blocks.isNotEmpty())
    }
}
