// Buildscript for the 1.12.2 LegacyFabric node (TASmod's loader).
// Same toolchain as released TASmod: fabric-loom + legacy-looming, MCP names on LegacyFabric intermediary.
plugins {
    id("legacy-looming") version "1.16.1"
}

val modId: String = sc.properties["mod.id"]
val modVersion: String = sc.properties["mod.version"]

version = "$modVersion+1.12.2-legacyfabric"
base.archivesName = modId

sourceSets.main {
    java.srcDir(rootProject.file("legacy/src/legacyfabric/java"))
    resources.srcDir(rootProject.file("legacy/src/legacyfabric/resources"))
}

repositories {
    maven("https://repo.legacyfabric.net/legacyfabric/") { name = "LegacyFabric" }
    // MCP mappings expressed against LegacyFabric intermediary (net.legacyfabric:yarn:<mc>+build.mcp)
    maven("https://raw.githubusercontent.com/BleachDev/cursed-mappings/main/") {
        name = "cursed-mappings"
        content { includeVersionByRegex("net\\.legacyfabric", "yarn", ".*\\+build\\.mcp") }
    }
    mavenCentral()
}

val shade: Configuration = configurations.create("shade") {
    isTransitive = false
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}
val refmap = "mixins.$modId.refmap.json"

dependencies {
    minecraft("com.mojang:minecraft:1.12.2")
    mappings("net.legacyfabric:yarn:${sc.properties["deps.lf_mappings"]}")
    modImplementation("net.fabricmc:fabric-loader:${sc.properties["deps.fabric_loader"]}")

    implementation(project(":core"))
    implementation(project(":platform-api"))
    shade(project(":core"))
    shade(project(":platform-api"))
}

loom {
    runConfigs.all {
        (findProperty("taspJvm") as String?)?.split(' ')?.filter { it.isNotBlank() }?.let { vmArgs(it) }
        ideConfigGenerated(false)
        runDir = "../../../run/1.12.2-legacyfabric"
    }
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(8) }
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

    processResources {
        val props = mapOf(
            "id" to modId, "name" to sc.properties["mod.name"], "version" to version.toString(),
            "description" to sc.properties["mod.description"], "license" to sc.properties["mod.license"],
            "refmap" to refmap,
        )
        inputs.properties(props)
        filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
        // Loom remaps mixin targets directly in the bytecode (no annotation processor / refmap),
        // so drop the shared config's refmap entry, which only the Forge node needs.
        inputs.property("dropRefmapEntry", true)
        filesMatching("*.mixins.json") { filter { line -> if (line.contains("\"refmap\"")) "" else line } }
    }

    jar {
        from(shade.elements.map { files -> files.map { zipTree(it) } }) { exclude("META-INF/MANIFEST.MF") }
        from(rootProject.file("COPYING"), rootProject.file("COPYING.LESSER"))
    }

    register<Copy>("collectJar") {
        group = "build"
        from(named("remapJar").map { it.outputs.files })
        into(rootProject.layout.projectDirectory.dir("dist"))
    }
}
