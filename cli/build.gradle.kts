plugins {
    application
    id("org.graalvm.buildtools.native") version "0.10.6"
}

application {
    mainClass.set("org.kgen.cli.MainKt")
}

dependencies {
    implementation(project(":kgen"))
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("kgen")
            mainClass.set("org.kgen.cli.MainKt")
            buildArgs.add("--no-fallback")
        }
    }
}
