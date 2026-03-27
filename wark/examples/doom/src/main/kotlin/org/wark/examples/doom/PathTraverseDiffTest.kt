package org.wark.examples.doom

import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.nio.file.Files
import java.nio.file.Path

object PathTraverseDiffTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val wasmPath = Path.of("../assets/doom_wasm_debug.wasm")
        if (!Files.exists(wasmPath)) { println("WASM not found"); return }
        val wasmBytes = Files.readAllBytes(wasmPath)

        val sharedTime = java.util.concurrent.atomic.AtomicLong(0)
        val timeFn = org.wark.HostFunction { _, _ -> longArrayOf(sharedTime.incrementAndGet()) }

        println("Booting JIT...")
        val jitBuilder = org.wark.WarkImports.builder()
        DoomHost(ByteArray(0)).registerImports(jitBuilder)
        jitBuilder.function("runtimeControl", "timeInMilliseconds", timeFn)
        val jitInstance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
            .load(wasmBytes).instantiate(jitBuilder.build())
        jitInstance.call("initGame")
        println("initGame done")

        for (tick in 1..35) { jitInstance.call("tickGame") }
        println("35 ticks done")

        val memSize = jitInstance.memory().sizeBytes()
        val snapshot = jitInstance.memory().readBytes(0, memSize)
        val globalCount = jitInstance.module.wasmModule.globals.size
        val savedGlobals = LongArray(globalCount) { jitInstance.global(it).rawValue() }

        println("Setting up interpreter...")
        val interpBuilder = org.wark.WarkImports.builder()
        DoomHost(ByteArray(0)).registerImports(interpBuilder)
        interpBuilder.function("runtimeControl", "timeInMilliseconds", timeFn)
        val interpInstance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
            .load(wasmBytes).instantiate(interpBuilder.build())
        if (memSize / 65536 > interpInstance.memory().pages()) {
            interpInstance.memory().grow(memSize / 65536 - interpInstance.memory().pages())
        }
        interpInstance.memory().writeBytes(0, snapshot)
        for (i in 0 until globalCount) {
            if (interpInstance.global(i).mutable) { interpInstance.global(i).setI64(savedGlobals[i]) }
        }
        interpInstance.setInstructionLimit(100_000_000)

        val wasmModule = jitInstance.module.wasmModule
        val importCount = wasmModule.importedFunctionCount
        fun findFunc(name: String): Int {
            for (i in 0 until wasmModule.functions.size) {
                val gIdx = i + importCount
                if (wasmModule.functionName(gIdx) == name) { return gIdx }
            }
            return -1
        }

        // Test PTR_ShootTraverse with a fake LINE intercept
        val ptrShootIdx = findFunc("PTR_ShootTraverse")
        println("\nPTR_ShootTraverse = func_$ptrShootIdx")

        if (ptrShootIdx >= 0) {
            // Set up shootz, shootthing, aimslope, la_damage globals
            // shootz at 4385280, shootthing at 4385288, aimslope at 4385296, la_damage at 4385300
            // These should already be set from the game state

            // Create a fake intercept_t at a high memory address
            // intercept_t = { frac: i32, isaline: i32, d: i32 (line* or mobj*) }
            val interceptAddr = memSize - 200

            // Find a valid line pointer — read from the first line in the lines array
            // linebuffer address stored in game state. Let's search for it.
            // Actually, just use the intercept data from a REAL P_PathTraverse call.
            // Call P_PathTraverse through JIT first, then read the intercept array.

            // First, we need valid trace coordinates. Let's read from the blockmap.
            // bmaporgx/bmaporgy are stored in known globals.
            // Actually, let's just try calling P_PathTraverse from a known position
            // and see what intercepts we get, then test PTR_ShootTraverse on those.

            // Easier: just test with isaline=1 and a pointer to ANY valid line
            // The first thing PTR_ShootTraverse does for a line: check li->special, check li->flags
            // If we point to garbage, it might crash. Need a valid line pointer.

            // Read lines array from memory. DOOM stores lines in a contiguous array.
            // The address is in a global. Let's search for it.
            // Actually, let's just read from the intercepts that the previous P_PathTraverse call left.

            println("Testing with fake intercepts...")

            // Test 1: isaline=1 (line), frac=32768 (halfway), ptr=0 (invalid)
            // This should at least test the branch logic without crashing
            for (instance in listOf("JIT" to jitInstance, "Interp" to interpInstance)) {
                val (label, inst) = instance
                // Restore state
                inst.memory().writeBytes(0, snapshot)
                for (i in 0 until globalCount) {
                    if (inst.global(i).mutable) { inst.global(i).setI64(savedGlobals[i]) }
                }

                // Set la_damage to 10
                inst.memory().writeI32(4385300, 10)

                // Create fake intercept: frac=32768, isaline=1, line_ptr=point to valid memory
                // Use a spot in BSS that likely has a line-like struct (flags at offset 16)
                inst.memory().writeI32(interceptAddr, 32768)   // frac
                inst.memory().writeI32(interceptAddr + 4, 1)   // isaline = true
                inst.memory().writeI32(interceptAddr + 8, 0x500000) // line ptr (in BSS area)

                // Make sure the "line" at 0x500000 has flags with ML_TWOSIDED (0x4) clear
                // so PTR_ShootTraverse hits the line and spawns a puff
                inst.memory().writeI32(0x500000 + 16, 0) // flags = 0 (one-sided, solid)
                inst.memory().writeI32(0x500000 + 18, 0) // special = 0

                try {
                    val result = inst.callByIndex(ptrShootIdx, interceptAddr.toLong())
                    println("  $label: PTR_ShootTraverse(frac=32768, isaline=1) = ${result[0]}")
                } catch (e: Exception) {
                    println("  $label: ERROR: ${e.message?.take(80)}")
                }
            }

            // Test 2: isaline=0 (thing), frac=32768
            for (instance in listOf("JIT" to jitInstance, "Interp" to interpInstance)) {
                val (label, inst) = instance
                inst.memory().writeBytes(0, snapshot)
                for (i in 0 until globalCount) {
                    if (inst.global(i).mutable) { inst.global(i).setI64(savedGlobals[i]) }
                }

                inst.memory().writeI32(4385300, 10) // la_damage

                // Create fake thing intercept
                inst.memory().writeI32(interceptAddr, 32768)   // frac
                inst.memory().writeI32(interceptAddr + 4, 0)   // isaline = false (thing)
                inst.memory().writeI32(interceptAddr + 8, 0x500000) // mobj ptr

                // Fake mobj at 0x500000: needs MF_SHOOTABLE flag (bit 22 = 0x400000) at offset...
                // mobj_t flags is at offset 100 in DOOM (approximately)
                // Actually the offset varies. Let's set bytes 0-200 to plausible values
                // z at offset 8, height at offset 56 (approximately)
                inst.memory().writeI32(0x500000 + 8, 0)       // z = 0
                inst.memory().writeI32(0x500000 + 56, 56 * 65536) // height = 56 units
                // flags: need MF_SHOOTABLE = 0x400000, at the right offset

                try {
                    val result = inst.callByIndex(ptrShootIdx, interceptAddr.toLong())
                    println("  $label: PTR_ShootTraverse(frac=32768, isaline=0) = ${result[0]}")
                } catch (e: Exception) {
                    println("  $label: ERROR: ${e.message?.take(80)}")
                }
            }
        }

        // Also directly test the call_indirect dispatch
        println("\n=== Testing call_indirect type 2 dispatch ===")
        // P_TraverseIntercepts uses call_indirect type=2 with table index as the callback
        // PTR_ShootTraverse is at table slot 140
        // Let's verify the dispatch works by checking the call_indirect dispatcher

        val dispatcherName = "__wark_call_indirect_type2"
        val dispatcherFunc = jitInstance.compiledIr()?.functions?.find { it.name == dispatcherName }
        if (dispatcherFunc != null) {
            println("  Dispatcher $dispatcherName exists: ${dispatcherFunc.blocks.size} blocks, ${dispatcherFunc.params.size} params")
            // Count how many table entries it dispatches to
            var entryCount = 0
            for (block in dispatcherFunc.blocks) {
                for (inst in block.instructions) {
                    if (inst is org.kgen.ir.instructions.Call && !inst.function.name.startsWith("__wark_")) {
                        entryCount++
                    }
                }
            }
            println("  Dispatches to $entryCount functions")

            // Check if PTR_ShootTraverse (table 140) is in the dispatcher
            var found = false
            for (block in dispatcherFunc.blocks) {
                for (inst in block.instructions) {
                    if (inst is org.kgen.ir.instructions.Call && inst.function.name == "PTR_ShootTraverse") {
                        found = true
                    }
                }
            }
            println("  PTR_ShootTraverse in dispatcher: $found")
        } else {
            println("  Dispatcher $dispatcherName NOT FOUND")
            // List available dispatchers
            val dispatchers = jitInstance.compiledIr()?.functions?.filter { it.name.startsWith("__wark_call_indirect") }
            dispatchers?.forEach { println("    ${it.name}") }
        }

        println("Done.")
    }
}
