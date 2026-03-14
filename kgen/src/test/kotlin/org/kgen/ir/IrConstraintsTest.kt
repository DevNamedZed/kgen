package org.kgen.ir

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IrConstraintsTest {

    @Nested
    inner class StructuralPresetTests {

        @Test
        fun containsTerminator() {
            assertTrue(IrConstraints.STRUCTURAL.contains(IrCategory.TERMINATOR))
        }

        @Test
        fun containsCall() {
            assertTrue(IrConstraints.STRUCTURAL.contains(IrCategory.CALL))
        }

        @Test
        fun containsSsa() {
            assertTrue(IrConstraints.STRUCTURAL.contains(IrCategory.SSA))
        }

        @Test
        fun containsDebug() {
            assertTrue(IrConstraints.STRUCTURAL.contains(IrCategory.DEBUG))
        }

        @Test
        fun containsIntrinsic() {
            assertTrue(IrConstraints.STRUCTURAL.contains(IrCategory.INTRINSIC))
        }

        @Test
        fun doesNotContainArithmetic() {
            assertFalse(IrConstraints.STRUCTURAL.contains(IrCategory.ARITHMETIC))
        }

        @Test
        fun doesNotContainObject() {
            assertFalse(IrConstraints.STRUCTURAL.contains(IrCategory.OBJECT))
        }

        @Test
        fun hasExactlyFiveCategories() {
            assertEquals(5, IrConstraints.STRUCTURAL.size)
        }

        @Test
        fun isUnmodifiable() {
            assertThrows<UnsupportedOperationException> {
                (IrConstraints.STRUCTURAL as MutableSet<IrCategory>).add(IrCategory.MEMORY)
            }
        }
    }

    @Nested
    inner class NativePresetTests {

        @Test
        fun containsAllStructural() {
            assertTrue(IrConstraints.NATIVE.containsAll(IrConstraints.STRUCTURAL))
        }

        @Test
        fun containsMachineCategories() {
            val machineCategories = setOf(
                IrCategory.ARITHMETIC, IrCategory.BITWISE, IrCategory.COMPARISON,
                IrCategory.CONVERSION, IrCategory.MEMORY, IrCategory.ATOMIC,
                IrCategory.VECTOR, IrCategory.AGGREGATE, IrCategory.EXCEPTION
            )
            assertTrue(IrConstraints.NATIVE.containsAll(machineCategories))
        }

        @Test
        fun doesNotContainRuntime() {
            assertFalse(IrConstraints.NATIVE.contains(IrCategory.RUNTIME))
        }

        @Test
        fun doesNotContainInterop() {
            assertFalse(IrConstraints.NATIVE.contains(IrCategory.INTEROP))
        }

        @Test
        fun doesNotContainObject() {
            assertFalse(IrConstraints.NATIVE.contains(IrCategory.OBJECT))
        }

        @Test
        fun hasCorrectSize() {
            assertEquals(14, IrConstraints.NATIVE.size)
        }
    }

    @Nested
    inner class RuntimeNativePresetTests {

        @Test
        fun containsAllNative() {
            assertTrue(IrConstraints.RUNTIME_NATIVE.containsAll(IrConstraints.NATIVE))
        }

        @Test
        fun containsRuntime() {
            assertTrue(IrConstraints.RUNTIME_NATIVE.contains(IrCategory.RUNTIME))
        }

        @Test
        fun doesNotContainInterop() {
            assertFalse(IrConstraints.RUNTIME_NATIVE.contains(IrCategory.INTEROP))
        }

        @Test
        fun doesNotContainObject() {
            assertFalse(IrConstraints.RUNTIME_NATIVE.contains(IrCategory.OBJECT))
        }

        @Test
        fun hasCorrectSize() {
            assertEquals(15, IrConstraints.RUNTIME_NATIVE.size)
        }
    }

    @Nested
    inner class MixedPresetTests {

        @Test
        fun containsAllRuntimeNative() {
            assertTrue(IrConstraints.MIXED.containsAll(IrConstraints.RUNTIME_NATIVE))
        }

        @Test
        fun containsInterop() {
            assertTrue(IrConstraints.MIXED.contains(IrCategory.INTEROP))
        }

        @Test
        fun doesNotContainObject() {
            assertFalse(IrConstraints.MIXED.contains(IrCategory.OBJECT))
        }

        @Test
        fun hasCorrectSize() {
            assertEquals(16, IrConstraints.MIXED.size)
        }
    }

    @Nested
    inner class ManagedVmPresetTests {

        @Test
        fun containsAllStructural() {
            assertTrue(IrConstraints.MANAGED_VM.containsAll(IrConstraints.STRUCTURAL))
        }

        @Test
        fun containsObject() {
            assertTrue(IrConstraints.MANAGED_VM.contains(IrCategory.OBJECT))
        }

        @Test
        fun doesNotContainArithmetic() {
            assertFalse(IrConstraints.MANAGED_VM.contains(IrCategory.ARITHMETIC))
        }

        @Test
        fun doesNotContainMemory() {
            assertFalse(IrConstraints.MANAGED_VM.contains(IrCategory.MEMORY))
        }

        @Test
        fun doesNotContainRuntime() {
            assertFalse(IrConstraints.MANAGED_VM.contains(IrCategory.RUNTIME))
        }

        @Test
        fun doesNotContainInterop() {
            assertFalse(IrConstraints.MANAGED_VM.contains(IrCategory.INTEROP))
        }

        @Test
        fun hasCorrectSize() {
            assertEquals(6, IrConstraints.MANAGED_VM.size)
        }
    }

    @Nested
    inner class ManagedNativePresetTests {

        @Test
        fun containsCoreCategories() {
            val expected = setOf(
                IrCategory.TERMINATOR, IrCategory.CALL, IrCategory.SSA,
                IrCategory.DEBUG, IrCategory.INTRINSIC,
                IrCategory.ARITHMETIC, IrCategory.BITWISE, IrCategory.COMPARISON,
                IrCategory.CONVERSION, IrCategory.MEMORY, IrCategory.ATOMIC,
                IrCategory.VECTOR, IrCategory.AGGREGATE, IrCategory.EXCEPTION,
                IrCategory.RUNTIME, IrCategory.INTEROP, IrCategory.OBJECT,
            )
            for (category in expected) {
                assertTrue(IrConstraints.MANAGED_NATIVE.contains(category)) {
                    "MANAGED_NATIVE should contain $category"
                }
            }
        }

        @Test
        fun doesNotContainDeoptOrCompute() {
            assertFalse(IrConstraints.MANAGED_NATIVE.contains(IrCategory.DEOPTIMIZATION))
            assertFalse(IrConstraints.MANAGED_NATIVE.contains(IrCategory.COMPUTE))
        }
    }

    @Nested
    inner class JitPresetTests {

        @Test
        fun containsDeoptimization() {
            assertTrue(IrConstraints.JIT.contains(IrCategory.DEOPTIMIZATION))
        }

        @Test
        fun containsManagedNativeCategories() {
            assertTrue(IrConstraints.JIT.containsAll(IrConstraints.MANAGED_NATIVE))
        }
    }

    @Nested
    inner class ComputeKernelPresetTests {

        @Test
        fun containsCompute() {
            assertTrue(IrConstraints.COMPUTE_KERNEL.contains(IrCategory.COMPUTE))
        }

        @Test
        fun doesNotContainObject() {
            assertFalse(IrConstraints.COMPUTE_KERNEL.contains(IrCategory.OBJECT))
        }

        @Test
        fun doesNotContainException() {
            assertFalse(IrConstraints.COMPUTE_KERNEL.contains(IrCategory.EXCEPTION))
        }
    }

    @Nested
    inner class AllPresetTests {

        @Test
        fun containsAllCategories() {
            for (category in IrCategory.entries) {
                assertTrue(IrConstraints.ALL.contains(category)) {
                    "ALL should contain $category"
                }
            }
        }

        @Test
        fun sizeEqualsAllCategoriesCount() {
            assertEquals(IrCategory.entries.size, IrConstraints.ALL.size)
        }
    }

    @Nested
    inner class PresetHierarchyTests {

        @Test
        fun structuralIsSubsetOfNative() {
            assertTrue(IrConstraints.NATIVE.containsAll(IrConstraints.STRUCTURAL))
        }

        @Test
        fun nativeIsSubsetOfRuntimeNative() {
            assertTrue(IrConstraints.RUNTIME_NATIVE.containsAll(IrConstraints.NATIVE))
        }

        @Test
        fun runtimeNativeIsSubsetOfMixed() {
            assertTrue(IrConstraints.MIXED.containsAll(IrConstraints.RUNTIME_NATIVE))
        }

        @Test
        fun mixedIsSubsetOfAll() {
            assertTrue(IrConstraints.ALL.containsAll(IrConstraints.MIXED))
        }

        @Test
        fun managedVmIsSubsetOfAll() {
            assertTrue(IrConstraints.ALL.containsAll(IrConstraints.MANAGED_VM))
        }

        @Test
        fun managedVmIsNotSubsetOfNative() {
            assertFalse(IrConstraints.NATIVE.containsAll(IrConstraints.MANAGED_VM))
        }

        @Test
        fun nativeIsNotSubsetOfManagedVm() {
            assertFalse(IrConstraints.MANAGED_VM.containsAll(IrConstraints.NATIVE))
        }
    }

    @Nested
    inner class SubmoduleTests {

        @Test
        fun hasCorrectName() {
            val sub = Submodule("gc", IrConstraints.RUNTIME_NATIVE)
            assertEquals("gc", sub.name)
        }

        @Test
        fun hasCorrectConstraints() {
            val sub = Submodule("gc", IrConstraints.RUNTIME_NATIVE)
            assertEquals(IrConstraints.RUNTIME_NATIVE, sub.constraints)
        }

        @Test
        fun defaultFunctionsAreEmpty() {
            val sub = Submodule("gc", IrConstraints.NATIVE)
            assertTrue(sub.functions.isEmpty())
        }

        @Test
        fun defaultGlobalsAreEmpty() {
            val sub = Submodule("gc", IrConstraints.NATIVE)
            assertTrue(sub.globals.isEmpty())
        }

        @Test
        fun withFunctions() {
            val sub = Submodule("app", IrConstraints.ALL, functions = listOf("main", "init"))
            assertEquals(listOf("main", "init"), sub.functions)
        }

        @Test
        fun withGlobals() {
            val sub = Submodule("data", IrConstraints.NATIVE, globals = listOf("counter", "buffer"))
            assertEquals(listOf("counter", "buffer"), sub.globals)
        }

        @Test
        fun equality() {
            val a = Submodule("gc", IrConstraints.NATIVE, listOf("alloc"), listOf("heap"))
            val b = Submodule("gc", IrConstraints.NATIVE, listOf("alloc"), listOf("heap"))
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityByName() {
            assertNotEquals(
                Submodule("a", IrConstraints.NATIVE),
                Submodule("b", IrConstraints.NATIVE)
            )
        }

        @Test
        fun inequalityByConstraints() {
            assertNotEquals(
                Submodule("gc", IrConstraints.NATIVE),
                Submodule("gc", IrConstraints.ALL)
            )
        }

        @Test
        fun inequalityByFunctions() {
            assertNotEquals(
                Submodule("gc", IrConstraints.NATIVE, functions = listOf("a")),
                Submodule("gc", IrConstraints.NATIVE, functions = listOf("b"))
            )
        }
    }

    @Nested
    inner class IrTierTests {

        @Test
        fun structuralHasLevel0() {
            assertEquals(0, IrTier.STRUCTURAL.level)
        }

        @Test
        fun machineHasLevel1() {
            assertEquals(1, IrTier.MACHINE.level)
        }

        @Test
        fun runtimeHasLevel2() {
            assertEquals(2, IrTier.RUNTIME.level)
        }

        @Test
        fun interopHasLevel2() {
            assertEquals(2, IrTier.INTEROP.level)
        }

        @Test
        fun objectHasLevel3() {
            assertEquals(3, IrTier.OBJECT.level)
        }

        @Test
        fun hasFiveEntries() {
            assertEquals(5, IrTier.entries.size)
        }

        @Test
        fun runtimeAndInteropShareSameLevel() {
            assertEquals(IrTier.RUNTIME.level, IrTier.INTEROP.level)
        }

        @Test
        fun levelOrdering() {
            assertTrue(IrTier.STRUCTURAL.level < IrTier.MACHINE.level)
            assertTrue(IrTier.MACHINE.level < IrTier.RUNTIME.level)
            assertTrue(IrTier.RUNTIME.level < IrTier.OBJECT.level)
        }
    }

    @Nested
    inner class IrCategoryTierMappingTests {

        @Test
        fun structuralCategoriesMapToStructuralTier() {
            val structural = listOf(
                IrCategory.TERMINATOR, IrCategory.CALL, IrCategory.SSA,
                IrCategory.DEBUG, IrCategory.INTRINSIC
            )
            for (cat in structural) {
                assertEquals(IrTier.STRUCTURAL, cat.tier) { "$cat should map to STRUCTURAL" }
            }
        }

        @Test
        fun machineCategoriesMapToMachineTier() {
            val machine = listOf(
                IrCategory.ARITHMETIC, IrCategory.BITWISE, IrCategory.COMPARISON,
                IrCategory.CONVERSION, IrCategory.MEMORY, IrCategory.ATOMIC,
                IrCategory.VECTOR, IrCategory.AGGREGATE, IrCategory.EXCEPTION
            )
            for (cat in machine) {
                assertEquals(IrTier.MACHINE, cat.tier) { "$cat should map to MACHINE" }
            }
        }

        @Test
        fun runtimeCategoryMapsToRuntimeTier() {
            assertEquals(IrTier.RUNTIME, IrCategory.RUNTIME.tier)
        }

        @Test
        fun interopCategoryMapsToInteropTier() {
            assertEquals(IrTier.INTEROP, IrCategory.INTEROP.tier)
        }

        @Test
        fun objectCategoryMapsToObjectTier() {
            assertEquals(IrTier.OBJECT, IrCategory.OBJECT.tier)
        }

        @Test
        fun allCategoriesHaveATier() {
            for (cat in IrCategory.entries) {
                assertNotNull(cat.tier) { "$cat should have a tier" }
            }
        }
    }
}
