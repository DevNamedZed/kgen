plugins {
    application
}

application {
    mainClass.set("org.kgen.generator.WasmCodeGenKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("runX86") {
    group = "application"
    description = "Generate x86-64 assembler code"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.kgen.generator.X86CodeGenKt")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("runArm64") {
    group = "application"
    description = "Generate ARM64 assembler code"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.kgen.generator.Arm64CodeGenMainKt")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("runRiscV") {
    group = "application"
    description = "Generate RISC-V assembler code"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.kgen.generator.RiscVCodeGenMainKt")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("runJvm") {
    group = "application"
    description = "Generate JVM assembler code"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.kgen.generator.JvmCodeGenMainKt")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("runCil") {
    group = "application"
    description = "Generate CIL assembler code"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.kgen.generator.CilCodeGenMainKt")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("runIr") {
    group = "application"
    description = "Generate IR instruction data classes and visitors from JSON spec"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.kgen.generator.IrCodeGenMainKt")
    workingDir = rootProject.projectDir
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.yaml:snakeyaml:2.3")
}
