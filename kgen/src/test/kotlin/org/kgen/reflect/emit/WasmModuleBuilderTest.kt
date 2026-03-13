package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WasmModuleBuilderTest {

    @Nested
    inner class Construction {

        @Test
        fun constructsWithName() {
            val mod = WasmModuleBuilder("myModule")
            assertEquals("myModule", mod.name)
        }

        @Test
        fun constructsViaFactory() {
            val mod = ModuleBuilder.wasm("testWasm")
            assertInstanceOf(WasmModuleBuilder::class.java, mod)
            assertEquals("testWasm", mod.name)
        }
    }

    @Nested
    inner class Assembler {

        @Test
        fun assemblerIsNotNull() {
            val mod = WasmModuleBuilder("test")
            assertNotNull(mod.assembler())
        }

        @Test
        fun assemblerReturnsSameInstance() {
            val mod = WasmModuleBuilder("test")
            assertSame(mod.assembler(), mod.assembler())
        }
    }

    @Nested
    inner class ToBytes {

        @Test
        fun toBytesReturnsWasmMagicNumber() {
            val mod = WasmModuleBuilder("test")
            val bytes = mod.toBytes()
            assertTrue(bytes.isNotEmpty())
            assertEquals(0x00.toByte(), bytes[0])
            assertEquals(0x61.toByte(), bytes[1])
            assertEquals(0x73.toByte(), bytes[2])
            assertEquals(0x6D.toByte(), bytes[3])
        }

        @Test
        fun toBytesReturnsWasmVersion() {
            val mod = WasmModuleBuilder("test")
            val bytes = mod.toBytes()
            assertTrue(bytes.size >= 8)
            assertEquals(0x01.toByte(), bytes[4])
            assertEquals(0x00.toByte(), bytes[5])
            assertEquals(0x00.toByte(), bytes[6])
            assertEquals(0x00.toByte(), bytes[7])
        }
    }
}
