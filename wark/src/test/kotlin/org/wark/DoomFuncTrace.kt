package org.wark

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    val wasmBytes = Files.readAllBytes(Path.of("examples/assets/doom.wasm"))
    val wadBytes = Files.readAllBytes(Path.of("examples/assets/doom1.wad"))

    // func_142 = P_DamageMobj
    // func_805 = P_AimLineAttack
    // We want to know if these are ever called during gameplay

    val damageCalls = AtomicInteger(0)
    val aimCalls = AtomicInteger(0)

    // Known function names from DWARF analysis
    val funcNames = mapOf(
        2 to "FixedMul", 35 to "P_PlayerThink", 46 to "P_MovePsprites",
        142 to "P_DamageMobj", 150 to "P_CheckPosition", 219 to "P_AproxDistance",
        304 to "G_BuildTiccmd", 312 to "A_WeaponReady", 341 to "P_GiveBody",
        474 to "P_CheckSight", 805 to "P_AimLineAttack", 118 to "P_BlockThingsIterator",
        782 to "P_TryMove", 44 to "P_LineOpening",
    )

    val startTime = System.currentTimeMillis()
    val instance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
        .load(wasmBytes).instantiate(WarkImports.builder()
            .function("loading", "onGameInit") { _, _ -> longArrayOf() }
            .function("loading", "wadSizes") { _, _ -> longArrayOf(wadBytes.size.toLong()) }
            .function("loading", "readWads") { inst, args ->
                inst.memory().writeBytes(args[0].toInt(), wadBytes)
                longArrayOf()
            }
            .function("runtimeControl", "timeInMilliseconds") { _, _ ->
                longArrayOf(System.currentTimeMillis() - startTime)
            }
            .function("ui", "drawFrame") { _, _ -> longArrayOf() }
            .function("gameSaving", "sizeOfSaveGame") { _, _ -> longArrayOf(0) }
            .function("gameSaving", "readSaveGame") { _, _ -> longArrayOf(0) }
            .function("gameSaving", "writeSaveGame") { _, _ -> longArrayOf(0) }
            .function("console", "onInfoMessage") { _, _ -> longArrayOf() }
            .function("console", "onErrorMessage") { _, _ -> longArrayOf() }
            .build())

    val callCounts = java.util.concurrent.ConcurrentHashMap<Int, AtomicInteger>()
    instance.enableTracing { funcIndex, _ ->
        callCounts.computeIfAbsent(funcIndex) { AtomicInteger(0) }.incrementAndGet()
        if (funcIndex == 142) { damageCalls.incrementAndGet() }
        if (funcIndex == 805) { aimCalls.incrementAndGet() }
    }

    println("initGame...")
    instance.call("initGame")
    println("initGame done. P_DamageMobj calls: ${damageCalls.get()}, P_AimLineAttack calls: ${aimCalls.get()}")

    fun pressKey(key: Long, ticks: Int = 5) {
        instance.call("reportKeyDown", key)
        for (tick in 1..ticks) { instance.call("tickGame") }
        instance.call("reportKeyUp", key)
        for (tick in 1..ticks) { instance.call("tickGame") }
    }

    fun tick(count: Int) {
        for (tick in 1..count) { instance.call("tickGame") }
    }

    fun status(label: String) {
        println("  [$label] P_PlayerThink=${callCounts[35]?.get() ?: 0}, P_MovePsprites=${callCounts[46]?.get() ?: 0}, P_DamageMobj=${damageCalls.get()}, P_AimLineAttack=${aimCalls.get()}")
    }

    // Navigate menus: any key → main menu, then New Game → Skill
    println("Navigating menus...")
    tick(35) // let title screen render
    status("title")
    pressKey(0x0DL, 10) // Enter → main menu
    tick(35)
    status("main menu")
    pressKey(0x0DL, 10) // Enter → New Game
    tick(35)
    status("new game")
    pressKey(0x0DL, 10) // Enter → skill select (or episode - shareware skips episode)
    tick(35)
    status("skill")
    pressKey(0x0DL, 10) // Enter → start game
    tick(100)
    status("gameplay?")

    // If P_PlayerThink still 0, try more Enter presses
    if ((callCounts[35]?.get() ?: 0) == 0) {
        println("  Not in gameplay yet, pressing Enter more...")
        pressKey(0x0DL, 10)
        tick(100)
        status("retry1")
        pressKey(0x0DL, 10)
        tick(100)
        status("retry2")
    }

    // Now fire
    println("Pressing fire and ticking...")
    instance.call("reportKeyDown", 0x9DL) // KEY_RCTRL (fire in default DOOM config)
    for (tick in 1..100) {
        instance.call("tickGame")
        if (tick % 25 == 0) { status("fire tick $tick") }
    }
    instance.call("reportKeyUp", 0x9DL)

    // Also try KEY_FIRE (0xA3) in case that's what doomgeneric uses
    instance.call("reportKeyDown", 0xA3L)
    for (tick in 1..50) { instance.call("tickGame") }
    instance.call("reportKeyUp", 0xA3L)
    status("after fire")
    println("Final: P_DamageMobj=${damageCalls.get()}, P_AimLineAttack=${aimCalls.get()}")
    println()
    println("Top 30 called functions:")
    callCounts.entries.sortedByDescending { it.value.get() }.take(30).forEach { (idx, count) ->
        val name = funcNames[idx] ?: ""
        println("  func_$idx: ${count.get()} calls  $name")
    }
    println()
    println("Total unique functions called: ${callCounts.size}")
    println()
    println("Damage path functions:")
    for (idx in listOf(142, 805, 118, 150, 474, 219, 782, 44, 46, 35, 304, 312, 341)) {
        val count = callCounts[idx]?.get() ?: 0
        val name = funcNames[idx] ?: ""
        println("  func_$idx ($name): $count calls")
    }
}
