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

tasks.register<JavaExec>("testNewGame") {
    mainClass.set("org.wark.examples.quake1.QuakeNewGameTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx512m")
    workingDir = projectDir
    val modeArg = providers.gradleProperty("mode").getOrElse("interpret")
    val extraArgs = providers.gradleProperty("extraArgs").getOrElse("")
    args = listOfNotNull(modeArg) + extraArgs.split(",").filter { it.isNotBlank() }
}

tasks.register<JavaExec>("frameDiff") {
    mainClass.set("org.wark.examples.quake1.QuakeFrameDiffTest")
    classpath = sourceSets["test"].runtimeClasspath
    val skipMem2RegProp = providers.gradleProperty("skipMem2Reg").getOrElse("false")
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx1g", "-DskipMem2Reg=$skipMem2RegProp")
    workingDir = projectDir
}

tasks.register<JavaExec>("memDiff") {
    mainClass.set("org.wark.examples.quake1.QuakeMemDiffTest")
    classpath = sourceSets["test"].runtimeClasspath
    val framesProp = providers.gradleProperty("frames").getOrElse("0")
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx1g", "-Dframes=$framesProp")
    workingDir = projectDir
}

tasks.register<JavaExec>("swapAddress") {
    mainClass.set("org.wark.examples.quake1.QuakeSwapAddressTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir = projectDir
}

tasks.register<JavaExec>("swapInit") {
    mainClass.set("org.wark.examples.quake1.QuakeSwapInitTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx512m")
    workingDir = projectDir
}

tasks.register<JavaExec>("callIndirectTest") {
    mainClass.set("org.wark.examples.quake1.QuakeCallIndirectTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx512m")
    workingDir = projectDir
}

tasks.register<JavaExec>("endianCheck") {
    mainClass.set("org.wark.examples.quake1.QuakeEndianTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir = projectDir
}

tasks.register<JavaExec>("pakRead") {
    mainClass.set("org.wark.examples.quake1.QuakePakReadTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir = projectDir
}

tasks.register<JavaExec>("bspVerify") {
    mainClass.set("org.wark.examples.quake1.QuakeBspVerifyTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx512m")
    workingDir = projectDir
}

tasks.register<JavaExec>("mapLoad") {
    mainClass.set("org.wark.examples.quake1.QuakeMapLoadTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx512m")
    workingDir = projectDir
    val modeArg = providers.gradleProperty("mode").getOrElse("interpret")
    args = listOf(modeArg)
}

tasks.register<JavaExec>("findWriter") {
    mainClass.set("org.wark.examples.quake1.QuakeFindWriterTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir = projectDir
}

tasks.register<JavaExec>("analyzeOob") {
    mainClass.set("org.wark.examples.quake1.QuakeOobFunctionTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    workingDir = projectDir
}

tasks.register<JavaExec>("modForName") {
    mainClass.set("org.wark.examples.quake1.QuakeModForNameTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx512m")
    workingDir = projectDir
}

tasks.register<JavaExec>("hunkOverlap") {
    mainClass.set("org.wark.examples.quake1.QuakeHunkOverlapTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx1g")
    workingDir = projectDir
}

tasks.register<JavaExec>("rendererTest") {
    mainClass.set("org.wark.examples.quake1.QuakeRendererTest")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx1g")
    workingDir = projectDir
}

tasks.register<JavaExec>("littleShortInvestigation") {
    mainClass.set("org.wark.examples.quake1.QuakeLittleShortInvestigation")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx512m")
    workingDir = projectDir
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
