dependencies {
    api(project(":kgen"))
}

tasks.register<JavaExec>("runDoomJit") {
    mainClass.set("org.wark.DoomJitTraceRunnerKt")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx1g", "-XX:ErrorFile=build/hs_err.log")
    workingDir = projectDir
}

tasks.register<JavaExec>("debug") {
    mainClass.set("org.wark.cli.WarkCli")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Xmx2g")
    workingDir = projectDir
    standardInput = System.`in`
    args = listOf("debug") + (project.findProperty("warkArgs") as String? ?: "examples/assets/doom.wasm").split(" ")
}
