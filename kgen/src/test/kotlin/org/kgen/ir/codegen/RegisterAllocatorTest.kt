package org.kgen.ir.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RegisterAllocatorTest {

    @Test
    fun physicalRegisterEquality() {
        val cls = RegisterClass("gp64", emptyList())
        val rax = PhysicalRegister("RAX", 0, cls)
        val rax2 = PhysicalRegister("RAX", 0, cls)
        assertEquals(rax, rax2)
    }

    @Test
    fun registerClassContainsRegisters() {
        val regs = mutableListOf<PhysicalRegister>()
        val cls = RegisterClass("gp64", regs)
        regs.add(PhysicalRegister("RAX", 0, cls))
        regs.add(PhysicalRegister("RCX", 1, cls))
        assertEquals(2, cls.count)
    }

    @Test
    fun constraintsBuilder() {
        val cls = RegisterClass("gp64", emptyList())
        val rax = PhysicalRegister("RAX", 0, cls)
        val rcx = PhysicalRegister("RCX", 1, cls)
        val rdx = PhysicalRegister("RDX", 2, cls)
        val rbx = PhysicalRegister("RBX", 3, cls)

        val constraints = RegisterConstraints.builder()
            .allocatable(listOf(rcx, rbx))
            .reserved(setOf(rax, rdx))
            .calleeSaved(setOf(rbx))
            .paramRegisters(listOf(rcx, rdx))
            .returnRegisters(listOf(rax))
            .clobber(InstructionClobber.INT_DIV, setOf(rax, rdx))
            .build()

        assertEquals(2, constraints.allocatable.size)
        assertTrue(rax in constraints.reserved)
        assertTrue(rdx in constraints.reserved)
        assertTrue(rbx in constraints.calleeSaved)
        assertEquals(setOf(rax, rdx), constraints.clobbers[InstructionClobber.INT_DIV])
    }

    @Test
    fun valueLocationRegister() {
        val cls = RegisterClass("gp64", emptyList())
        val reg = PhysicalRegister("RCX", 1, cls)
        val loc: ValueLocation = ValueLocation.Register(reg)
        assertTrue(loc is ValueLocation.Register)
        assertEquals(reg, (loc as ValueLocation.Register).reg)
    }

    @Test
    fun valueLocationSpill() {
        val loc: ValueLocation = ValueLocation.SpillSlot(-8)
        assertTrue(loc is ValueLocation.SpillSlot)
        assertEquals(-8, (loc as ValueLocation.SpillSlot).offset)
    }

    @Test
    fun registerAssignmentCreation() {
        val cls = RegisterClass("gp64", emptyList())
        val rcx = PhysicalRegister("RCX", 1, cls)
        val assignment = RegisterAssignment(
            locations = mapOf("x" to ValueLocation.Register(rcx)),
            spillSlots = 0,
            usedCalleeRegisters = emptySet(),
        )
        assertEquals(1, assignment.locations.size)
        assertTrue(assignment.locations["x"] is ValueLocation.Register)
    }

    @Test
    fun instructionClobberValues() {
        assertEquals(3, InstructionClobber.entries.size)
        assertTrue(InstructionClobber.INT_DIV in InstructionClobber.entries)
        assertTrue(InstructionClobber.VARIABLE_SHIFT in InstructionClobber.entries)
        assertTrue(InstructionClobber.CALL in InstructionClobber.entries)
    }
}
