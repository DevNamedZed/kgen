package org.kgen.ir

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.text.IrPrinter
import org.kgen.ir.text.IrParser
import org.kgen.ir.text.IrSerializer

class MixedModeIrTest {

    private val serializer = IrSerializer()

    private fun findFunction(module: Module, name: String): IrFunction {
        for (f in module.functions) { if (f.name == name) return f }
        throw IllegalArgumentException("Function $name not found")
    }

    // --- Types ---

    @Test
    fun interiorRefType() {
        val t = Type.InteriorRef(Type.I32)
        assertEquals(Type.I32, t.pointee)
        assertEquals(t, Type.interiorRef(Type.I32))
    }

    @Test
    fun pinnedRefType() {
        val t = Type.PinnedRef(Type.I64)
        assertEquals(Type.I64, t.referent)
        assertEquals(t, Type.pinnedRef(Type.I64))
    }

    @Test
    fun interiorRefEquality() {
        assertEquals(Type.InteriorRef(Type.F32), Type.InteriorRef(Type.F32))
        assertNotEquals(Type.InteriorRef(Type.F32), Type.InteriorRef(Type.F64))
    }

    @Test
    fun pinnedRefEquality() {
        assertEquals(Type.PinnedRef(Type.I32), Type.PinnedRef(Type.I32))
        assertNotEquals(Type.PinnedRef(Type.I32), Type.PinnedRef(Type.I64))
    }

    @Test
    fun nestedRefTypes() {
        val inner = Type.Reference(Type.I64, false)
        val pinned = Type.PinnedRef(Type.ClassRef("MyClass"))
        val interior = Type.InteriorRef(Type.I32)
        assertNotEquals(inner, pinned)
        assertNotEquals(inner, interior)
    }

    // --- Pin / Unpin instructions ---

    @Test
    fun pinInstruction() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("obj", Type.Reference(Type.I64, false))), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)
        val pinned = ir.pin(param)
        assertEquals(Type.PinnedRef(Type.I64), pinned.type)
        ir.unpin(pinned)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()
        val func = module.functions[0]
        assertEquals(3, func.blocks[0].instructions.size)
        assertTrue(func.blocks[0].instructions[0] is Instruction.Pin)
        assertTrue(func.blocks[0].instructions[1] is Instruction.Unpin)
    }

    @Test
    fun pinInstructionResult() {
        val ref = InstructionRef("r1", Type.Reference(Type.I64))
        val dest = InstructionRef("pinned", Type.PinnedRef(Type.ClassRef("X")))
        val pin = Instruction.Pin(dest, ref)
        assertEquals(dest, pin.result)
    }

    @Test
    fun unpinHasNoResult() {
        val ref = InstructionRef("pinned", Type.PinnedRef(Type.I32))
        val unpin = Instruction.Unpin(ref)
        assertNull(unpin.result)
    }

    // --- InteriorPtr instruction ---

    @Test
    fun interiorPtrInstruction() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("obj", Type.Reference(Type.I64, false))), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)
        val iptr = ir.interiorPtr(param, Constant.I32(2), Type.F64)
        assertEquals(Type.InteriorRef(Type.F64), iptr.type)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()
        val inst = module.functions[0].blocks[0].instructions[0] as Instruction.InteriorPtr
        assertEquals(Type.F64, inst.pointeeType)
    }

    // --- WriteBarrier instruction ---

    @Test
    fun writeBarrierInstruction() {
        val obj = InstructionRef("obj", Type.Reference(Type.I64))
        val fieldIdx = Constant.I32(3)
        val value = InstructionRef("val", Type.Reference(Type.I32))
        val wb = Instruction.WriteBarrier(obj, fieldIdx, value)
        assertNull(wb.result)
    }

    @Test
    fun writeBarrierBuilder() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(
            Param("obj", Type.Reference(Type.I64, false)),
            Param("val", Type.Reference(Type.ClassRef("Other")))
        ), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val objParam = Parameter("obj", Type.Reference(Type.I64, false), 0)
        val valParam = Parameter("val", Type.Reference(Type.ClassRef("Other")), 1)
        ir.writeBarrier(objParam, Constant.I32(0), valParam)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()
        assertTrue(module.functions[0].blocks[0].instructions[0] is Instruction.WriteBarrier)
    }

    // --- ReadBarrier instruction ---

    @Test
    fun readBarrierInstruction() {
        val ref = InstructionRef("ref", Type.Reference(Type.I64))
        val dest = InstructionRef("updated", Type.Reference(Type.I64))
        val rb = Instruction.ReadBarrier(dest, ref)
        assertEquals(dest, rb.result)
    }

    @Test
    fun readBarrierBuilder() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("obj", Type.Reference(Type.I64, false))), Type.Reference(Type.I64, false))
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)
        val result = ir.readBarrier(param)
        assertEquals(Type.Reference(Type.I64, false), result.type)
        ir.ret(result)
        ir.finalizeFunction()
        val module = ir.build()
        assertTrue(module.functions[0].blocks[0].instructions[0] is Instruction.ReadBarrier)
    }

    // --- ManagedCall instruction ---

    @Test
    fun managedCallManagedToNative() {
        val func = GlobalRef("native_fn", Type.Function(listOf(Type.I32), Type.I64))
        val dest = InstructionRef("r", Type.I64)
        val mc = Instruction.ManagedCall(dest, func, listOf(Constant.I32(42)), Type.I64, ManagedCallDirection.MANAGED_TO_NATIVE)
        assertEquals(dest, mc.result)
        assertEquals(ManagedCallDirection.MANAGED_TO_NATIVE, mc.direction)
    }

    @Test
    fun managedCallNativeToManaged() {
        val func = GlobalRef("managed_fn", Type.Function(listOf(Type.I32), Type.Void))
        val mc = Instruction.ManagedCall(null, func, listOf(Constant.I32(1)), Type.Void, ManagedCallDirection.NATIVE_TO_MANAGED)
        assertNull(mc.result)
        assertEquals(ManagedCallDirection.NATIVE_TO_MANAGED, mc.direction)
    }

    @Test
    fun managedCallBuilder() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("native_add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.createFunction("caller", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val fn = GlobalRef("native_add", Type.Function(listOf(Type.I32, Type.I32), Type.I32))
        val result = ir.managedCall(fn, listOf(Constant.I32(10), Constant.I32(20)), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)
        assertNotNull(result)
        ir.ret(result)
        ir.finalizeFunction()
        val module = ir.build()
        val inst = findFunction(module, "caller").blocks[0].instructions[0]
        assertTrue(inst is Instruction.ManagedCall)
        assertEquals(ManagedCallDirection.MANAGED_TO_NATIVE, (inst as Instruction.ManagedCall).direction)
    }

    @Test
    fun managedCallVoidBuilder() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("log", listOf(Param("msg", Type.I32)), Type.Void)
        ir.createFunction("caller", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val fn = GlobalRef("log", Type.Function(listOf(Type.I32), Type.Void))
        val result = ir.managedCall(fn, listOf(Constant.I32(0)), Type.Void, ManagedCallDirection.NATIVE_TO_MANAGED)
        assertNull(result)
        ir.ret()
        ir.finalizeFunction()
    }

    // --- ManagedCallDirection enum ---

    @Test
    fun managedCallDirectionValues() {
        assertEquals(2, ManagedCallDirection.entries.size)
        assertEquals(ManagedCallDirection.MANAGED_TO_NATIVE, ManagedCallDirection.entries[0])
        assertEquals(ManagedCallDirection.NATIVE_TO_MANAGED, ManagedCallDirection.entries[1])
    }

    // --- Printer round-trip ---

    @Test
    fun printAndParseInteriorRefType() {
        val type = Type.InteriorRef(Type.I32)
        val printed = IrPrinter.typeStr(type)
        assertEquals("interiorref<i32>", printed)
    }

    @Test
    fun printAndParsePinnedRefType() {
        val type = Type.PinnedRef(Type.I64)
        val printed = IrPrinter.typeStr(type)
        assertEquals("pinnedref<i64>", printed)
    }

    @Test
    fun printPinInstruction() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("obj", Type.Reference(Type.I64, false))), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)
        ir.pin(param)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()
        val text = IrPrinter.print(module)
        assertTrue(text.contains("gc.pin"), "Should contain gc.pin: $text")
    }

    @Test
    fun printUnpinInstruction() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("obj", Type.Reference(Type.I64, false))), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)
        val pinned = ir.pin(param)
        ir.unpin(pinned)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()
        val text = IrPrinter.print(module)
        assertTrue(text.contains("gc.unpin"), "Should contain gc.unpin: $text")
    }

    @Test
    fun printManagedCall() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("native_fn", listOf(Param("x", Type.I32)), Type.I32)
        ir.createFunction("caller", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val fn = GlobalRef("native_fn", Type.Function(listOf(Type.I32), Type.I32))
        val r = ir.managedCall(fn, listOf(Constant.I32(5)), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)
        ir.ret(r)
        ir.finalizeFunction()
        val text = IrPrinter.print(ir.build())
        assertTrue(text.contains("managed.call managed_to_native"), "Should contain managed.call: $text")
    }

    // --- Text round-trip (print → parse) ---

    @Test
    fun textRoundTripPinUnpin() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("obj", Type.Reference(Type.I64, false))), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)
        val pinned = ir.pin(param)
        ir.unpin(pinned)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val text = IrPrinter.print(module)
        val parsed = IrParser.parse(text)

        assertEquals(module.functions.size, parsed.functions.size)
        val instrs = parsed.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Instruction.Pin)
        assertTrue(instrs[1] is Instruction.Unpin)
    }

    @Test
    fun textRoundTripInteriorPtr() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("obj", Type.Reference(Type.I64, false))), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)
        ir.interiorPtr(param, Constant.I32(0), Type.F64)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val text = IrPrinter.print(module)
        val parsed = IrParser.parse(text)
        val inst = parsed.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Instruction.InteriorPtr)
        assertEquals(Type.F64, (inst as Instruction.InteriorPtr).pointeeType)
    }

    @Test
    fun textRoundTripWriteBarrier() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(
            Param("obj", Type.Reference(Type.I64)),
            Param("val", Type.Reference(Type.I32))
        ), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val obj = Parameter("obj", Type.Reference(Type.I64), 0)
        val v = Parameter("val", Type.Reference(Type.I32), 1)
        ir.writeBarrier(obj, Constant.I32(1), v)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val text = IrPrinter.print(module)
        val parsed = IrParser.parse(text)
        assertTrue(parsed.functions[0].blocks[0].instructions[0] is Instruction.WriteBarrier)
    }

    @Test
    fun textRoundTripReadBarrier() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("r", Type.Reference(Type.I64))), Type.Reference(Type.I64))
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("r", Type.Reference(Type.I64), 0)
        val result = ir.readBarrier(param)
        ir.ret(result)
        ir.finalizeFunction()
        val module = ir.build()

        val text = IrPrinter.print(module)
        val parsed = IrParser.parse(text)
        assertTrue(parsed.functions[0].blocks[0].instructions[0] is Instruction.ReadBarrier)
    }

    @Test
    fun textRoundTripManagedCall() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("native_add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.createFunction("caller", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val fn = GlobalRef("native_add", Type.Function(listOf(Type.I32, Type.I32), Type.I32))
        val r = ir.managedCall(fn, listOf(Constant.I32(3), Constant.I32(4)), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)
        ir.ret(r)
        ir.finalizeFunction()
        val module = ir.build()

        val text = IrPrinter.print(module)
        val parsed = IrParser.parse(text)
        val inst = findFunction(parsed, "caller").blocks[0].instructions[0]
        assertTrue(inst is Instruction.ManagedCall)
        assertEquals(ManagedCallDirection.MANAGED_TO_NATIVE, (inst as Instruction.ManagedCall).direction)
        assertEquals(2, inst.args.size)
    }

    @Test
    fun textRoundTripManagedCallNativeToManaged() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("managed_fn", listOf(Param("x", Type.I32)), Type.Void)
        ir.createFunction("caller", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val fn = GlobalRef("managed_fn", Type.Function(listOf(Type.I32), Type.Void))
        ir.managedCall(fn, listOf(Constant.I32(1)), Type.Void, ManagedCallDirection.NATIVE_TO_MANAGED)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val text = IrPrinter.print(module)
        val parsed = IrParser.parse(text)
        val inst = findFunction(parsed, "caller").blocks[0].instructions[0]
        assertTrue(inst is Instruction.ManagedCall)
        assertEquals(ManagedCallDirection.NATIVE_TO_MANAGED, (inst as Instruction.ManagedCall).direction)
    }

    // --- Binary serialization round-trip ---

    @Test
    fun binaryRoundTripPinUnpin() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("obj", Type.Reference(Type.I64, false))), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)
        val pinned = ir.pin(param)
        ir.unpin(pinned)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val bytes = serializer.serialize(module)
        val deserialized = serializer.deserialize(bytes)

        assertEquals(module.functions.size, deserialized.functions.size)
        val instrs = deserialized.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Instruction.Pin)
        assertTrue(instrs[1] is Instruction.Unpin)
    }

    @Test
    fun binaryRoundTripInteriorPtr() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("obj", Type.Reference(Type.I64, false))), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)
        ir.interiorPtr(param, Constant.I32(0), Type.F64)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val bytes = serializer.serialize(module)
        val deserialized = serializer.deserialize(bytes)
        val inst = deserialized.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Instruction.InteriorPtr)
        assertEquals(Type.F64, (inst as Instruction.InteriorPtr).pointeeType)
    }

    @Test
    fun binaryRoundTripManagedCall() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("native_fn", listOf(Param("x", Type.I32)), Type.I32)
        ir.createFunction("caller", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val fn = GlobalRef("native_fn", Type.Function(listOf(Type.I32), Type.I32))
        val r = ir.managedCall(fn, listOf(Constant.I32(5)), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)
        ir.ret(r)
        ir.finalizeFunction()
        val module = ir.build()

        val bytes = serializer.serialize(module)
        val deserialized = serializer.deserialize(bytes)
        val inst = findFunction(deserialized, "caller").blocks[0].instructions[0]
        assertTrue(inst is Instruction.ManagedCall)
        assertEquals(ManagedCallDirection.MANAGED_TO_NATIVE, (inst as Instruction.ManagedCall).direction)
    }

    @Test
    fun binaryRoundTripWriteBarrier() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(
            Param("obj", Type.Reference(Type.I64)),
            Param("val", Type.Reference(Type.I32))
        ), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val obj = Parameter("obj", Type.Reference(Type.I64), 0)
        val v = Parameter("val", Type.Reference(Type.I32), 1)
        ir.writeBarrier(obj, Constant.I32(0), v)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val bytes = serializer.serialize(module)
        val deserialized = serializer.deserialize(bytes)
        assertTrue(deserialized.functions[0].blocks[0].instructions[0] is Instruction.WriteBarrier)
    }

    @Test
    fun binaryRoundTripReadBarrier() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("r", Type.Reference(Type.I64))), Type.Reference(Type.I64))
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("r", Type.Reference(Type.I64), 0)
        val result = ir.readBarrier(param)
        ir.ret(result)
        ir.finalizeFunction()
        val module = ir.build()

        val bytes = serializer.serialize(module)
        val deserialized = serializer.deserialize(bytes)
        assertTrue(deserialized.functions[0].blocks[0].instructions[0] is Instruction.ReadBarrier)
    }

    @Test
    fun binaryRoundTripInteriorRefType() {
        val interiorType = Type.InteriorRef(Type.F32)
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("p", interiorType)), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val serialized: ByteArray = serializer.serialize(module)
        val deserialized: Module = serializer.deserialize(serialized)
        val paramType = deserialized.functions[0].params[0].type
        assertEquals(interiorType, paramType)
    }

    @Test
    fun binaryRoundTripPinnedRefType() {
        val pinnedType = Type.PinnedRef(Type.I64)
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("p", pinnedType)), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val serialized: ByteArray = serializer.serialize(module)
        val deserialized: Module = serializer.deserialize(serialized)
        val paramType = deserialized.functions[0].params[0].type
        assertEquals(pinnedType, paramType)
    }

    // --- Combined scenarios ---

    @Test
    fun pinAccessUnpinPattern() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("accessField", listOf(
            Param("obj", Type.Reference(Type.I64, false))
        ), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)

        // Pin the object
        val pinned = ir.pin(param)

        // Get interior pointer to a field
        val fieldPtr = ir.interiorPtr(param, Constant.I32(0), Type.I32)

        // Unpin when done
        ir.unpin(pinned)

        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        val module = ir.build()

        val instrs = module.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Instruction.Pin)
        assertTrue(instrs[1] is Instruction.InteriorPtr)
        assertTrue(instrs[2] is Instruction.Unpin)
    }

    @Test
    fun writeBarrierBeforeFieldStore() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("setField", listOf(
            Param("obj", Type.Reference(Type.I64, false)),
            Param("value", Type.Reference(Type.I32, false))
        ), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val obj = Parameter("obj", Type.Reference(Type.I64, false), 0)
        val value = Parameter("value", Type.Reference(Type.I32, false), 1)

        // Write barrier before storing a reference
        ir.writeBarrier(obj, Constant.I32(0), value)
        // Then the actual field write
        ir.putField(obj, "Container", "item", Type.Reference(Type.I32, false), value)

        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val instrs = module.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Instruction.WriteBarrier)
        assertTrue(instrs[1] is Instruction.PutField)
    }

    @Test
    fun managedNativeCallbackChain() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("native_compute", listOf(Param("x", Type.I32)), Type.I32)
        ir.declareFunction("managed_log", listOf(Param("result", Type.I32)), Type.Void)

        ir.createFunction("pipeline", listOf(Param("input", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val input = Parameter("input", Type.I32, 0)

        // Call into native code
        val nativeFn = GlobalRef("native_compute", Type.Function(listOf(Type.I32), Type.I32))
        val result = ir.managedCall(nativeFn, listOf(input), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)!!

        // Call back into managed code
        val managedFn = GlobalRef("managed_log", Type.Function(listOf(Type.I32), Type.Void))
        ir.managedCall(managedFn, listOf(result), Type.Void, ManagedCallDirection.NATIVE_TO_MANAGED)

        ir.ret(result)
        ir.finalizeFunction()
        val module = ir.build()

        val instrs = findFunction(module, "pipeline").blocks[0].instructions
        val mc1 = instrs[0] as Instruction.ManagedCall
        val mc2 = instrs[1] as Instruction.ManagedCall
        assertEquals(ManagedCallDirection.MANAGED_TO_NATIVE, mc1.direction)
        assertEquals(ManagedCallDirection.NATIVE_TO_MANAGED, mc2.direction)
    }

    @Test
    fun fullMixedModeTextRoundTrip() {
        val ir = IrBuilder("mixed", Target.x86_64())
        ir.declareFunction("native_fn", listOf(Param("x", Type.I32)), Type.I32)

        ir.createFunction("mixed_func", listOf(
            Param("obj", Type.Reference(Type.I64, false))
        ), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)

        val pinned = ir.pin(param)
        val iptr = ir.interiorPtr(param, Constant.I32(0), Type.I32)
        val barriered = ir.readBarrier(param)
        val fn = GlobalRef("native_fn", Type.Function(listOf(Type.I32), Type.I32))
        val result = ir.managedCall(fn, listOf(Constant.I32(42)), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)!!
        ir.writeBarrier(param, Constant.I32(1), param)
        ir.unpin(pinned)
        ir.ret(result)
        ir.finalizeFunction()
        val module = ir.build()

        // Print → Parse → compare structure
        val text = IrPrinter.print(module)
        val parsed = IrParser.parse(text)

        val origInstrs = findFunction(module, "mixed_func").blocks[0].instructions
        val parsedInstrs = findFunction(parsed, "mixed_func").blocks[0].instructions
        assertEquals(origInstrs.size, parsedInstrs.size)
        assertTrue(parsedInstrs[0] is Instruction.Pin)
        assertTrue(parsedInstrs[1] is Instruction.InteriorPtr)
        assertTrue(parsedInstrs[2] is Instruction.ReadBarrier)
        assertTrue(parsedInstrs[3] is Instruction.ManagedCall)
        assertTrue(parsedInstrs[4] is Instruction.WriteBarrier)
        assertTrue(parsedInstrs[5] is Instruction.Unpin)
        assertTrue(parsedInstrs[6] is Instruction.Ret)
    }

    @Test
    fun fullMixedModeBinaryRoundTrip() {
        val ir = IrBuilder("mixed", Target.x86_64())
        ir.declareFunction("native_fn", listOf(Param("x", Type.I32)), Type.I32)

        ir.createFunction("mixed_func", listOf(
            Param("obj", Type.Reference(Type.I64, false))
        ), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)

        val pinned = ir.pin(param)
        ir.interiorPtr(param, Constant.I32(0), Type.I32)
        ir.readBarrier(param)
        val fn = GlobalRef("native_fn", Type.Function(listOf(Type.I32), Type.I32))
        val result = ir.managedCall(fn, listOf(Constant.I32(42)), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)!!
        ir.writeBarrier(param, Constant.I32(1), param)
        ir.unpin(pinned)
        ir.ret(result)
        ir.finalizeFunction()
        val module = ir.build()

        val serialized: ByteArray = serializer.serialize(module)
        val deserialized: Module = serializer.deserialize(serialized)

        val origInstrs = findFunction(module, "mixed_func").blocks[0].instructions
        val deserInstrs = findFunction(deserialized, "mixed_func").blocks[0].instructions
        assertEquals(origInstrs.size, deserInstrs.size)
        for (i in origInstrs.indices) {
            assertEquals(origInstrs[i]::class, deserInstrs[i]::class, "Instruction $i type mismatch")
        }
    }
}
