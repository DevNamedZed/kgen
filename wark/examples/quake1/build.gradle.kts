plugins {
    application
}

dependencies {
    implementation(project(":wark"))
}

application {
    mainClass.set("org.wark.examples.quake1.QuakeSdlRunner")
}

tasks.named<JavaExec>("run") {
    args = listOf("../assets/quake.wasm", "../assets")
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx512m")
}

tasks.register<JavaExec>("runHeadless") {
    mainClass.set("org.wark.examples.quake1.QuakeRunner")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx512m")
    workingDir = projectDir
    args = listOf("../assets/quake.wasm", "../assets")
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("wark-quake1")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes("Main-Class" to "org.wark.examples.quake1.QuakeSdlRunner")
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

        val wasmFile = assetsDir.resolve("quake.wasm")
        val pakFile = assetsDir.resolve("pak0.pak")
        val sdlFile = assetsDir.resolve("SDL2.dll")
        if (wasmFile.exists()) {
            wasmFile.copyTo(dir.resolve("quake.wasm"), overwrite = true)
        }
        if (pakFile.exists()) {
            pakFile.copyTo(dir.resolve("pak0.pak"), overwrite = true)
        }
        if (sdlFile.exists()) {
            sdlFile.copyTo(dir.resolve("SDL2.dll"), overwrite = true)
        }

        dir.resolve("quake.bat").writeText(
            "@echo off\r\n" +
            "cd /d \"%~dp0\"\r\n" +
            "java --enable-native-access=ALL-UNNAMED -Xmx512m -jar $jarName quake.wasm . %*\r\n"
        )

        dir.resolve("quake.sh").writeText(
            "#!/bin/sh\n" +
            "DIR=\"\$(cd \"\$(dirname \"\$0\")\" && pwd)\"\n" +
            "exec java --enable-native-access=ALL-UNNAMED -Xmx512m -jar \"\$DIR/$jarName\" \"\$DIR/quake.wasm\" \"\$DIR\" \"\$@\"\n"
        )
        dir.resolve("quake.sh").setExecutable(true)

        println()
        println("Quake 1 ready in: ${dir.absolutePath}")
        println("  $jarName (${archiveFile.get().asFile.length() / 1024}K)")
        if (wasmFile.exists()) { println("  quake.wasm") }
        if (pakFile.exists()) { println("  pak0.pak") }
        if (sdlFile.exists()) { println("  SDL2.dll") }
        println("  quake.bat / quake.sh")
    }
}
