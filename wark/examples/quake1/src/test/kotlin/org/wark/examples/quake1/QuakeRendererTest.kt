package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.wasi.WasiPreview1
import java.nio.file.Files
import java.nio.file.Path

/**
 * Compares JIT and interpreter framebuffer/palette output after N frames
 * to isolate whether graphical issues (darker colors, missing polygons)
 * are caused by JIT codegen or host-side rendering.
 *
 * Uses deterministic clock injection so timing differences don't cause false diffs.
 */
class QuakeRendererTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    private class ClockTrackingRunner(
        val runner: QuakeRunner,
        val clockCounter: LongArray,
    ) {
        fun clockCalls(): Long = clockCounter[0]
    }

    private class FrameCapture(
        val palette: ByteArray,
        val framebuffer: ByteArray,
        val width: Int,
        val height: Int,
    )

    private fun loadDeterministic(mode: ExecutionMode): ClockTrackingRunner {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val host = QuakeHost()
        val setjmpEmulation = SetjmpEmulation()
        val wasi = WasiPreview1.builder().directory(gameDirectory).build()
        val runtime = WarkRuntime.create(WasmTarget.V2_0, mode)
        val module = runtime.load(wasmBytes)

        val builder = WarkImports.builder()
        host.registerImports(builder)
        wasi.registerImports(builder)
        setjmpEmulation.registerImports(builder)

        // Deterministic clock so both engines see identical time
        val startTimeNanos = 1000000000000L
        val clockCounter = longArrayOf(0)
        builder.function("wasi_snapshot_preview1", "clock_time_get",
            org.wark.HostFunction { instance, args ->
                val resultAddress = args[2].toInt()
                val timeNanos = startTimeNanos + clockCounter[0] * 33333333L
                clockCounter[0]++
                instance.memory().writeI64(resultAddress, timeNanos)
                longArrayOf(0L)
            })

        val instance = module.instantiate(builder.build())
        return ClockTrackingRunner(QuakeRunner(instance, host, wasi), clockCounter)
    }

    private fun captureAfterFrames(mode: ExecutionMode, frameCount: Int): FrameCapture {
        val runner = loadDeterministic(mode).runner
        runner.initialize()

        val deltaTime = 1.0f / 30.0f
        for (frame in 1..frameCount) {
            runner.frame(deltaTime)
        }

        val memory = runner.memory()
        val fbPtr = runner.framebufferPointer()
        val fbWidth = runner.framebufferWidth()
        val fbHeight = runner.framebufferHeight()
        val palPtr = runner.palettePointer()

        val capture = FrameCapture(
            palette = memory.readBytes(palPtr, 768),
            framebuffer = memory.readBytes(fbPtr, fbWidth * fbHeight),
            width = fbWidth,
            height = fbHeight,
        )

        runner.shutdown()
        return capture
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun compareJitVsInterpreterFramebuffer() {
        val framesToRun = 30

        println("Running $framesToRun frames in INTERPRET mode (deterministic clock)...")
        val interpCapture = captureAfterFrames(ExecutionMode.INTERPRET, framesToRun)
        println("  Framebuffer: ${interpCapture.width}x${interpCapture.height}, ${interpCapture.framebuffer.size} bytes")

        println("Running $framesToRun frames in JIT mode (deterministic clock)...")
        val jitCapture = captureAfterFrames(ExecutionMode.JIT, framesToRun)
        println("  Framebuffer: ${jitCapture.width}x${jitCapture.height}, ${jitCapture.framebuffer.size} bytes")

        comparePalettes(interpCapture.palette, jitCapture.palette)
        compareFramebuffers(interpCapture, jitCapture)
        dumpPaletteEntries(interpCapture.palette, jitCapture.palette, 16)
    }

    /**
     * Runs both engines side-by-side frame-by-frame, comparing the framebuffer
     * after each frame to find the exact frame where divergence first appears.
     */
    @Test
    @EnabledIf("quakeWasmExists")
    fun findFirstDivergentFrame() {
        val maxFrames = 30

        println("Loading interpreter (deterministic clock)...")
        val interpTracking = loadDeterministic(ExecutionMode.INTERPRET)
        val interpRunner = interpTracking.runner
        interpRunner.initialize()
        println("  Clock calls after init: ${interpTracking.clockCalls()}")

        println("Loading JIT (deterministic clock)...")
        val jitTracking = loadDeterministic(ExecutionMode.JIT)
        val jitRunner = jitTracking.runner
        jitRunner.initialize()
        println("  Clock calls after init: ${jitTracking.clockCalls()}")

        // Compare memory right after init (before any frames)
        val interpInitMem = interpRunner.memory().readBytes(0, interpRunner.memory().sizeBytes())
        val jitInitMem = jitRunner.memory().readBytes(0, jitRunner.memory().sizeBytes())
        val initDiffs = countMemoryDiffs(interpInitMem, jitInitMem)
        println("Memory diffs after init (before any frames): $initDiffs i32 words differ")

        val deltaTime = 1.0f / 30.0f
        for (frame in 1..maxFrames) {
            interpRunner.frame(deltaTime)
            jitRunner.frame(deltaTime)
            println("  Clock calls: interp=${interpTracking.clockCalls()} jit=${jitTracking.clockCalls()}")

            val interpMem = interpRunner.memory()
            val jitMem = jitRunner.memory()

            val interpFbPtr = interpRunner.framebufferPointer()
            val jitFbPtr = jitRunner.framebufferPointer()
            val width = interpRunner.framebufferWidth()
            val height = interpRunner.framebufferHeight()

            if (interpFbPtr != jitFbPtr) {
                println("Frame $frame: framebuffer pointer diverged! interp=0x${Integer.toHexString(interpFbPtr)} jit=0x${Integer.toHexString(jitFbPtr)}")
            }

            val interpFb = interpMem.readBytes(interpFbPtr, width * height)
            val jitFb = jitMem.readBytes(jitFbPtr, width * height)

            var pixelDiffs = 0
            for (i in interpFb.indices) {
                if (interpFb[i] != jitFb[i]) {
                    pixelDiffs++
                }
            }

            if (pixelDiffs > 0) {
                println("Frame $frame: $pixelDiffs pixel diffs (${String.format("%.2f", pixelDiffs * 100.0 / interpFb.size)}%)")

                // Show bounding box of diffs
                var minRow = Int.MAX_VALUE
                var maxRow = 0
                var minCol = Int.MAX_VALUE
                var maxCol = 0
                for (i in interpFb.indices) {
                    if (interpFb[i] != jitFb[i]) {
                        val row = i / width
                        val col = i % width
                        if (row < minRow) { minRow = row }
                        if (row > maxRow) { maxRow = row }
                        if (col < minCol) { minCol = col }
                        if (col > maxCol) { maxCol = col }
                    }
                }
                println("  Region: rows[$minRow..$maxRow] cols[$minCol..$maxCol]")
            } else {
                println("Frame $frame: IDENTICAL")
            }
        }

        interpRunner.shutdown()
        jitRunner.shutdown()
    }

    private fun countMemoryDiffs(interpSnap: ByteArray, jitSnap: ByteArray): Int {
        val minSize = minOf(interpSnap.size, jitSnap.size)
        var diffs = 0
        for (address in 0 until minSize step 4) {
            val interpVal = readI32(interpSnap, address)
            val jitVal = readI32(jitSnap, address)
            if (interpVal != jitVal) {
                diffs++
            }
        }
        return diffs
    }

    private fun readI32(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) { return 0 }
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun comparePalettes(interpPalette: ByteArray, jitPalette: ByteArray) {
        println("\n=== Palette Comparison ===")
        var paletteDiffs = 0
        for (i in 0 until 256) {
            val interpR = interpPalette[i * 3].toInt() and 0xFF
            val interpG = interpPalette[i * 3 + 1].toInt() and 0xFF
            val interpB = interpPalette[i * 3 + 2].toInt() and 0xFF
            val jitR = jitPalette[i * 3].toInt() and 0xFF
            val jitG = jitPalette[i * 3 + 1].toInt() and 0xFF
            val jitB = jitPalette[i * 3 + 2].toInt() and 0xFF
            if (interpR != jitR || interpG != jitG || interpB != jitB) {
                if (paletteDiffs < 20) {
                    println("  Palette[$i]: interp=($interpR,$interpG,$interpB) jit=($jitR,$jitG,$jitB)")
                }
                paletteDiffs++
            }
        }
        if (paletteDiffs == 0) {
            println("  Palettes IDENTICAL")
        } else {
            println("  $paletteDiffs / 256 palette entries differ")
        }
    }

    private fun compareFramebuffers(interpCapture: FrameCapture, jitCapture: FrameCapture) {
        println("\n=== Framebuffer Comparison ===")
        val minSize = minOf(interpCapture.framebuffer.size, jitCapture.framebuffer.size)
        var pixelDiffs = 0
        var firstDiffIndex = -1
        val diffsByRow = mutableMapOf<Int, Int>()

        for (i in 0 until minSize) {
            if (interpCapture.framebuffer[i] != jitCapture.framebuffer[i]) {
                if (firstDiffIndex == -1) {
                    firstDiffIndex = i
                }
                pixelDiffs++
                val row = i / interpCapture.width
                diffsByRow[row] = (diffsByRow[row] ?: 0) + 1
            }
        }

        if (pixelDiffs == 0) {
            println("  Framebuffers IDENTICAL")
        } else {
            val diffPercent = (pixelDiffs * 100.0) / minSize
            println("  $pixelDiffs / $minSize pixels differ (${String.format("%.2f", diffPercent)}%)")
            println("  First diff at pixel $firstDiffIndex (row ${firstDiffIndex / interpCapture.width}, col ${firstDiffIndex % interpCapture.width})")

            println("  Top rows with most diffs:")
            diffsByRow.entries.sortedByDescending { it.value }.take(10).forEach { (row, count) ->
                println("    Row $row: $count / ${interpCapture.width} pixels differ")
            }

            println("  First 10 differing pixels:")
            var shown = 0
            for (i in 0 until minSize) {
                if (interpCapture.framebuffer[i] != jitCapture.framebuffer[i]) {
                    val interpIdx = interpCapture.framebuffer[i].toInt() and 0xFF
                    val jitIdx = jitCapture.framebuffer[i].toInt() and 0xFF
                    val row = i / interpCapture.width
                    val col = i % interpCapture.width
                    println("    [$row,$col] interp=palette[$interpIdx] jit=palette[$jitIdx]")
                    shown++
                    if (shown >= 10) {
                        break
                    }
                }
            }
        }
    }

    private fun dumpPaletteEntries(interpPalette: ByteArray, jitPalette: ByteArray, count: Int) {
        println("\n=== First $count Palette Entries ===")
        for (i in 0 until count) {
            val interpR = interpPalette[i * 3].toInt() and 0xFF
            val interpG = interpPalette[i * 3 + 1].toInt() and 0xFF
            val interpB = interpPalette[i * 3 + 2].toInt() and 0xFF
            val jitR = jitPalette[i * 3].toInt() and 0xFF
            val jitG = jitPalette[i * 3 + 1].toInt() and 0xFF
            val jitB = jitPalette[i * 3 + 2].toInt() and 0xFF
            val marker = if (interpR != jitR || interpG != jitG || interpB != jitB) " <-- DIFF" else ""
            println("  [$i] interp=($interpR,$interpG,$interpB) jit=($jitR,$jitG,$jitB)$marker")
        }
    }

    /**
     * Runs both engines to frame 5 (first divergent frame), then does a full
     * memory comparison to see if the divergence is just framebuffer pixels
     * or if deeper state is corrupted.
     */
    @Test
    @EnabledIf("quakeWasmExists")
    fun analyzeFrame5Divergence() {
        println("Loading both engines with deterministic clocks...")
        val interpRunner = loadDeterministic(ExecutionMode.INTERPRET).runner
        interpRunner.initialize()
        val jitRunner = loadDeterministic(ExecutionMode.JIT).runner
        jitRunner.initialize()

        val deltaTime = 1.0f / 30.0f

        // Run to frame 4 (still identical)
        for (frame in 1..4) {
            interpRunner.frame(deltaTime)
            jitRunner.frame(deltaTime)
        }

        // Snapshot memory at end of frame 4
        val interpMem4 = interpRunner.memory().readBytes(0, interpRunner.memory().sizeBytes())
        val jitMem4 = jitRunner.memory().readBytes(0, jitRunner.memory().sizeBytes())
        val frame4Diffs = countMemoryDiffs(interpMem4, jitMem4)
        println("After frame 4: $frame4Diffs i32 word diffs (should be 0)")

        // Run frame 5
        interpRunner.frame(deltaTime)
        jitRunner.frame(deltaTime)

        val interpMem5 = interpRunner.memory().readBytes(0, interpRunner.memory().sizeBytes())
        val jitMem5 = jitRunner.memory().readBytes(0, jitRunner.memory().sizeBytes())

        // Full memory diff
        val minSize = minOf(interpMem5.size, jitMem5.size)
        var totalByteDiffs = 0
        var totalWordDiffs = 0
        val diffRegions = mutableListOf<Pair<Int, Int>>() // start, length
        var regionStart = -1

        for (i in 0 until minSize) {
            if (interpMem5[i] != jitMem5[i]) {
                totalByteDiffs++
                if (regionStart == -1) {
                    regionStart = i
                }
            } else {
                if (regionStart != -1) {
                    diffRegions.add(regionStart to (i - regionStart))
                    regionStart = -1
                }
            }
        }
        if (regionStart != -1) {
            diffRegions.add(regionStart to (minSize - regionStart))
        }

        for (address in 0 until minSize step 4) {
            val interpVal = readI32(interpMem5, address)
            val jitVal = readI32(jitMem5, address)
            if (interpVal != jitVal) {
                totalWordDiffs++
            }
        }

        println("\nAfter frame 5:")
        println("  Total byte diffs: $totalByteDiffs")
        println("  Total i32 word diffs: $totalWordDiffs")
        println("  Contiguous diff regions: ${diffRegions.size}")

        // Get framebuffer location
        val fbPtr = interpRunner.framebufferPointer()
        val fbWidth = interpRunner.framebufferWidth()
        val fbHeight = interpRunner.framebufferHeight()
        val fbEnd = fbPtr + fbWidth * fbHeight
        println("  Framebuffer: 0x${Integer.toHexString(fbPtr)}..0x${Integer.toHexString(fbEnd)} (${fbWidth}x${fbHeight})")

        // Categorize diff regions
        var fbDiffBytes = 0
        var nonFbDiffBytes = 0
        val nonFbRegions = mutableListOf<Triple<Int, Int, String>>() // addr, size, context

        val wasmModule = interpRunner.instance().module.wasmModule
        val importCount = wasmModule.importedFunctionCount

        for ((start, length) in diffRegions) {
            if (start >= fbPtr && start < fbEnd) {
                fbDiffBytes += length
            } else {
                nonFbDiffBytes += length
                // Read a few bytes from each side for context
                val interpVal = if (start + 4 <= minSize) { readI32(interpMem5, start) } else { 0 }
                val jitVal = if (start + 4 <= minSize) { readI32(jitMem5, start) } else { 0 }
                val context = "interp=0x${Integer.toHexString(interpVal)} jit=0x${Integer.toHexString(jitVal)}"
                nonFbRegions.add(Triple(start, length, context))
            }
        }

        println("\n  Framebuffer diff bytes: $fbDiffBytes")
        println("  Non-framebuffer diff bytes: $nonFbDiffBytes")

        if (nonFbRegions.isNotEmpty()) {
            println("\n  Non-framebuffer diff regions (first 20):")
            for ((address, length, context) in nonFbRegions.take(20)) {
                println("    0x${Integer.toHexString(address)} ($length bytes) $context")
            }
        } else {
            println("\n  ALL diffs are in the framebuffer — no state corruption")
        }

        // Also check stack pointer (global 0) and key runtime globals
        val interpSp = interpRunner.instance().global(0).getI32()
        val jitSp = jitRunner.instance().global(0).getI32()
        println("\n  Stack pointer: interp=0x${Integer.toHexString(interpSp)} jit=0x${Integer.toHexString(jitSp)}")

        interpRunner.shutdown()
        jitRunner.shutdown()
    }

    /**
     * Traces memory divergence frame-by-frame from frame 0, showing
     * the first diff addresses and how they evolve.
     */
    @Test
    @EnabledIf("quakeWasmExists")
    fun traceMemoryDivergenceFromStart() {
        println("Loading both engines with deterministic clocks...")
        val interpRunner = loadDeterministic(ExecutionMode.INTERPRET).runner
        interpRunner.initialize()
        val jitRunner = loadDeterministic(ExecutionMode.JIT).runner
        jitRunner.initialize()

        // Check right after init
        var interpSnap = interpRunner.memory().readBytes(0, interpRunner.memory().sizeBytes())
        var jitSnap = jitRunner.memory().readBytes(0, jitRunner.memory().sizeBytes())
        printMemoryDivergence("After init", interpSnap, jitSnap)

        val deltaTime = 1.0f / 30.0f
        for (frame in 1..10) {
            interpRunner.frame(deltaTime)
            jitRunner.frame(deltaTime)

            interpSnap = interpRunner.memory().readBytes(0, interpRunner.memory().sizeBytes())
            jitSnap = jitRunner.memory().readBytes(0, jitRunner.memory().sizeBytes())
            printMemoryDivergence("After frame $frame", interpSnap, jitSnap)
        }

        interpRunner.shutdown()
        jitRunner.shutdown()
    }

    private fun printMemoryDivergence(label: String, interpSnap: ByteArray, jitSnap: ByteArray) {
        val minSize = minOf(interpSnap.size, jitSnap.size)
        var wordDiffs = 0
        var firstDiffAddr = -1
        val diffAddresses = mutableListOf<Int>()

        for (address in 0 until minSize step 4) {
            val interpVal = readI32(interpSnap, address)
            val jitVal = readI32(jitSnap, address)
            if (interpVal != jitVal) {
                wordDiffs++
                if (firstDiffAddr == -1) {
                    firstDiffAddr = address
                }
                if (diffAddresses.size < 10) {
                    diffAddresses.add(address)
                }
            }
        }

        if (wordDiffs == 0) {
            println("$label: 0 diffs")
        } else {
            println("$label: $wordDiffs i32 diffs, first at 0x${Integer.toHexString(firstDiffAddr)}")
            for (address in diffAddresses) {
                val interpVal = readI32(interpSnap, address)
                val jitVal = readI32(jitSnap, address)
                // Try interpreting as float too
                val interpFloat = java.lang.Float.intBitsToFloat(interpVal)
                val jitFloat = java.lang.Float.intBitsToFloat(jitVal)
                val floatInfo = if (interpFloat.isFinite() && jitFloat.isFinite()) {
                    " (float: interp=$interpFloat jit=$jitFloat)"
                } else {
                    ""
                }
                println("    0x${Integer.toHexString(address)}: interp=0x${Integer.toHexString(interpVal)} jit=0x${Integer.toHexString(jitVal)}$floatInfo")
            }
        }
    }

    /**
     * Uses the DifferentialRunner to test individual functions that might
     * produce the -0.0 vs +0.0 divergence seen at address 0x1dfd4.
     *
     * First, lists all functions that write to the divergent addresses.
     */
    @Test
    @EnabledIf("quakeWasmExists")
    fun identifyDivergentFunction() {
        println("Loading interpreter with function tracing...")
        val interpRunner = loadDeterministic(ExecutionMode.INTERPRET).runner
        interpRunner.initialize()

        val instance = interpRunner.instance()
        val wasmModule = instance.module.wasmModule
        val importCount = wasmModule.importedFunctionCount

        // Watch addresses that still diverge after FNeg fix
        val watchAddresses = intArrayOf(0x4a2cc, 0x4a2d0, 0x4c310, 0x4c314)
        val initialValues = IntArray(watchAddresses.size)
        val memory = interpRunner.memory()
        for (i in watchAddresses.indices) {
            initialValues[i] = memory.readI32(watchAddresses[i])
        }
        println("Initial values at watch addresses:")
        for (i in watchAddresses.indices) {
            val floatVal = java.lang.Float.intBitsToFloat(initialValues[i])
            println("  0x${Integer.toHexString(watchAddresses[i])}: 0x${Integer.toHexString(initialValues[i])} (float=$floatVal)")
        }

        // Run frame 1 with function entry tracing to find which function writes -0.0
        var lastFuncName = "unknown"
        var lastFuncIndex = -1
        instance.setFunctionEntryCallback { funcIndex, _ ->
            val name = wasmModule.functionName(funcIndex)
            if (name != null) {
                lastFuncName = name
                lastFuncIndex = funcIndex
            }
            // Check watch addresses for changes
            for (i in watchAddresses.indices) {
                val currentVal = memory.readI32(watchAddresses[i])
                if (currentVal != initialValues[i]) {
                    val floatVal = java.lang.Float.intBitsToFloat(currentVal)
                    val prevFloat = java.lang.Float.intBitsToFloat(initialValues[i])
                    println("  WRITE at 0x${Integer.toHexString(watchAddresses[i])}: " +
                        "0x${Integer.toHexString(initialValues[i])} ($prevFloat) → " +
                        "0x${Integer.toHexString(currentVal)} ($floatVal) " +
                        "during entry to $lastFuncName (func $lastFuncIndex)")
                    initialValues[i] = currentVal
                }
            }
        }

        println("\nRunning frame 1 with write tracing on watch addresses...")
        interpRunner.frame(1.0f / 30.0f)

        // Also check what function names are at certain key locations
        println("\nRelevant function names:")
        val interestingNames = listOf(
            "CL_RelinkEntities", "CL_UpdateTEnts",
            "R_AliasDrawModel", "D_PolysetDraw", "D_PolysetDrawSpans8",
            "R_SetupFrame", "V_CalcRefdef", "R_RenderView",
            "AngleVectors", "VectorMA", "SV_Physics",
        )
        for (name in interestingNames) {
            for ((localIndex, _) in wasmModule.functions.withIndex()) {
                val funcName = wasmModule.functionName(localIndex + importCount)
                if (funcName == name) {
                    println("  func[${localIndex + importCount}] = $name")
                }
            }
        }

        interpRunner.shutdown()
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun categorizeFrame1Diffs() {
        val interpRunner = loadDeterministic(ExecutionMode.INTERPRET).runner
        interpRunner.initialize()
        val jitRunner = loadDeterministic(ExecutionMode.JIT).runner
        jitRunner.initialize()

        interpRunner.frame(1.0f / 30.0f)
        jitRunner.frame(1.0f / 30.0f)

        val interpSnap = interpRunner.memory().readBytes(0, interpRunner.memory().sizeBytes())
        val jitSnap = jitRunner.memory().readBytes(0, jitRunner.memory().sizeBytes())
        val minSize = minOf(interpSnap.size, jitSnap.size)

        var jitZeroInterpNonZero = 0
        var interpZeroJitNonZero = 0
        var bothNonZeroDifferent = 0
        val jitZeroExamples = mutableListOf<Pair<Int, Int>>() // address, interpValue

        for (address in 0 until minSize step 4) {
            val interpVal = readI32(interpSnap, address)
            val jitVal = readI32(jitSnap, address)
            if (interpVal == jitVal) { continue }
            if (jitVal == 0 && interpVal != 0) {
                jitZeroInterpNonZero++
                if (jitZeroExamples.size < 15) {
                    jitZeroExamples.add(address to interpVal)
                }
            } else if (interpVal == 0 && jitVal != 0) {
                interpZeroJitNonZero++
            } else {
                bothNonZeroDifferent++
            }
        }

        println("=== Frame 1 diff categories ===")
        println("  JIT=0, Interp!=0: $jitZeroInterpNonZero")
        println("  Interp=0, JIT!=0: $interpZeroJitNonZero")
        println("  Both non-zero, different: $bothNonZeroDifferent")
        println("  Total: ${jitZeroInterpNonZero + interpZeroJitNonZero + bothNonZeroDifferent}")

        if (jitZeroExamples.isNotEmpty()) {
            println("\n  JIT=0 examples:")
            for ((address, interpVal) in jitZeroExamples) {
                println("    0x${Integer.toHexString(address)}: interp=0x${Integer.toHexString(interpVal)}")
            }
        }

        interpRunner.shutdown()
        jitRunner.shutdown()
    }

    /**
     * Disassembles the key rasterizer functions to find which WASM opcodes
     * they use — helps identify which JIT codegen paths could diverge.
     */
    @Test
    @EnabledIf("quakeWasmExists")
    fun disassembleRasterizer() {
        val wasmBytes = java.nio.file.Files.readAllBytes(wasmPath)
        val wasmModule = org.kgen.target.wasm.module.WasmModuleReader.read(wasmBytes)
        val importCount = wasmModule.importedFunctionCount
        val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()

        // Key functions from our investigation
        val targetFuncs = mapOf(
            "R_AliasTransformFinalVert" to -1,
            "R_AliasProjectFinalVert" to -1,
            "D_RasterizeAliasPolySmooth" to 236,
            "D_PolysetScanLeftEdge" to 239,
            "D_PolysetDrawSpans8" to 240,
            "R_AliasDrawModel" to 557,
        )

        for ((name, knownIndex) in targetFuncs) {
            var funcIndex = knownIndex
            if (funcIndex < 0) {
                for ((localIndex, _) in wasmModule.functions.withIndex()) {
                    val funcName = wasmModule.functionName(localIndex + importCount)
                    if (funcName == name) {
                        funcIndex = localIndex + importCount
                        break
                    }
                }
            }
            if (funcIndex < 0) {
                println("$name: NOT FOUND")
                continue
            }

            val localIndex = funcIndex - importCount
            if (localIndex < 0 || localIndex >= wasmModule.functions.size) {
                println("$name (func $funcIndex): out of range")
                continue
            }

            val function = wasmModule.functions[localIndex]
            val instructions = disasm.disassemble(function)
            val opcodeCounts = mutableMapOf<String, Int>()
            for (instruction in instructions) {
                val opName = instruction.opcode.name
                opcodeCounts[opName] = (opcodeCounts[opName] ?: 0) + 1
            }

            println("$name (func $funcIndex): ${instructions.size} instructions")
            // Show float and interesting opcodes
            val interesting = opcodeCounts.filter { (op, _) ->
                op.startsWith("F32_") || op.startsWith("F64_") ||
                op.contains("CONVERT") || op.contains("TRUNC") ||
                op.contains("EXTEND") || op.contains("WRAP") ||
                op.contains("REINTERPRET") || op == "CALL_INDIRECT" ||
                op == "CALL" || op == "SELECT"
            }.entries.sortedByDescending { it.value }

            if (interesting.isNotEmpty()) {
                for ((op, count) in interesting) {
                    println("  $op: $count")
                }
            } else {
                println("  (no float/conversion ops)")
                // Show top opcodes instead
                for ((op, count) in opcodeCounts.entries.sortedByDescending { it.value }.take(10)) {
                    println("  $op: $count")
                }
            }
            println()
        }
    }

    /**
     * Runs both engines through frame 1, then dumps memory around the first
     * diff address to understand the data layout and nature of the divergence.
     */
    @Test
    @EnabledIf("quakeWasmExists")
    fun isolateVertexTransformDivergence() {
        val interpRunner = loadDeterministic(ExecutionMode.INTERPRET).runner
        interpRunner.initialize()
        val jitRunner = loadDeterministic(ExecutionMode.JIT).runner
        jitRunner.initialize()

        interpRunner.frame(1.0f / 30.0f)
        jitRunner.frame(1.0f / 30.0f)

        val interpSnap = interpRunner.memory().readBytes(0, interpRunner.memory().sizeBytes())
        val jitSnap = jitRunner.memory().readBytes(0, jitRunner.memory().sizeBytes())
        val minSize = minOf(interpSnap.size, jitSnap.size)

        var firstDiffAddr = -1
        var totalDiffs = 0
        for (address in 0 until minSize step 4) {
            if (readI32(interpSnap, address) != readI32(jitSnap, address)) {
                if (firstDiffAddr == -1) { firstDiffAddr = address }
                totalDiffs++
            }
        }
        println("After frame 1: $totalDiffs diffs, first at 0x${Integer.toHexString(firstDiffAddr)}")

        // Dump memory around first diff
        if (firstDiffAddr >= 0) {
            val start = maxOf(0, firstDiffAddr - 16)
            val end = minOf(minSize, firstDiffAddr + 128)
            println("\nMemory around first diff:")
            for (address in start until end step 4) {
                val interpVal = readI32(interpSnap, address)
                val jitVal = readI32(jitSnap, address)
                val marker = if (interpVal != jitVal) " <-- DIFF" else ""
                println("  0x${Integer.toHexString(address)}: interp=0x${Integer.toHexString(interpVal)} jit=0x${Integer.toHexString(jitVal)}$marker")
            }
        }

        // FNeg fix verification
        for (addr in intArrayOf(0x1dfd4, 0x21264)) {
            val interpVal = readI32(interpSnap, addr)
            val jitVal = readI32(jitSnap, addr)
            println("-0.0 check 0x${Integer.toHexString(addr)}: ${if (interpVal == jitVal) "FIXED" else "STILL BROKEN"}")
        }

        interpRunner.shutdown()
        jitRunner.shutdown()
    }

    /**
     * Traces R_AliasTransformFinalVert calls in the interpreter to capture
     * the vertex output values, then compares against JIT.
     */
    @Test
    @EnabledIf("quakeWasmExists")
    fun traceVertexTransformOutputs() {
        // Run interpreter with function tracing on R_AliasTransformFinalVert
        val interpRunner = loadDeterministic(ExecutionMode.INTERPRET).runner
        interpRunner.initialize()

        val instance = interpRunner.instance()
        val wasmModule = instance.module.wasmModule
        val importCount = wasmModule.importedFunctionCount

        val transformFuncIndex = (0 until wasmModule.functions.size).firstOrNull { localIndex ->
            wasmModule.functionName(localIndex + importCount) == "R_AliasTransformFinalVert"
        }?.let { it + importCount } ?: -1

        val rasterizeFuncIndex = (0 until wasmModule.functions.size).firstOrNull { localIndex ->
            wasmModule.functionName(localIndex + importCount) == "D_RasterizeAliasPolySmooth"
        }?.let { it + importCount } ?: -1

        println("R_AliasTransformFinalVert: func $transformFuncIndex")
        println("D_RasterizeAliasPolySmooth: func $rasterizeFuncIndex")

        val memory = interpRunner.memory()
        // Track calls to R_AliasTransformFinalVert and their output addresses
        // In Quake: R_AliasTransformFinalVert(finalvert_t *fv, stvert_t *pstverts, trivertx_t *pverts)
        // The first arg is the output finalvert_t struct
        val vertexOutputs = mutableListOf<Pair<Int, IntArray>>()
        var rasterizeCallCount = 0

        instance.setFunctionEntryCallback { funcIndex, args ->
            if (funcIndex == transformFuncIndex && args.isNotEmpty()) {
                val outputAddr = args[0].toInt()
                // Read the output after return won't work (this is entry callback)
                // Instead, record address and read after frame
            }
            if (funcIndex == rasterizeFuncIndex) {
                rasterizeCallCount++
            }
        }

        interpRunner.frame(1.0f / 30.0f)
        println("D_RasterizeAliasPolySmooth called $rasterizeCallCount times in frame 1")

        // Now run JIT and compare
        val jitRunner = loadDeterministic(ExecutionMode.JIT).runner
        jitRunner.initialize()
        jitRunner.frame(1.0f / 30.0f)

        // Compare the scan-line data that feeds D_RasterizeAliasPolySmooth
        // Addresses 0x4a2cc and 0x4a2d0 are span edge Y coordinates
        val interpMem = interpRunner.memory()
        val jitMem = jitRunner.memory()

        // Scan the region 0x4a200-0x4a400 for the polygon data structures
        println("\n=== Rasterizer input data (0x4a280-0x4a340) ===")
        for (addr in 0x4a280 until 0x4a340 step 4) {
            val interpVal = interpMem.readI32(addr)
            val jitVal = jitMem.readI32(addr)
            val marker = if (interpVal != jitVal) " <-- DIFF" else ""
            if (interpVal != jitVal || (addr in 0x4a2c0..0x4a2e0)) {
                println("  0x${Integer.toHexString(addr)}: interp=$interpVal jit=$jitVal$marker")
            }
        }

        // Also scan the vertex output area — look for float diffs
        // that feed into the rasterizer
        println("\n=== Scanning 0x4c200-0x4c400 for vertex float diffs ===")
        var vertexDiffs = 0
        for (addr in 0x4c200 until 0x4c400 step 4) {
            val interpVal = interpMem.readI32(addr)
            val jitVal = jitMem.readI32(addr)
            if (interpVal != jitVal) {
                vertexDiffs++
                val interpFloat = java.lang.Float.intBitsToFloat(interpVal)
                val jitFloat = java.lang.Float.intBitsToFloat(jitVal)
                if (vertexDiffs <= 20) {
                    println("  0x${Integer.toHexString(addr)}: interp=0x${Integer.toHexString(interpVal)}($interpFloat) jit=0x${Integer.toHexString(jitVal)}($jitFloat)")
                }
            }
        }
        println("  Total diffs in range: $vertexDiffs")

        interpRunner.shutdown()
        jitRunner.shutdown()
    }

    /**
     * Uses function entry callbacks to capture the aliastransform matrix
     * (and related state) right before R_AliasTransformFinalVert runs,
     * then compares JIT vs interpreter.
     */
    @Test
    @EnabledIf("quakeWasmExists")
    fun traceTransformMatrix() {
        val interpRunner = loadDeterministic(ExecutionMode.INTERPRET).runner
        interpRunner.initialize()

        val instance = interpRunner.instance()
        val wasmModule = instance.module.wasmModule
        val importCount = wasmModule.importedFunctionCount

        // Find the functions
        val transformIndex = findFunc(wasmModule, importCount, "R_AliasTransformFinalVert")
        val drawModelIndex = findFunc(wasmModule, importCount, "R_AliasDrawModel")
        val setupTransIndex = findFunc(wasmModule, importCount, "R_AliasSetUpTransform")
        println("R_AliasTransformFinalVert: $transformIndex")
        println("R_AliasDrawModel: $drawModelIndex")
        println("R_AliasSetUpTransform: $setupTransIndex")

        val memory = interpRunner.memory()

        // Track first call to R_AliasTransformFinalVert — capture args
        var firstTransformArgs: LongArray? = null
        var transformCallCount = 0
        var watchAddr = 0x1fdad54
        var watchPrev = memory.readI32(watchAddr)

        val projectFuncIndex = findFunc(wasmModule, importCount, "R_AliasProjectFinalVert")
        var projectCallCount = 0

        instance.setFunctionEntryCallback { funcIndex, args ->
            if (funcIndex == transformIndex) {
                transformCallCount++
                if (firstTransformArgs == null) {
                    firstTransformArgs = args.clone()
                }
            }
            if (funcIndex == projectFuncIndex) {
                projectCallCount++
            }
        }

        // Check 0x1fdad54 before frame
        val preFrameInterp = memory.readI32(0x1fdad54)
        println("0x1fdad54 BEFORE frame (interp): 0x${Integer.toHexString(preFrameInterp)} (${java.lang.Float.intBitsToFloat(preFrameInterp)})")

        interpRunner.frame(1.0f / 30.0f)

        val postFrameInterp = memory.readI32(0x1fdad54)
        println("0x1fdad54 AFTER frame (interp): 0x${Integer.toHexString(postFrameInterp)} (${java.lang.Float.intBitsToFloat(postFrameInterp)})")
        println("R_AliasTransformFinalVert called $transformCallCount times")
        println("R_AliasProjectFinalVert called $projectCallCount times")

        if (firstTransformArgs != null) {
            val args = firstTransformArgs!!
            println("First call args: ${args.map { "0x${Integer.toHexString(it.toInt())}" }}")
            // Args: fv (output), pstverts, pverts
            // The aliastransform matrix is a global: 3 rows × 4 cols of floats = 48 bytes
            // Find it by searching for known function R_AliasSetUpTransform which writes it
        }

        // Now run JIT and count R_AliasProjectFinalVert calls via a wrapper
        val jitRunner = loadDeterministic(ExecutionMode.JIT).runner
        jitRunner.initialize()

        // We can't hook individual WASM function calls in JIT mode.
        // But we can compare the outputs.
        jitRunner.frame(1.0f / 30.0f)

        val jitPostFrame = jitRunner.memory().readI32(0x1fdad54)
        println("0x1fdad54 AFTER frame (jit): 0x${Integer.toHexString(jitPostFrame)} (${java.lang.Float.intBitsToFloat(jitPostFrame)})")

        val interpMem = interpRunner.memory()
        val jitMem = jitRunner.memory()

        // Search for the first float-valued diff in a wide range (outside framebuffer)
        println("\n=== First 30 float-valued diffs (plausible range) ===")
        val fbStart = interpRunner.framebufferPointer()
        val fbEnd = fbStart + 320 * 200
        var shown = 0
        for (addr in 0 until minOf(interpMem.sizeBytes(), jitMem.sizeBytes()) step 4) {
            if (addr in fbStart until fbEnd) { continue }
            val interpVal = interpMem.readI32(addr)
            val jitVal = jitMem.readI32(addr)
            if (interpVal == jitVal) { continue }
            val interpFloat = java.lang.Float.intBitsToFloat(interpVal)
            val jitFloat = java.lang.Float.intBitsToFloat(jitVal)
            // Only show values that look like plausible floats (not tiny denorms)
            if (interpFloat.isFinite() && jitFloat.isFinite() &&
                kotlin.math.abs(interpFloat) > 0.01f && kotlin.math.abs(interpFloat) < 1e6f) {
                println("  0x${Integer.toHexString(addr)}: interp=0x${Integer.toHexString(interpVal)}($interpFloat) jit=0x${Integer.toHexString(jitVal)}($jitFloat)")
                shown++
                if (shown >= 30) { break }
            }
        }

        interpRunner.shutdown()
        jitRunner.shutdown()
    }

    private fun findFunc(wasmModule: org.kgen.target.wasm.module.WasmModule, importCount: Int, name: String): Int {
        for ((localIndex, _) in wasmModule.functions.withIndex()) {
            if (wasmModule.functionName(localIndex + importCount) == name) {
                return localIndex + importCount
            }
        }
        return -1
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = QuakeRendererTest()
            if (!Files.exists(test.wasmPath)) {
                println("quake.wasm not found at ${test.wasmPath}")
                return
            }
            test.findFirstDivergentFrame()
        }
    }
}
