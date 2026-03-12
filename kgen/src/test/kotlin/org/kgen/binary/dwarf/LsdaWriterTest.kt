package org.kgen.binary.dwarf

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class LsdaWriterTest {

    @Test
    fun writeEmptyTable() {
        val table = LsdaTable(emptyList())
        val result = LsdaWriter.write(table)
        assertTrue(result.isNotEmpty(), "Even empty LSDA should have a header")
    }

    @Test
    fun writeSingleCallSite() {
        val table = LsdaTable(
            callSites = listOf(CallSiteEntry(0, 5, 20, 0)),
        )
        val result = LsdaWriter.write(table)
        assertTrue(result.isNotEmpty())
        assertTrue(result.size > 3) // header + at least the call site
    }

    @Test
    fun writeCallSiteWithAction() {
        val table = LsdaTable(
            callSites = listOf(CallSiteEntry(0, 5, 20, 1)),
            actions = listOf(ActionEntry(1, 0)),
            typeNames = listOf("java/lang/Exception"),
        )
        val result = LsdaWriter.write(table)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun writeMultipleCallSites() {
        val table = LsdaTable(
            callSites = listOf(
                CallSiteEntry(0, 5, 30, 1),
                CallSiteEntry(10, 5, 30, 1),
                CallSiteEntry(20, 5, 0, 0),
            ),
            actions = listOf(ActionEntry(1, 0)),
            typeNames = listOf("Exception"),
        )
        val result = LsdaWriter.write(table)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun writeMultipleTypes() {
        val table = LsdaTable(
            callSites = listOf(
                CallSiteEntry(0, 5, 20, 1),
                CallSiteEntry(10, 5, 30, 2),
            ),
            actions = listOf(
                ActionEntry(1, 0),
                ActionEntry(2, 0),
            ),
            typeNames = listOf("IOException", "RuntimeException"),
        )
        val result = LsdaWriter.write(table)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun writeCleanupOnly() {
        val table = LsdaTable(
            callSites = listOf(CallSiteEntry(0, 5, 20, 0)),
        )
        val result = LsdaWriter.write(table)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun writeNoLandingPad() {
        val table = LsdaTable(
            callSites = listOf(CallSiteEntry(0, 5, 0, 0)),
        )
        val result = LsdaWriter.write(table)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun headerStartsWithLandingPadEncoding() {
        val table = LsdaTable(emptyList())
        val result = LsdaWriter.write(table)
        // First byte: DW_EH_PE_omit (0xFF) for landing pad base
        assertEquals(0xFF.toByte(), result[0])
    }

    @Test
    fun headerWithTypesHasUdata4Encoding() {
        val table = LsdaTable(
            callSites = listOf(CallSiteEntry(0, 5, 20, 1)),
            actions = listOf(ActionEntry(1, 0)),
            typeNames = listOf("Test"),
        )
        val result = LsdaWriter.write(table)
        // First byte: 0xFF (LP base encoding)
        assertEquals(0xFF.toByte(), result[0])
        // Second byte: DW_EH_PE_udata4 (0x03) for type table encoding
        assertEquals(0x03.toByte(), result[1])
    }

    @Test
    fun headerWithoutTypesHasOmitEncoding() {
        val table = LsdaTable(
            callSites = listOf(CallSiteEntry(0, 5, 0, 0)),
        )
        val result = LsdaWriter.write(table)
        // First byte: 0xFF
        assertEquals(0xFF.toByte(), result[0])
        // Second byte: 0xFF (DW_EH_PE_omit — no type table)
        assertEquals(0xFF.toByte(), result[1])
    }

    @Test
    fun lsdaTableDataClass() {
        val table = LsdaTable(
            callSites = listOf(CallSiteEntry(0, 5, 20, 1)),
            actions = listOf(ActionEntry(1, 0)),
            typeNames = listOf("Foo"),
        )
        assertEquals(1, table.callSites.size)
        assertEquals(1, table.actions.size)
        assertEquals("Foo", table.typeNames[0])
    }

    @Test
    fun callSiteEntryData() {
        val cs = CallSiteEntry(10, 5, 30, 2)
        assertEquals(10, cs.callOffset)
        assertEquals(5, cs.callLength)
        assertEquals(30, cs.landingPadOffset)
        assertEquals(2, cs.actionIndex)
    }

    @Test
    fun actionEntryData() {
        val ae = ActionEntry(3, 2)
        assertEquals(3, ae.typeIndex)
        assertEquals(2, ae.nextAction)
    }

    @Test
    fun largerTableProducesMoreBytes() {
        val small = LsdaWriter.write(LsdaTable(
            listOf(CallSiteEntry(0, 5, 20, 0))
        ))
        val large = LsdaWriter.write(LsdaTable(
            listOf(
                CallSiteEntry(0, 5, 20, 1),
                CallSiteEntry(10, 5, 30, 1),
                CallSiteEntry(20, 5, 40, 1),
            ),
            listOf(ActionEntry(1, 0)),
            listOf("Exception"),
        ))
        assertTrue(large.size > small.size)
    }
}
