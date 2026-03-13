package org.kgen.target.jvm

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttributeRoundTripTest {

    @Nested
    inner class CodeAttributeRoundTrip {

        @Test
        fun emptyCode() {
            val code = CodeAttribute(
                maxStack = 0,
                maxLocals = 0,
                code = ByteArray(0),
                exceptionTable = emptyList(),
                attributes = emptyList(),
            )
            val attr = AttributeBuilder.buildCode(1, code)
            val parsed = AttributeParser.parseCode(attr, dummyPool())

            assertEquals(0, parsed.maxStack)
            assertEquals(0, parsed.maxLocals)
            assertEquals(0, parsed.code.size)
            assertTrue(parsed.exceptionTable.isEmpty())
            assertTrue(parsed.attributes.isEmpty())
        }

        @Test
        fun codeWithBytecodeAndStack() {
            val bytecode = byteArrayOf(0x2A, 0xB1.toByte()) // aload_0, return
            val code = CodeAttribute(
                maxStack = 1,
                maxLocals = 1,
                code = bytecode,
                exceptionTable = emptyList(),
                attributes = emptyList(),
            )
            val attr = AttributeBuilder.buildCode(1, code)
            val parsed = AttributeParser.parseCode(attr, dummyPool())

            assertEquals(1, parsed.maxStack)
            assertEquals(1, parsed.maxLocals)
            assertEquals(2, parsed.code.size)
            assertEquals(0x2A, parsed.code[0].toInt() and 0xFF)
        }

        @Test
        fun codeWithExceptionTable() {
            val code = CodeAttribute(
                maxStack = 2,
                maxLocals = 3,
                code = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0xB1.toByte()),
                exceptionTable = listOf(
                    ExceptionEntry(0, 3, 4, 5),
                    ExceptionEntry(0, 2, 3, 0),
                ),
                attributes = emptyList(),
            )
            val attr = AttributeBuilder.buildCode(1, code)
            val parsed = AttributeParser.parseCode(attr, dummyPool())

            assertEquals(2, parsed.exceptionTable.size)
            assertEquals(0, parsed.exceptionTable[0].startPc)
            assertEquals(3, parsed.exceptionTable[0].endPc)
            assertEquals(4, parsed.exceptionTable[0].handlerPc)
            assertEquals(5, parsed.exceptionTable[0].catchType)
            assertEquals(0, parsed.exceptionTable[1].catchType)
        }

        @Test
        fun codeWithSubAttributes() {
            val subAttr = AttributeInfo(2, byteArrayOf(0x01, 0x02))
            val code = CodeAttribute(
                maxStack = 1,
                maxLocals = 1,
                code = byteArrayOf(0xB1.toByte()),
                exceptionTable = emptyList(),
                attributes = listOf(subAttr),
            )
            val attr = AttributeBuilder.buildCode(1, code)
            val parsed = AttributeParser.parseCode(attr, dummyPool())

            assertEquals(1, parsed.attributes.size)
            assertEquals(2, parsed.attributes[0].nameIndex)
            assertEquals(2, parsed.attributes[0].data.size)
        }
    }

    @Nested
    inner class LineNumberTableRoundTrip {

        @Test
        fun emptyTable() {
            val table = LineNumberTableAttribute(emptyList())
            val attr = AttributeBuilder.buildLineNumberTable(1, table)
            val parsed = AttributeParser.parseLineNumberTable(attr)
            assertTrue(parsed.entries.isEmpty())
        }

        @Test
        fun multipleEntries() {
            val table = LineNumberTableAttribute(listOf(
                LineNumberEntry(0, 10),
                LineNumberEntry(5, 15),
                LineNumberEntry(12, 20),
            ))
            val attr = AttributeBuilder.buildLineNumberTable(1, table)
            val parsed = AttributeParser.parseLineNumberTable(attr)

            assertEquals(3, parsed.entries.size)
            assertEquals(0, parsed.entries[0].startPc)
            assertEquals(10, parsed.entries[0].lineNumber)
            assertEquals(5, parsed.entries[1].startPc)
            assertEquals(15, parsed.entries[1].lineNumber)
            assertEquals(12, parsed.entries[2].startPc)
            assertEquals(20, parsed.entries[2].lineNumber)
        }
    }

    @Nested
    inner class SourceFileRoundTrip {

        @Test
        fun roundTrip() {
            val attr = AttributeBuilder.buildSourceFile(1, SourceFileAttribute(42))
            val parsed = AttributeParser.parseSourceFile(attr)
            assertEquals(42, parsed.sourceFileIndex)
        }
    }

    @Nested
    inner class ConstantValueRoundTrip {

        @Test
        fun roundTrip() {
            val attr = AttributeBuilder.buildConstantValue(1, ConstantValueAttribute(7))
            val parsed = AttributeParser.parseConstantValue(attr)
            assertEquals(7, parsed.constantValueIndex)
        }
    }

    @Nested
    inner class ExceptionsRoundTrip {

        @Test
        fun emptyExceptions() {
            val attr = AttributeBuilder.buildExceptions(1, ExceptionsAttribute(emptyList()))
            val parsed = AttributeParser.parseExceptions(attr)
            assertTrue(parsed.exceptionIndexTable.isEmpty())
        }

        @Test
        fun multipleExceptions() {
            val attr = AttributeBuilder.buildExceptions(1, ExceptionsAttribute(listOf(3, 5, 8)))
            val parsed = AttributeParser.parseExceptions(attr)
            assertEquals(listOf(3, 5, 8), parsed.exceptionIndexTable)
        }
    }

    @Nested
    inner class InnerClassesRoundTrip {

        @Test
        fun emptyInnerClasses() {
            val attr = AttributeBuilder.buildInnerClasses(1, InnerClassesAttribute(emptyList()))
            val parsed = AttributeParser.parseInnerClasses(attr)
            assertTrue(parsed.classes.isEmpty())
        }

        @Test
        fun singleInnerClass() {
            val entry = InnerClassEntry(
                innerClassInfoIndex = 5,
                outerClassInfoIndex = 3,
                innerNameIndex = 7,
                innerClassAccessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            )
            val attr = AttributeBuilder.buildInnerClasses(1, InnerClassesAttribute(listOf(entry)))
            val parsed = AttributeParser.parseInnerClasses(attr)

            assertEquals(1, parsed.classes.size)
            assertEquals(5, parsed.classes[0].innerClassInfoIndex)
            assertEquals(3, parsed.classes[0].outerClassInfoIndex)
            assertEquals(7, parsed.classes[0].innerNameIndex)
            assertEquals(AccessFlags.PUBLIC or AccessFlags.STATIC, parsed.classes[0].innerClassAccessFlags)
        }
    }

    @Nested
    inner class SignatureRoundTrip {

        @Test
        fun roundTrip() {
            val attr = AttributeBuilder.buildSignature(1, SignatureAttribute(10))
            val parsed = AttributeParser.parseSignature(attr)
            assertEquals(10, parsed.signatureIndex)
        }
    }

    @Nested
    inner class BootstrapMethodsRoundTrip {

        @Test
        fun emptyBootstrapMethods() {
            val attr = AttributeBuilder.buildBootstrapMethods(1, BootstrapMethodsAttribute(emptyList()))
            val parsed = AttributeParser.parseBootstrapMethods(attr)
            assertTrue(parsed.methods.isEmpty())
        }

        @Test
        fun bootstrapWithArguments() {
            val method = BootstrapMethodEntry(
                methodRefIndex = 10,
                arguments = listOf(20, 30, 40),
            )
            val attr = AttributeBuilder.buildBootstrapMethods(1, BootstrapMethodsAttribute(listOf(method)))
            val parsed = AttributeParser.parseBootstrapMethods(attr)

            assertEquals(1, parsed.methods.size)
            assertEquals(10, parsed.methods[0].methodRefIndex)
            assertEquals(listOf(20, 30, 40), parsed.methods[0].arguments)
        }

        @Test
        fun multipleBootstrapMethods() {
            val methods = listOf(
                BootstrapMethodEntry(5, listOf(10)),
                BootstrapMethodEntry(6, emptyList()),
                BootstrapMethodEntry(7, listOf(11, 12)),
            )
            val attr = AttributeBuilder.buildBootstrapMethods(1, BootstrapMethodsAttribute(methods))
            val parsed = AttributeParser.parseBootstrapMethods(attr)

            assertEquals(3, parsed.methods.size)
            assertEquals(5, parsed.methods[0].methodRefIndex)
            assertEquals(listOf(10), parsed.methods[0].arguments)
            assertEquals(6, parsed.methods[1].methodRefIndex)
            assertTrue(parsed.methods[1].arguments.isEmpty())
            assertEquals(7, parsed.methods[2].methodRefIndex)
            assertEquals(listOf(11, 12), parsed.methods[2].arguments)
        }
    }

    @Nested
    inner class NestHostRoundTrip {

        @Test
        fun roundTrip() {
            val attr = AttributeBuilder.buildNestHost(1, NestHostAttribute(15))
            val parsed = AttributeParser.parseNestHost(attr)
            assertEquals(15, parsed.hostClassIndex)
        }
    }

    @Nested
    inner class NestMembersRoundTrip {

        @Test
        fun emptyMembers() {
            val attr = AttributeBuilder.buildNestMembers(1, NestMembersAttribute(emptyList()))
            val parsed = AttributeParser.parseNestMembers(attr)
            assertTrue(parsed.classes.isEmpty())
        }

        @Test
        fun multipleMembers() {
            val attr = AttributeBuilder.buildNestMembers(1, NestMembersAttribute(listOf(3, 5, 7)))
            val parsed = AttributeParser.parseNestMembers(attr)
            assertEquals(listOf(3, 5, 7), parsed.classes)
        }
    }

    @Nested
    inner class MethodParametersRoundTrip {

        @Test
        fun emptyParameters() {
            val attr = AttributeBuilder.buildMethodParameters(1, MethodParametersAttribute(emptyList()))
            val parsed = AttributeParser.parseMethodParameters(attr)
            assertTrue(parsed.parameters.isEmpty())
        }

        @Test
        fun multipleParameters() {
            val params = listOf(
                MethodParameterEntry(10, AccessFlags.FINAL),
                MethodParameterEntry(12, 0),
            )
            val attr = AttributeBuilder.buildMethodParameters(1, MethodParametersAttribute(params))
            val parsed = AttributeParser.parseMethodParameters(attr)

            assertEquals(2, parsed.parameters.size)
            assertEquals(10, parsed.parameters[0].nameIndex)
            assertEquals(AccessFlags.FINAL, parsed.parameters[0].accessFlags)
            assertEquals(12, parsed.parameters[1].nameIndex)
            assertEquals(0, parsed.parameters[1].accessFlags)
        }
    }

    @Nested
    inner class StackMapTableRoundTrip {

        @Test
        fun sameFrameSmallDelta() {
            val smt = StackMapTableAttribute(listOf(StackMapFrame.Same(10)))
            val attr = AttributeBuilder.buildStackMapTable(1, smt)
            val parsed = AttributeParser.parseStackMapTable(attr)
            assertEquals(1, parsed.entries.size)
            val frame = parsed.entries[0] as StackMapFrame.Same
            assertEquals(10, frame.offsetDelta)
        }

        @Test
        fun sameFrameLargeDelta() {
            val smt = StackMapTableAttribute(listOf(StackMapFrame.Same(100)))
            val attr = AttributeBuilder.buildStackMapTable(1, smt)
            val parsed = AttributeParser.parseStackMapTable(attr)
            assertEquals(1, parsed.entries.size)
            val frame = parsed.entries[0] as StackMapFrame.Same
            assertEquals(100, frame.offsetDelta)
        }

        @Test
        fun sameLocals1StackSmallDelta() {
            val smt = StackMapTableAttribute(listOf(
                StackMapFrame.SameLocals1Stack(5, VerificationType.Integer)
            ))
            val attr = AttributeBuilder.buildStackMapTable(1, smt)
            val parsed = AttributeParser.parseStackMapTable(attr)
            val frame = parsed.entries[0] as StackMapFrame.SameLocals1Stack
            assertEquals(5, frame.offsetDelta)
            assertEquals(VerificationType.Integer, frame.stack)
        }

        @Test
        fun sameLocals1StackLargeDelta() {
            val smt = StackMapTableAttribute(listOf(
                StackMapFrame.SameLocals1Stack(200, VerificationType.Float)
            ))
            val attr = AttributeBuilder.buildStackMapTable(1, smt)
            val parsed = AttributeParser.parseStackMapTable(attr)
            val frame = parsed.entries[0] as StackMapFrame.SameLocals1Stack
            assertEquals(200, frame.offsetDelta)
            assertEquals(VerificationType.Float, frame.stack)
        }

        @Test
        fun chopFrame() {
            val smt = StackMapTableAttribute(listOf(StackMapFrame.Chop(50, 2)))
            val attr = AttributeBuilder.buildStackMapTable(1, smt)
            val parsed = AttributeParser.parseStackMapTable(attr)
            val frame = parsed.entries[0] as StackMapFrame.Chop
            assertEquals(50, frame.offsetDelta)
            assertEquals(2, frame.k)
        }

        @Test
        fun appendFrame() {
            val smt = StackMapTableAttribute(listOf(
                StackMapFrame.Append(30, listOf(VerificationType.Integer, VerificationType.Long))
            ))
            val attr = AttributeBuilder.buildStackMapTable(1, smt)
            val parsed = AttributeParser.parseStackMapTable(attr)
            val frame = parsed.entries[0] as StackMapFrame.Append
            assertEquals(30, frame.offsetDelta)
            assertEquals(2, frame.locals.size)
            assertEquals(VerificationType.Integer, frame.locals[0])
            assertEquals(VerificationType.Long, frame.locals[1])
        }

        @Test
        fun fullFrame() {
            val smt = StackMapTableAttribute(listOf(
                StackMapFrame.Full(
                    offsetDelta = 42,
                    locals = listOf(VerificationType.Integer, VerificationType.Object(10)),
                    stack = listOf(VerificationType.Double),
                )
            ))
            val attr = AttributeBuilder.buildStackMapTable(1, smt)
            val parsed = AttributeParser.parseStackMapTable(attr)
            val frame = parsed.entries[0] as StackMapFrame.Full
            assertEquals(42, frame.offsetDelta)
            assertEquals(2, frame.locals.size)
            assertEquals(VerificationType.Integer, frame.locals[0])
            assertEquals(VerificationType.Object(10), frame.locals[1])
            assertEquals(1, frame.stack.size)
            assertEquals(VerificationType.Double, frame.stack[0])
        }

        @Test
        fun allVerificationTypes() {
            val smt = StackMapTableAttribute(listOf(
                StackMapFrame.Full(
                    offsetDelta = 0,
                    locals = listOf(
                        VerificationType.Top,
                        VerificationType.Integer,
                        VerificationType.Float,
                        VerificationType.Double,
                        VerificationType.Long,
                        VerificationType.Null,
                        VerificationType.UninitializedThis,
                        VerificationType.Object(5),
                        VerificationType.Uninitialized(10),
                    ),
                    stack = emptyList(),
                )
            ))
            val attr = AttributeBuilder.buildStackMapTable(1, smt)
            val parsed = AttributeParser.parseStackMapTable(attr)
            val frame = parsed.entries[0] as StackMapFrame.Full
            assertEquals(9, frame.locals.size)
            assertEquals(VerificationType.Top, frame.locals[0])
            assertEquals(VerificationType.Integer, frame.locals[1])
            assertEquals(VerificationType.Float, frame.locals[2])
            assertEquals(VerificationType.Double, frame.locals[3])
            assertEquals(VerificationType.Long, frame.locals[4])
            assertEquals(VerificationType.Null, frame.locals[5])
            assertEquals(VerificationType.UninitializedThis, frame.locals[6])
            assertEquals(VerificationType.Object(5), frame.locals[7])
            assertEquals(VerificationType.Uninitialized(10), frame.locals[8])
        }

        @Test
        fun multipleFrames() {
            val smt = StackMapTableAttribute(listOf(
                StackMapFrame.Same(5),
                StackMapFrame.Chop(10, 1),
                StackMapFrame.Append(15, listOf(VerificationType.Integer)),
            ))
            val attr = AttributeBuilder.buildStackMapTable(1, smt)
            val parsed = AttributeParser.parseStackMapTable(attr)

            assertEquals(3, parsed.entries.size)
            assertTrue(parsed.entries[0] is StackMapFrame.Same)
            assertTrue(parsed.entries[1] is StackMapFrame.Chop)
            assertTrue(parsed.entries[2] is StackMapFrame.Append)
        }
    }

    @Nested
    inner class EnclosingMethodParse {

        @Test
        fun parseEnclosingMethod() {
            val data = byteArrayOf(0x00, 0x05, 0x00, 0x0A)
            val attr = AttributeInfo(1, data)
            val parsed = AttributeParser.parseEnclosingMethod(attr)
            assertEquals(5, parsed.classIndex)
            assertEquals(10, parsed.methodIndex)
        }
    }

    @Nested
    inner class PermittedSubclassesParse {

        @Test
        fun parsePermittedSubclasses() {
            val data = byteArrayOf(0x00, 0x02, 0x00, 0x03, 0x00, 0x05)
            val attr = AttributeInfo(1, data)
            val parsed = AttributeParser.parsePermittedSubclasses(attr)
            assertEquals(listOf(3, 5), parsed.classes)
        }
    }

    @Nested
    inner class ParseDispatch {

        @Test
        fun dispatchReturnsNullForUnknown() {
            val cp = ConstantPoolBuilder()
            val nameIdx = cp.utf8("UnknownAttribute")
            val pool = cp.build()
            val attr = AttributeInfo(nameIdx, byteArrayOf(0x01, 0x02))
            val result = AttributeParser.parse(attr, pool)
            assertEquals(null, result)
        }

        @Test
        fun dispatchRoutesToSourceFile() {
            val cp = ConstantPoolBuilder()
            val nameIdx = cp.utf8("SourceFile")
            val srcIdx = cp.utf8("Test.java")
            val pool = cp.build()
            val data = byteArrayOf((srcIdx shr 8).toByte(), (srcIdx and 0xFF).toByte())
            val attr = AttributeInfo(nameIdx, data)
            val result = AttributeParser.parse(attr, pool)
            assertTrue(result is SourceFileAttribute)
            assertEquals(srcIdx, (result as SourceFileAttribute).sourceFileIndex)
        }

        @Test
        fun dispatchRoutesToConstantValue() {
            val cp = ConstantPoolBuilder()
            val nameIdx = cp.utf8("ConstantValue")
            val pool = cp.build()
            val data = byteArrayOf(0x00, 0x07)
            val attr = AttributeInfo(nameIdx, data)
            val result = AttributeParser.parse(attr, pool)
            assertTrue(result is ConstantValueAttribute)
            assertEquals(7, (result as ConstantValueAttribute).constantValueIndex)
        }

        @Test
        fun dispatchRoutesToSignature() {
            val cp = ConstantPoolBuilder()
            val nameIdx = cp.utf8("Signature")
            val pool = cp.build()
            val data = byteArrayOf(0x00, 0x0A)
            val attr = AttributeInfo(nameIdx, data)
            val result = AttributeParser.parse(attr, pool)
            assertTrue(result is SignatureAttribute)
            assertEquals(10, (result as SignatureAttribute).signatureIndex)
        }
    }

    private fun dummyPool(): ConstantPool {
        return ConstantPoolBuilder().build()
    }
}
