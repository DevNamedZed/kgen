plugins {
    application
}

dependencies {
    implementation(project(":wark"))
}

application {
    mainClass.set("org.wark.examples.doom.DoomSdlRunner")
}

tasks.named<JavaExec>("run") {
    args = listOf("../assets/doom.wasm", "../assets/doom1.wad")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("runDebug") {
    mainClass.set("org.wark.examples.doom.DoomDebugRunner")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx2g")
    workingDir = projectDir
    standardInput = System.`in`
    args = (project.findProperty("debugArgs") as String?)?.split(" ") ?: listOf("../assets/doom.wasm")
}

tasks.register<JavaExec>("findCrash") {
    mainClass.set("org.wark.examples.doom.DoomCrashFinder")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx2g")
    workingDir = projectDir
    args = (project.findProperty("funcArgs") as String?)?.split(" ") ?: listOf("scan")
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("wark-doom")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Main-Class" to "org.wark.examples.doom.DoomSdlRunner")
    }

    from(sourceSets["main"].output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })

    val outDir = layout.buildDirectory.dir("bin")
    destinationDirectory.set(outDir)

    doLast {
        val dir = outDir.get().asFile
        val jarName = archiveFile.get().asFile.name
        val assetsDir = projectDir.resolve("../assets")

        val wasmFile = assetsDir.resolve("doom.wasm")
        val wadFile = assetsDir.resolve("doom1.wad")
        val sdlFile = assetsDir.resolve("SDL2.dll")
        if (wasmFile.exists()) {
            wasmFile.copyTo(dir.resolve("doom.wasm"), overwrite = true)
        }
        if (wadFile.exists()) {
            wadFile.copyTo(dir.resolve("doom1.wad"), overwrite = true)
        }
        if (sdlFile.exists()) {
            sdlFile.copyTo(dir.resolve("SDL2.dll"), overwrite = true)
        }

        dir.resolve("doom.bat").writeText(
            "@echo off\r\n" +
            "cd /d \"%~dp0\"\r\n" +
            "if \"%~1\"==\"\" (\r\n" +
            "    set WASM=doom.wasm\r\n" +
            ") else (\r\n" +
            "    set WASM=%~1\r\n" +
            ")\r\n" +
            "if \"%~2\"==\"\" (\r\n" +
            "    set WAD=doom1.wad\r\n" +
            ") else (\r\n" +
            "    set WAD=%~2\r\n" +
            ")\r\n" +
            "java --enable-native-access=ALL-UNNAMED -Xmx1g -jar $jarName %WASM% %WAD%\r\n"
        )

        dir.resolve("doom.sh").writeText(
            "#!/bin/sh\n" +
            "DIR=\"\$(cd \"\$(dirname \"\$0\")\" && pwd)\"\n" +
            "WASM=\"\${1:-\$DIR/doom.wasm}\"\n" +
            "WAD=\"\${2:-\$DIR/doom1.wad}\"\n" +
            "exec java --enable-native-access=ALL-UNNAMED -Xmx1g -jar \"\$DIR/$jarName\" \"\$WASM\" \"\$WAD\"\n"
        )
        dir.resolve("doom.sh").setExecutable(true)

        println()
        println("DOOM ready in: ${dir.absolutePath}")
        println("  $jarName (${archiveFile.get().asFile.length() / 1024}K)")
        if (wasmFile.exists()) { println("  doom.wasm") } else { println("  doom.wasm — NOT FOUND, provide your own") }
        if (wadFile.exists()) { println("  doom1.wad") } else { println("  doom1.wad — NOT FOUND, provide your own") }
        println("  doom.bat / doom.sh")
        println()
        println("Run:  doom.bat  (or ./doom.sh)")
        println("      doom.bat <path/to/doom.wasm> <path/to/doom1.wad>")
    }
}
