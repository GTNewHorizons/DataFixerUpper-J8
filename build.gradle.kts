// Repackages Mojang's DataFixerUpper (https://github.com/Mojang/DataFixerUpper) for Java 8 and Minecraft 1.7.10.
// There is no DFU source here: the build takes the release jar Mojang publishes to libraries.minecraft.net,
// patches its bytecode with src/patcher and bundles the compat classes from src/main.
plugins {
    java
    `maven-publish`
    id("xyz.wagyourtail.jvmdowngrader") version "2.0.1" // https://github.com/unimined/JvmDowngrader
}

// Bump to follow upstream; the build fails if the new version needs a shim that doesn't exist yet.
val dfuVersion = "10.0.21"

group = "com.github.GTNewHorizons"
version = System.getenv("VERSION") ?: "$dfuVersion-1" // the release workflow sets VERSION to the git tag

tasks.compileJava {
    options.release = 8
}

repositories {
    maven("https://libraries.minecraft.net") // Mojang's maven, the only place DFU is published
    mavenCentral()
}

val dfu by configurations.creating { isTransitive = false } // DFU alone, the jar that gets patched
val dfuDeps by configurations.creating { extendsFrom(dfu) } // with its deps, so JVM Downgrader can resolve class hierarchies

val patcher by sourceSets.creating // runs at build time only, never shipped

dependencies {
    dfu("com.mojang:datafixerupper:$dfuVersion")
    // The versions 1.7.10 ships, which the compat classes are written against.
    compileOnly("com.google.guava:guava:17.0")
    compileOnly("org.apache.logging.log4j:log4j-api:2.0-beta9")
    add(patcher.implementationConfigurationName, "org.ow2.asm:asm-commons:9.8")
}

val patchedDir = layout.buildDirectory.dir("patchedDfu")
// Kept out of build/libs, since the release workflow uploads every jar found there.
val intermediates = layout.buildDirectory.dir("intermediates")

// DFU uses Guava 21+ and slf4j, which 1.7.10 lacks: redirect those calls to GuavaCompat and DfuLogger.
val patchDfu by tasks.registering(JavaExec::class) {
    classpath = patcher.runtimeClasspath
    mainClass = "com.gtnewhorizons.dfu.build.DfuGuavaShimPatcher"
    inputs.files(dfu)
    outputs.dir(patchedDir)
    doFirst { delete(patchedDir) } // drop classes left over from a previous DFU version
    argumentProviders.add { listOf(dfu.singleFile.absolutePath, patchedDir.get().asFile.absolutePath) }
}

// Package the patched DFU with the compat classes and the licenses.
tasks.jar {
    destinationDirectory = intermediates
    from(patchDfu) { exclude("META-INF/MANIFEST.MF") }
    from("LICENSE") { into("META-INF") }
    from("licenses") { into("META-INF/licenses") }
}

// DFU targets Java 17: downgrade the bytecode to Java 8.
tasks.downgradeJar {
    classpath = dfuDeps
    destinationDirectory = intermediates
}

// Shade in the stubs for the Java 9+ APIs DFU calls, so dependents need no extra runtime lib.
tasks.shadeDowngradedApi {
    shadePath = "com/gtnewhorizons/dfu/jvmdg/"
    archiveClassifier = ""
    destinationDirectory = layout.buildDirectory.dir("libs")
}

tasks.assemble { dependsOn(tasks.shadeDowngradedApi) }

// The GTNH workflows call this; RetroFuturaGradle provides it in mod repos, here there is nothing to set up.
tasks.register("setupCIWorkspace")

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.shadeDowngradedApi)
        }
    }
    // The release workflow provides the credentials and URL. Its Modrinth and Curseforge steps also run
    // `publish` without them, so without credentials there is no repository and `publish` does nothing.
    val mavenUser = System.getenv("MAVEN_USER")
    if (mavenUser != null) {
        repositories {
            maven {
                url = uri(project.findProperty("mavenPublishUrl") ?: "https://nexus.gtnewhorizons.com/repository/releases/")
                credentials {
                    username = mavenUser
                    password = System.getenv("MAVEN_PASSWORD")
                }
            }
        }
    }
}
