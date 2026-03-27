plugins {
    kotlin("jvm") version "2.1.10" apply false
    `maven-publish`
    signing
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("com.diffplug.spotless") version "7.0.2" apply false
}

allprojects {
    group = "org.kgen"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "com.diffplug.spotless")
    if (path in listOf(":kgen", ":wark")) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            ktlint("1.5.0")
                .setEditorConfigPath(rootProject.file(".editorconfig"))
            targetExclude("**/generated/**")
        }
    }

    dependencies {
        "testImplementation"(kotlin("test"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        maxHeapSize = "1g"
        forkEvery = 10
        binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/${name}/binary-${System.currentTimeMillis()}"))
    }

    val javadocJar by tasks.registering(Jar::class) { archiveClassifier.set("javadoc") }
    val sourcesJar by tasks.registering(Jar::class) {
        archiveClassifier.set("sources")
        from(project.extensions.getByType<SourceSetContainer>()["main"].allSource)
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                artifact(javadocJar)
                artifact(sourcesJar)
                artifactId = if (project.name == "kgen") "kgen" else "kgen-${project.name}"

                pom {
                    name.set(artifactId)
                    description.set("Assembler toolkit and compiler infrastructure for the JVM")
                    url.set("https://github.com/kgen-org/kgen")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    developers {
                        developer {
                            id.set("kgen")
                            name.set("kgen contributors")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/kgen-org/kgen.git")
                        developerConnection.set("scm:git:ssh://github.com/kgen-org/kgen.git")
                        url.set("https://github.com/kgen-org/kgen")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "ossrh"
                val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
                credentials {
                    username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                    password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
                }
            }
        }
    }

    configure<SigningExtension> {
        val signingKey = findProperty("signingKey") as String? ?: System.getenv("GPG_SIGNING_KEY")
        val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("GPG_SIGNING_PASSWORD")
        if (signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
        sign(extensions.getByType<PublishingExtension>().publications["maven"])
        isRequired = !version.toString().endsWith("SNAPSHOT")
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }
}

// --- Top-level convenience tasks ---

// Core modules for building/testing
val coreModules = listOf(":kgen", ":wark", ":integration", ":generator", ":cli")
// Modules with reliable test suites for coverage
val coverageModules = listOf(":kgen", ":wark")

tasks.register("buildAll") {
    description = "Build core modules (kgen, wark, integration, generator, cli)"
    group = "build"
    dependsOn(coreModules.map { project(it).tasks.named("classes") })
}

tasks.register("testAll") {
    description = "Run tests for core modules"
    group = "verification"
    dependsOn(coreModules.filter { it != ":cli" && it != ":generator" }.map { project(it).tasks.named("test") })
}

// Kover merged report at root build/reports/kover/html
dependencies {
    coverageModules.forEach { path ->
        kover(project(path))
    }
}

kover {
    reports {
        total {
            html {
                onCheck = false
                htmlDir = layout.buildDirectory.dir("reports/kover/html")
            }
        }
    }
}

tasks.register("lint") {
    description = "Check formatting across all modules"
    group = "verification"
    dependsOn(subprojects.map { it.tasks.named("spotlessCheck") })
}

tasks.register("format") {
    description = "Auto-format all source files"
    group = "verification"
    dependsOn(subprojects.map { it.tasks.named("spotlessApply") })
}

tasks.register("coverage") {
    description = "Run tests and generate merged coverage report (build/reports/kover/html)"
    group = "verification"
    dependsOn("koverHtmlReport")
    doLast {
        println("Coverage report: file://${rootProject.projectDir}/build/reports/kover/html/index.html")
    }
}

// Collect jars from core modules into build/libs/
tasks.register<Copy>("collectJars") {
    description = "Copy core module jars to build/libs/"
    group = "build"
    val coreProjects = coreModules.map { project(it) }
    from(coreProjects.flatMap { sub ->
        sub.tasks.withType<Jar>().map { it.archiveFile }
    })
    into(layout.buildDirectory.dir("libs"))
    dependsOn(coreProjects.map { it.tasks.named("jar") })
}
