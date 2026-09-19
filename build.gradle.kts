plugins {
    java
    `maven-publish`
    id("xyz.wagyourtail.jvmdowngrader") version "1.3.6"
}

val dfuVersion = "10.0.21"

group = "com.github.0hwx"
version = System.getenv("VERSION") ?: "$dfuVersion-1"

repositories {
    maven("https://libraries.minecraft.net")
    mavenCentral()
}

val dfu by configurations.creating { isTransitive = false }
// DFU's own deps, only used so JVM Downgrader can resolve class hierarchies.
val dfuDeps by configurations.creating

val patcher by sourceSets.creating

dependencies {
    dfu("com.mojang:datafixerupper:$dfuVersion")
    dfuDeps("com.mojang:datafixerupper:$dfuVersion")
    // Guava and log4j versions shipped with Minecraft 1.7.10.
    compileOnly("com.google.guava:guava:17.0")
    compileOnly("org.apache.logging.log4j:log4j-api:2.0-beta9")
    add(patcher.implementationConfigurationName, "org.ow2.asm:asm-commons:9.8")
}

tasks.compileJava {
    options.release = 8
}

val patchedDir = layout.buildDirectory.dir("patchedDfu")

val patchDfu by tasks.registering(JavaExec::class) {
    classpath = patcher.runtimeClasspath
    mainClass = "com.github.hwx.dfu.build.DfuGuavaShimPatcher"
    inputs.files(dfu)
    outputs.dir(patchedDir)
    argumentProviders.add { listOf(dfu.singleFile.absolutePath, patchedDir.get().asFile.absolutePath) }
}

tasks.jar {
    from(patchDfu) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    }
    from("LICENSE") { into("META-INF") }
    from("licenses") { into("META-INF/licenses") }
}

tasks.downgradeJar {
    classpath = dfuDeps
    archiveClassifier = ""
    destinationDirectory = layout.buildDirectory.dir("libs/downgraded")
}

tasks.assemble { dependsOn(tasks.downgradeJar) }

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = rootProject.name
            artifact(tasks.downgradeJar)
        }
    }
}
