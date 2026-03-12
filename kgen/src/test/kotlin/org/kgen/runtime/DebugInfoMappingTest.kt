package org.kgen.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.Instruction
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.RuntimeCompiler
import org.kgen.target.jvm.*

class DebugInfoMappingTest {

    /**
     * Build a classfile with a LineNumberTable attribute on a simple add method.
     */
    private fun buildClassWithLineNumbers(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/DebugAdd")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val methodNameIdx = cp.utf8("add")
        val methodDescIdx = cp.utf8("(II)I")
        val codeNameIdx = cp.utf8("Code")
        val lineTableNameIdx = cp.utf8("LineNumberTable")
        val sourceFileNameIdx = cp.utf8("SourceFile")
        val sourceFileValueIdx = cp.utf8("DebugAdd.java")

        // iload_0, iload_1, iadd, ireturn
        val bytecode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())

        // Build LineNumberTable attribute: pc=0 → line 5, pc=2 → line 6
        val lineTableData = buildLineNumberTable(
            LineNumberEntry(0, 5),
            LineNumberEntry(2, 6),
        )
        val lineTableAttr = AttributeInfo(lineTableNameIdx, lineTableData)

        val codeAttr = AttributeBuilder.buildCode(
            codeNameIdx,
            CodeAttribute(
                maxStack = 2, maxLocals = 2, code = bytecode,
                exceptionTable = emptyList(),
                attributes = listOf(lineTableAttr),
            )
        )

        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = methodNameIdx,
            descriptorIndex = methodDescIdx,
            attributes = listOf(codeAttr),
        )

        // SourceFile attribute
        val sourceFileData = byteArrayOf(
            ((sourceFileValueIdx shr 8) and 0xFF).toByte(),
            (sourceFileValueIdx and 0xFF).toByte()
        )
        val sourceFileAttr = AttributeInfo(sourceFileNameIdx, sourceFileData)

        val cf = ClassFile(
            minorVersion = 0, majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method),
            attributes = listOf(sourceFileAttr),
        )

        return JvmClassWriter.write(cf)
    }

    private fun buildLineNumberTable(vararg entries: LineNumberEntry): ByteArray {
        val size = 2 + entries.size * 4
        val data = ByteArray(size)
        data[0] = ((entries.size shr 8) and 0xFF).toByte()
        data[1] = (entries.size and 0xFF).toByte()
        var pos = 2
        for (e in entries) {
            data[pos++] = ((e.startPc shr 8) and 0xFF).toByte()
            data[pos++] = (e.startPc and 0xFF).toByte()
            data[pos++] = ((e.lineNumber shr 8) and 0xFF).toByte()
            data[pos++] = (e.lineNumber and 0xFF).toByte()
        }
        return data
    }

    @Test
    fun debugLocEmittedFromLineNumberTable() {
        val classBytes = buildClassWithLineNumbers()
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(classBytes)

        // Find DebugLoc instructions in the compiled IR
        val func = module.functions.first()
        val debugLocs = func.blocks.flatMap { block ->
            block.instructions.filterIsInstance<Instruction.DebugLoc>()
        }

        // Should have emitted DebugLoc for line 5 and line 6
        assertTrue(debugLocs.isNotEmpty(), "Expected DebugLoc instructions")
        assertTrue(debugLocs.any { it.line == 5 }, "Expected DebugLoc for line 5")
        assertTrue(debugLocs.any { it.line == 6 }, "Expected DebugLoc for line 6")
    }

    @Test
    fun debugLocScopeIsMethodName() {
        val classBytes = buildClassWithLineNumbers()
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(classBytes)

        val func = module.functions.first()
        val debugLocs = func.blocks.flatMap { block ->
            block.instructions.filterIsInstance<Instruction.DebugLoc>()
        }

        for (loc in debugLocs) {
            assertEquals("add", loc.scope)
        }
    }

    @Test
    fun noLineNumberTableProducesNoDebugLoc() {
        // Build class without LineNumberTable
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/NoDebug")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val methodNameIdx = cp.utf8("add")
        val methodDescIdx = cp.utf8("(II)I")
        val codeNameIdx = cp.utf8("Code")

        val bytecode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())

        val codeAttr = AttributeBuilder.buildCode(
            codeNameIdx,
            CodeAttribute(
                maxStack = 2, maxLocals = 2, code = bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            )
        )

        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = methodNameIdx,
            descriptorIndex = methodDescIdx,
            attributes = listOf(codeAttr),
        )

        val cf = ClassFile(
            minorVersion = 0, majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )

        val classBytes = JvmClassWriter.write(cf)
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(classBytes)

        val func = module.functions.first()
        val debugLocs = func.blocks.flatMap { block ->
            block.instructions.filterIsInstance<Instruction.DebugLoc>()
        }

        assertTrue(debugLocs.isEmpty(), "No DebugLoc expected without LineNumberTable")
    }

    @Test
    fun sourceFileFromClassAttribute() {
        val classBytes = buildClassWithLineNumbers()
        val cf = JvmClassReader.read(classBytes)
        assertEquals("DebugAdd.java", cf.sourceFile)
    }

    @Test
    fun sourceFileNullWhenAbsent() {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/NoSource")
        val superClassIdx = cp.classEntry("java/lang/Object")

        val cf = ClassFile(
            minorVersion = 0, majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = emptyList(), attributes = emptyList(),
        )

        assertNull(cf.sourceFile)
    }
}
