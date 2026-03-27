plugins {
    application
}

dependencies {
    implementation(project(":wark"))
}

application {
    mainClass.set("org.wark.examples.wasm4.Wasm4SdlRunner")
}

tasks.named<JavaExec>("run") {
    val game = project.findProperty("game") as String? ?: "watris"
    args = listOf("../assets/wasm4/$game.wasm")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir = projectDir
}

tasks.register<JavaExec>("snakeDebug") {
    mainClass.set("org.wark.examples.wasm4.SnakeDebugKt")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir = projectDir
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("wark-wasm4")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Main-Class" to "org.wark.examples.wasm4.Wasm4SdlRunner")
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

        // Copy SDL2
        val sdlFile = assetsDir.resolve("SDL2.dll")
        if (sdlFile.exists()) {
            sdlFile.copyTo(dir.resolve("SDL2.dll"), overwrite = true)
        }

        // Copy games
        val gamesDir = dir.resolve("games")
        gamesDir.mkdirs()
        val wasm4Assets = assetsDir.resolve("wasm4")
        if (wasm4Assets.exists()) {
            wasm4Assets.listFiles()?.filter { it.extension == "wasm" }?.forEach { wasm ->
                wasm.copyTo(gamesDir.resolve(wasm.name), overwrite = true)
            }
            val gamesJson = wasm4Assets.resolve("games.json")
            if (gamesJson.exists()) {
                gamesJson.copyTo(gamesDir.resolve("games.json"), overwrite = true)
            }
        }

        // Batch file
        dir.resolve("wasm4.bat").writeText(
            "@echo off\r\n" +
            "cd /d \"%~dp0\"\r\n" +
            "java --enable-native-access=ALL-UNNAMED -jar $jarName %*\r\n"
        )

        // Interpreter batch file
        dir.resolve("wasm4-interp.bat").writeText(
            "@echo off\r\n" +
            "cd /d \"%~dp0\"\r\n" +
            "java --enable-native-access=ALL-UNNAMED -jar $jarName --interpret %*\r\n"
        )

        // Shell script
        dir.resolve("wasm4.sh").writeText(
            "#!/bin/sh\n" +
            "DIR=\"\$(cd \"\$(dirname \"\$0\")\" && pwd)\"\n" +
            "exec java --enable-native-access=ALL-UNNAMED -jar \"\$DIR/$jarName\" \"\$@\"\n"
        )
        dir.resolve("wasm4.sh").setExecutable(true)

        println()
        println("WASM-4 ready in: ${dir.absolutePath}")
        println("  $jarName (${archiveFile.get().asFile.length() / 1024}K)")
        println("  games/")
        gamesDir.listFiles()?.filter { it.extension == "wasm" }?.forEach {
            println("    ${it.name} (${it.length() / 1024}K)")
        }
        println()
        println("Run:  wasm4.bat games/watris.wasm")
        println("      wasm4.bat                    (game selector)")
    }
}
