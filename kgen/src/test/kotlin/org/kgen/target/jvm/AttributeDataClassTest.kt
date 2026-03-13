package org.kgen.target.jvm

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttributeDataClassTest {

    @Nested
    inner class CodeAttributeModel {

        @Test
        fun construction() {
            val code = CodeAttribute(
                maxStack = 4,
                maxLocals = 3,
                code = byteArrayOf(0x2A, 0xB1.toByte()),
                exceptionTable = listOf(ExceptionEntry(0, 5, 6, 7)),
                attributes = emptyList(),
            )
            assertEquals(4, code.maxStack)
            assertEquals(3, code.maxLocals)
            assertEquals(2, code.code.size)
            assertEquals(1, code.exceptionTable.size)
        }

        @Test
        fun equalityByContent() {
            val c1 = CodeAttribute(1, 1, byteArrayOf(0x00), emptyList(), emptyList())
            val c2 = CodeAttribute(1, 1, byteArrayOf(0x00), emptyList(), emptyList())
            assertEquals(c1, c2)
            assertEquals(c1.hashCode(), c2.hashCode())
        }

        @Test
        fun inequalityByCode() {
            val c1 = CodeAttribute(1, 1, byteArrayOf(0x00), emptyList(), emptyList())
            val c2 = CodeAttribute(1, 1, byteArrayOf(0x01), emptyList(), emptyList())
            assertTrue(c1 != c2)
        }

        @Test
        fun inequalityByMaxStack() {
            val c1 = CodeAttribute(1, 1, byteArrayOf(0x00), emptyList(), emptyList())
            val c2 = CodeAttribute(2, 1, byteArrayOf(0x00), emptyList(), emptyList())
            assertTrue(c1 != c2)
        }

        @Test
        fun equalsSameReference() {
            val c = CodeAttribute(1, 1, byteArrayOf(0x00), emptyList(), emptyList())
            assertEquals(c, c)
        }

        @Test
        fun equalsDifferentType() {
            val c = CodeAttribute(1, 1, byteArrayOf(0x00), emptyList(), emptyList())
            assertTrue(c != (Any() as? CodeAttribute))
        }
    }

    @Nested
    inner class ExceptionEntryModel {

        @Test
        fun construction() {
            val entry = ExceptionEntry(0, 10, 12, 5)
            assertEquals(0, entry.startPc)
            assertEquals(10, entry.endPc)
            assertEquals(12, entry.handlerPc)
            assertEquals(5, entry.catchType)
        }

        @Test
        fun catchAllHasZeroCatchType() {
            val entry = ExceptionEntry(0, 10, 12, 0)
            assertEquals(0, entry.catchType)
        }
    }

    @Nested
    inner class LineNumberEntryModel {

        @Test
        fun construction() {
            val entry = LineNumberEntry(5, 42)
            assertEquals(5, entry.startPc)
            assertEquals(42, entry.lineNumber)
        }
    }

    @Nested
    inner class LocalVariableEntryModel {

        @Test
        fun construction() {
            val entry = LocalVariableEntry(0, 10, 5, 6, 0)
            assertEquals(0, entry.startPc)
            assertEquals(10, entry.length)
            assertEquals(5, entry.nameIndex)
            assertEquals(6, entry.descriptorIndex)
            assertEquals(0, entry.index)
        }
    }

    @Nested
    inner class LocalVariableTypeEntryModel {

        @Test
        fun construction() {
            val entry = LocalVariableTypeEntry(0, 10, 5, 6, 0)
            assertEquals(0, entry.startPc)
            assertEquals(10, entry.length)
            assertEquals(5, entry.nameIndex)
            assertEquals(6, entry.signatureIndex)
            assertEquals(0, entry.index)
        }
    }

    @Nested
    inner class InnerClassEntryModel {

        @Test
        fun construction() {
            val entry = InnerClassEntry(5, 3, 7, AccessFlags.PUBLIC or AccessFlags.STATIC)
            assertEquals(5, entry.innerClassInfoIndex)
            assertEquals(3, entry.outerClassInfoIndex)
            assertEquals(7, entry.innerNameIndex)
            assertEquals(AccessFlags.PUBLIC or AccessFlags.STATIC, entry.innerClassAccessFlags)
        }
    }

    @Nested
    inner class BootstrapMethodEntryModel {

        @Test
        fun construction() {
            val entry = BootstrapMethodEntry(10, listOf(20, 30))
            assertEquals(10, entry.methodRefIndex)
            assertEquals(listOf(20, 30), entry.arguments)
        }

        @Test
        fun emptyArguments() {
            val entry = BootstrapMethodEntry(5, emptyList())
            assertTrue(entry.arguments.isEmpty())
        }
    }

    @Nested
    inner class RecordComponentInfoModel {

        @Test
        fun construction() {
            val comp = RecordComponentInfo(5, 6, emptyList())
            assertEquals(5, comp.nameIndex)
            assertEquals(6, comp.descriptorIndex)
            assertTrue(comp.attributes.isEmpty())
        }
    }

    @Nested
    inner class AnnotationModel {

        @Test
        fun construction() {
            val value = AnnotationValue('I', 42)
            assertEquals('I', value.tag)
            assertEquals(42, value.value)
        }

        @Test
        fun annotationWithPairs() {
            val annotation = Annotation(5, listOf(10 to AnnotationValue('s', "hello")))
            assertEquals(5, annotation.typeIndex)
            assertEquals(1, annotation.elementValuePairs.size)
        }
    }

    @Nested
    inner class MethodParameterEntryModel {

        @Test
        fun construction() {
            val param = MethodParameterEntry(5, AccessFlags.FINAL)
            assertEquals(5, param.nameIndex)
            assertEquals(AccessFlags.FINAL, param.accessFlags)
        }
    }

    @Nested
    inner class VerificationTypeModel {

        @Test
        fun objectType() {
            val vt = VerificationType.Object(10)
            assertEquals(10, vt.cpoolIndex)
        }

        @Test
        fun uninitializedType() {
            val vt = VerificationType.Uninitialized(5)
            assertEquals(5, vt.offset)
        }

        @Test
        fun singletonTypes() {
            assertTrue(VerificationType.Top is VerificationType)
            assertTrue(VerificationType.Integer is VerificationType)
            assertTrue(VerificationType.Float is VerificationType)
            assertTrue(VerificationType.Double is VerificationType)
            assertTrue(VerificationType.Long is VerificationType)
            assertTrue(VerificationType.Null is VerificationType)
            assertTrue(VerificationType.UninitializedThis is VerificationType)
        }
    }

    @Nested
    inner class StackMapFrameModel {

        @Test
        fun sameFrame() {
            val frame = StackMapFrame.Same(10)
            assertEquals(10, frame.offsetDelta)
        }

        @Test
        fun sameLocals1Stack() {
            val frame = StackMapFrame.SameLocals1Stack(5, VerificationType.Integer)
            assertEquals(5, frame.offsetDelta)
            assertEquals(VerificationType.Integer, frame.stack)
        }

        @Test
        fun chopFrame() {
            val frame = StackMapFrame.Chop(20, 2)
            assertEquals(20, frame.offsetDelta)
            assertEquals(2, frame.k)
        }

        @Test
        fun appendFrame() {
            val frame = StackMapFrame.Append(15, listOf(VerificationType.Integer))
            assertEquals(15, frame.offsetDelta)
            assertEquals(1, frame.locals.size)
        }

        @Test
        fun fullFrame() {
            val frame = StackMapFrame.Full(
                offsetDelta = 30,
                locals = listOf(VerificationType.Integer, VerificationType.Float),
                stack = listOf(VerificationType.Long),
            )
            assertEquals(30, frame.offsetDelta)
            assertEquals(2, frame.locals.size)
            assertEquals(1, frame.stack.size)
        }
    }

    @Nested
    inner class SimpleAttributeModels {

        @Test
        fun sourceFileAttribute() {
            val attr = SourceFileAttribute(5)
            assertEquals(5, attr.sourceFileIndex)
        }

        @Test
        fun enclosingMethodAttribute() {
            val attr = EnclosingMethodAttribute(3, 7)
            assertEquals(3, attr.classIndex)
            assertEquals(7, attr.methodIndex)
        }

        @Test
        fun signatureAttribute() {
            val attr = SignatureAttribute(10)
            assertEquals(10, attr.signatureIndex)
        }

        @Test
        fun nestHostAttribute() {
            val attr = NestHostAttribute(15)
            assertEquals(15, attr.hostClassIndex)
        }

        @Test
        fun nestMembersAttribute() {
            val attr = NestMembersAttribute(listOf(3, 5))
            assertEquals(listOf(3, 5), attr.classes)
        }

        @Test
        fun permittedSubclassesAttribute() {
            val attr = PermittedSubclassesAttribute(listOf(10, 20))
            assertEquals(listOf(10, 20), attr.classes)
        }

        @Test
        fun constantValueAttribute() {
            val attr = ConstantValueAttribute(7)
            assertEquals(7, attr.constantValueIndex)
        }

        @Test
        fun exceptionsAttribute() {
            val attr = ExceptionsAttribute(listOf(1, 2, 3))
            assertEquals(listOf(1, 2, 3), attr.exceptionIndexTable)
        }
    }
}
