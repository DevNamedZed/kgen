package org.kgen.ir.build.extensions

import org.kgen.ir.Type
import org.kgen.ir.Value
import org.kgen.ir.build.sets.ConversionInstructionSet
import org.kgen.ir.text.IrPrinter

/**
 * Sugar methods for type conversions. Extends [ConversionInstructionSet]
 * with auto-detecting cast methods that choose the right instruction
 * based on source and target widths.
 *
 * ```java
 * NativeScope ins = fn.instructions();
 * Value narrowed = ins.intCast(longValue, Type.I32);    // auto trunc or sext
 * Value widened = ins.uintCast(byteValue, Type.I64);    // auto trunc or zext
 * Value asFloat = ins.toFloat(intValue, Type.F64);      // sitofp
 * ```
 */
interface ConversionExtensions : ConversionInstructionSet {

    fun intCast(value: Value, targetType: Type): Value {
        val sourceBits = bitWidth(value.type)
        val targetBits = bitWidth(targetType)
        return when {
            sourceBits > targetBits -> trunc(value, targetType)
            sourceBits < targetBits -> sext(value, targetType)
            else -> value
        }
    }

    fun uintCast(value: Value, targetType: Type): Value {
        val sourceBits = bitWidth(value.type)
        val targetBits = bitWidth(targetType)
        return when {
            sourceBits > targetBits -> trunc(value, targetType)
            sourceBits < targetBits -> zext(value, targetType)
            else -> value
        }
    }

    fun floatCast(value: Value, targetType: Type): Value {
        val sourceBits = bitWidth(value.type)
        val targetBits = bitWidth(targetType)
        return when {
            sourceBits > targetBits -> fptrunc(value, targetType)
            sourceBits < targetBits -> fpext(value, targetType)
            else -> value
        }
    }

    fun toFloat(value: Value, targetType: Type): Value = sitofp(value, targetType)

    fun toInt(value: Value, targetType: Type): Value = fptosi(value, targetType)

    companion object {
        internal fun bitWidth(type: Type): Int = when (type) {
            is Type.I1 -> 1; is Type.I8 -> 8; is Type.I16 -> 16; is Type.I32 -> 32
            is Type.I64 -> 64; is Type.I128 -> 128; is Type.IntN -> type.bits
            is Type.F16, is Type.BF16 -> 16; is Type.F32 -> 32; is Type.F64 -> 64
            is Type.F80 -> 80; is Type.F128 -> 128
            else -> error("Cannot determine bit width of ${IrPrinter.typeStr(type)}")
        }
    }
}
