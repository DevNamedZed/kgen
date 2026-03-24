package org.wark

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import java.io.ByteArrayOutputStream

class CallIndirectJitTest {

    @Test
    fun `call_indirect dispatches to correct function`() {
        val wasmBytes = buildCallIndirectModule()
        val instance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
            .load(wasmBytes).instantiate(WarkImports.empty())

        // test(0, 5) → double(5) = 10
        assertEquals(10L, instance.call("test", 0L, 5L)[0])
        // test(1, 5) → triple(5) = 15
        assertEquals(15L, instance.call("test", 1L, 5L)[0])
        // test(0, 7) → double(7) = 14
        assertEquals(14L, instance.call("test", 0L, 7L)[0])
    }

    @Test
    fun `call_indirect works in interpreter mode`() {
        val wasmBytes = buildCallIndirectModule()
        val instance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
            .load(wasmBytes).instantiate(WarkImports.empty())
        instance.setInstructionLimit(Long.MAX_VALUE)

        assertEquals(10L, instance.call("test", 0L, 5L)[0])
        assertEquals(15L, instance.call("test", 1L, 5L)[0])
    }

    private fun buildCallIndirectModule(): ByteArray {
        val buf = ByteArrayOutputStream()
        // WASM magic + version
        buf.write(byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00))

        // Type section (id=1): 2 types
        writeSection(buf, 1) { s ->
            writeLeb(s, 2) // 2 types
            // type 0: (i32) -> i32
            s.write(0x60); writeLeb(s, 1); s.write(0x7F); writeLeb(s, 1); s.write(0x7F)
            // type 1: (i32, i32) -> i32
            s.write(0x60); writeLeb(s, 2); s.write(0x7F); s.write(0x7F); writeLeb(s, 1); s.write(0x7F)
        }

        // Function section (id=3): 3 functions
        writeSection(buf, 3) { s ->
            writeLeb(s, 3)
            writeLeb(s, 0) // func 0: type 0
            writeLeb(s, 0) // func 1: type 0
            writeLeb(s, 1) // func 2: type 1
        }

        // Table section (id=4)
        writeSection(buf, 4) { s ->
            writeLeb(s, 1)
            s.write(0x70) // funcref
            s.write(0x00) // no max
            writeLeb(s, 2) // min=2
        }

        // Memory section (id=5)
        writeSection(buf, 5) { s ->
            writeLeb(s, 1); s.write(0x00); writeLeb(s, 1)
        }

        // Export section (id=7): export "test" and "memory"
        writeSection(buf, 7) { s ->
            writeLeb(s, 2)
            writeString(s, "test"); s.write(0x00); writeLeb(s, 2)
            writeString(s, "memory"); s.write(0x02); writeLeb(s, 0)
        }

        // Element section (id=9): table 0, offset 0, [func 0, func 1]
        writeSection(buf, 9) { s ->
            writeLeb(s, 1) // 1 element segment
            writeLeb(s, 0) // table index 0
            s.write(0x41); writeLeb(s, 0); s.write(0x0B) // i32.const 0, end
            writeLeb(s, 2) // 2 entries
            writeLeb(s, 0) // func 0
            writeLeb(s, 1) // func 1
        }

        // Code section (id=10): 3 function bodies
        writeSection(buf, 10) { s ->
            writeLeb(s, 3)

            // func 0: double(x) = x * 2
            writeBody(s) { b ->
                b.write(0x20); writeLeb(b, 0)  // local.get 0
                b.write(0x41); writeLeb(b, 2)  // i32.const 2
                b.write(0x6C)                   // i32.mul
            }

            // func 1: triple(x) = x * 3
            writeBody(s) { b ->
                b.write(0x20); writeLeb(b, 0)  // local.get 0
                b.write(0x41); writeLeb(b, 3)  // i32.const 3
                b.write(0x6C)                   // i32.mul
            }

            // func 2: test(index, arg) = call_indirect[type 0](arg, index)
            writeBody(s) { b ->
                b.write(0x20); writeLeb(b, 1)  // local.get 1 (arg)
                b.write(0x20); writeLeb(b, 0)  // local.get 0 (table index)
                b.write(0x11); writeLeb(b, 0); writeLeb(b, 0) // call_indirect type=0 table=0
            }
        }

        return buf.toByteArray()
    }

    private fun writeSection(buf: ByteArrayOutputStream, id: Int, builder: (ByteArrayOutputStream) -> Unit) {
        val content = ByteArrayOutputStream()
        builder(content)
        val bytes = content.toByteArray()
        buf.write(id)
        writeLeb(buf, bytes.size)
        buf.write(bytes)
    }

    private fun writeBody(buf: ByteArrayOutputStream, builder: (ByteArrayOutputStream) -> Unit) {
        val body = ByteArrayOutputStream()
        writeLeb(body, 0) // 0 locals
        builder(body)
        body.write(0x0B) // end
        val bytes = body.toByteArray()
        writeLeb(buf, bytes.size)
        buf.write(bytes)
    }

    private fun writeString(buf: ByteArrayOutputStream, str: String) {
        val bytes = str.toByteArray()
        writeLeb(buf, bytes.size)
        buf.write(bytes)
    }

    private fun writeLeb(buf: ByteArrayOutputStream, value: Int) {
        var remaining = value
        do {
            var byte = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining != 0) { byte = byte or 0x80 }
            buf.write(byte)
        } while (remaining != 0)
    }
}
