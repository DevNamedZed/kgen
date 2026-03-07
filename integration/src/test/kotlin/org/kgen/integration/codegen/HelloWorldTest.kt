package org.kgen.integration.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.codegen.*
import org.kgen.ir.target.Target
import org.kgen.pass.OptLevel
import org.kgen.backend.x86.codegen.X86CodeGenerator
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HelloWorldTest {

    private fun le(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun buildHelloWorldModule(): Module {
        val ir = IrBuilder("hello", Target.x86_64())

        val strType = Type.Array(Type.I8, 14)
        val strRef = ir.addGlobal("hello_str", strType,
            Constant.StringConst("Hello, World!"), isConstant = true,
            linkage = Linkage.INTERNAL)

        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

        ir.createFunction("main", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.call("puts", listOf(strRef), Type.I32)
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        return ir.build()
    }

    private fun buildWindowsHelloWorldModule(): Module {
        val ir = IrBuilder("hello_pe", Target.x86_64())
        ir.targetTriple = "x86_64-unknown-windows-msvc"

        val strType = Type.Array(Type.I8, 14)
        val strRef = ir.addGlobal("hello_str", strType,
            Constant.StringConst("Hello, World!"), isConstant = true,
            linkage = Linkage.INTERNAL)

        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

        ir.createFunction("main", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.call("puts", listOf(strRef), Type.I32)
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        return ir.build()
    }

    @Test
    fun elfHelloWorldBinaryIsValid() {
        val module = buildHelloWorldModule()
        val binary = X86CodeGenerator().generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))

        assertEquals(0x7f, binary[0].toInt() and 0xFF, "ELF magic byte 0")
        assertEquals('E'.code, binary[1].toInt() and 0xFF, "ELF magic byte 1")
        assertEquals('L'.code, binary[2].toInt() and 0xFF, "ELF magic byte 2")
        assertEquals('F'.code, binary[3].toInt() and 0xFF, "ELF magic byte 3")
        assertTrue(binary.size > 1000, "Binary should be non-trivial")
        assertTrue(String(binary, Charsets.US_ASCII).contains("Hello, World!"))

        val outputDir = File(System.getProperty("user.dir"), "build")
        outputDir.mkdirs()
        File(outputDir, "hello_kgen").writeBytes(binary)
    }

    @Test
    fun peHelloWorldBinaryIsValid() {
        val module = buildWindowsHelloWorldModule()
        val binary = X86CodeGenerator().generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))
        val buf = le(binary)

        assertEquals('M'.code, binary[0].toInt() and 0xFF, "MZ magic byte 0")
        assertEquals('Z'.code, binary[1].toInt() and 0xFF, "MZ magic byte 1")

        val peOff = buf.getInt(0x3C)
        assertEquals('P'.code, binary[peOff].toInt() and 0xFF, "PE signature P")
        assertEquals('E'.code, binary[peOff + 1].toInt() and 0xFF, "PE signature E")

        assertTrue(binary.size > 512, "PE binary should be non-trivial")
        assertTrue(String(binary, Charsets.US_ASCII).contains("Hello, World!"))

        val outputDir = File(System.getProperty("user.dir"), "build")
        outputDir.mkdirs()
        File(outputDir, "hello_kgen.exe").writeBytes(binary)
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    fun elfHelloWorldRuns() {
        val module = buildHelloWorldModule()
        val binary = X86CodeGenerator().generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))

        val file = File.createTempFile("hello_kgen_", "")
        try {
            file.writeBytes(binary)
            file.setExecutable(true)

            val process = ProcessBuilder(file.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            assertEquals("Hello, World!\n", output)
            assertEquals(0, exitCode)
        } finally {
            file.delete()
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun peHelloWorldRuns() {
        val module = buildWindowsHelloWorldModule()
        val binary = X86CodeGenerator().generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))

        val file = File.createTempFile("hello_kgen_", ".exe")
        try {
            file.writeBytes(binary)

            val process = ProcessBuilder(file.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            assertEquals("Hello, World!\r\n", output)
            assertEquals(0, exitCode)
        } finally {
            file.delete()
        }
    }
}
