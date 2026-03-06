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

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
}
