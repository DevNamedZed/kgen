package org.kgen.reflect.process

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MemoryMappingTest {

    private fun mapping(
        start: Long = 0x7f000000L,
        end: Long = 0x7f001000L,
        permissions: String = "r-xp",
        offset: Long = 0,
        device: String = "08:01",
        inode: Long = 12345,
        path: String? = "/usr/lib/libc.so.6",
    ): RemoteProcess.MemoryMapping {
        return RemoteProcess.MemoryMapping(start, end, permissions, offset, device, inode, path)
    }

    @Nested
    inner class Size {
        @Test
        fun computesSizeFromAddressRange() {
            val m = mapping(start = 0x1000, end = 0x3000)
            assertEquals(0x2000L, m.size)
        }

        @Test
        fun zeroSizeWhenStartEqualsEnd() {
            val m = mapping(start = 0x5000, end = 0x5000)
            assertEquals(0L, m.size)
        }

        @Test
        fun largeSizeForBigRegion() {
            val m = mapping(start = 0, end = 0x10000000L)
            assertEquals(0x10000000L, m.size)
        }
    }

    @Nested
    inner class Permissions {
        @Test
        fun readableWhenFirstCharIsR() {
            val m = mapping(permissions = "r---")
            assertTrue(m.isReadable)
        }

        @Test
        fun notReadableWhenFirstCharIsDash() {
            val m = mapping(permissions = "----")
            assertFalse(m.isReadable)
        }

        @Test
        fun writableWhenSecondCharIsW() {
            val m = mapping(permissions = "-w--")
            assertTrue(m.isWritable)
        }

        @Test
        fun notWritableWhenSecondCharIsDash() {
            val m = mapping(permissions = "r---")
            assertFalse(m.isWritable)
        }

        @Test
        fun executableWhenThirdCharIsX() {
            val m = mapping(permissions = "--x-")
            assertTrue(m.isExecutable)
        }

        @Test
        fun notExecutableWhenThirdCharIsDash() {
            val m = mapping(permissions = "rw-p")
            assertFalse(m.isExecutable)
        }

        @Test
        fun privateWhenFourthCharIsP() {
            val m = mapping(permissions = "r-xp")
            assertTrue(m.isPrivate)
        }

        @Test
        fun notPrivateWhenFourthCharIsS() {
            val m = mapping(permissions = "r-xs")
            assertFalse(m.isPrivate)
        }

        @Test
        fun fullPermissions() {
            val m = mapping(permissions = "rwxp")
            assertTrue(m.isReadable)
            assertTrue(m.isWritable)
            assertTrue(m.isExecutable)
            assertTrue(m.isPrivate)
        }

        @Test
        fun emptyPermissionsString() {
            val m = mapping(permissions = "")
            assertFalse(m.isReadable)
            assertFalse(m.isWritable)
            assertFalse(m.isExecutable)
            assertFalse(m.isPrivate)
        }
    }

    @Nested
    inner class IsFile {
        @Test
        fun fileWhenPathStartsWithSlash() {
            val m = mapping(path = "/usr/lib/libc.so.6")
            assertTrue(m.isFile)
        }

        @Test
        fun notFileWhenPathIsNull() {
            val m = mapping(path = null)
            assertFalse(m.isFile)
        }

        @Test
        fun notFileForSpecialMapping() {
            val m = mapping(path = "[vdso]")
            assertFalse(m.isFile)
        }

        @Test
        fun notFileForHeap() {
            val m = mapping(path = "[heap]")
            assertFalse(m.isFile)
        }

        @Test
        fun notFileForStack() {
            val m = mapping(path = "[stack]")
            assertFalse(m.isFile)
        }
    }

    @Nested
    inner class DataClass {
        @Test
        fun equalsWhenSameValues() {
            val m1 = mapping()
            val m2 = mapping()
            assertEquals(m1, m2)
        }

        @Test
        fun notEqualsWhenDifferentPath() {
            val m1 = mapping(path = "/a")
            val m2 = mapping(path = "/b")
            assertNotEquals(m1, m2)
        }

        @Test
        fun hashCodeConsistent() {
            val m1 = mapping()
            val m2 = mapping()
            assertEquals(m1.hashCode(), m2.hashCode())
        }

        @Test
        fun toStringContainsAddress() {
            val m = mapping(start = 0x7f000000L)
            val str = m.toString()
            assertTrue(str.contains("7f000000") || str.contains("2130706432"))
        }

        @Test
        fun copyChangesField() {
            val m = mapping(path = "/a")
            val m2 = m.copy(path = "/b")
            assertEquals("/b", m2.path)
            assertEquals("/a", m.path)
        }
    }
}
