// Buildscript for every modern Fabric node (1.14.4 → 26.x). Runs once per node.
// loom-back-compat applies `net.fabricmc.fabric-loom-remap` below 26.1 and the
// non-remapping `net.fabricmc.fabric-loom` on 26.1+.
plugins {
    id("dev.kikugie.loom-back-compat")
}

val modId: String = sc.properties["mod.id"]
val modVersion: String = sc.properties["mod.version"]
val mcVersion: String = sc.current.version
val javaVersion: Int = sc.properties.get("mod.java").toInt()

version = "$modVersion+$mcVersion"
base.archivesName = modId

repositories { mavenCentral() }

val shade: Configuration = configurations.create("shade") {
    isTransitive = false
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${sc.properties["deps.fabric_loader"]}")

    implementation(project(":core"))
    implementation(project(":platform-api"))
    shade(project(":core"))
    shade(project(":platform-api"))
}

loom {
    runConfigs.all {
        isIdeConfigGenerated = false
        runDirectory = rootProject.layout.projectDirectory.dir("run/$mcVersion")
    }
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(javaVersion) }
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

tasks {
    withType<JavaCompile>().configureEach {
        if (javaVersion > 8) options.release = javaVersion
        options.encoding = "UTF-8"
    }

    processResources {
        val props = mapOf(
            "id" to modId,
            "name" to sc.properties["mod.name"],
            "version" to version.toString(),
            "description" to sc.properties["mod.description"],
            "license" to sc.properties["mod.license"],
            "minecraft" to sc.properties["mod.mc_compat"],
            "java" to javaVersion.toString(),
        )
        inputs.properties(props)
        filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
    }

    // core + platform-api are plain Java 8 with no Minecraft references; shade them into the mod jar.
    jar {
        from(shade.elements.map { files -> files.map { zipTree(it) } }) {
            exclude("META-INF/MANIFEST.MF")
        }
        from(rootProject.file("COPYING"), rootProject.file("COPYING.LESSER"))
    }

    register<Copy>("collectJar") {
        group = "build"
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.projectDirectory.dir("dist"))
    }
}
