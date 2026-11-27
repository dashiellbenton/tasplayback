// Buildscript for the 1.12.2 Forge node (RetroFuturaGradle + MixinBooter, MCP stable_39).
plugins {
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.6"
}

val modId: String = sc.properties["mod.id"]
val modVersion: String = sc.properties["mod.version"]

version = "$modVersion+1.12.2-forge"
group = sc.properties["mod.group"]
base.archivesName = modId

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
        vendor = JvmVendorSpec.AZUL
    }
}

// Shared MCP-named code (legacy/src/main, Stonecutter-processed) + Forge glue.
sourceSets.main {
    java.srcDir(rootProject.file("legacy/src/forge/java"))
    resources.srcDir(rootProject.file("legacy/src/forge/resources"))
}

minecraft {
    mcVersion = "1.12.2"
    mcpMappingChannel = sc.properties.get("deps.mcp_channel")
    mcpMappingVersion = sc.properties.get("deps.mcp_version")
    username = "Developer"
    extraRunJvmArguments.addAll("-Dmixin.debug.export=true", "-Dmixin.checks.interfaces=true")
}

repositories {
    maven("https://maven.cleanroommc.com") { name = "CleanroomMC" }
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
    val mixin = modUtils.enableMixins("zone.rong:mixinbooter:${sc.properties["deps.mixinbooter"]}", refmap).toString()
    implementation(mixin) { isTransitive = false }
    annotationProcessor(mixin)

    implementation(project(":core"))
    implementation(project(":platform-api"))
    shade(project(":core"))
    shade(project(":platform-api"))
}

tasks {
    withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

    processResources {
        val props = mapOf(
            "id" to modId, "name" to sc.properties["mod.name"], "version" to version.toString(),
            "description" to sc.properties["mod.description"], "refmap" to refmap,
        )
        inputs.properties(props)
        filesMatching(listOf("mcmod.info", "*.mixins.json")) { expand(props) }
    }

    jar {
        manifest { attributes("MixinConfigs" to "$modId.mixins.json") }
        from(shade.elements.map { files -> files.map { zipTree(it) } }) { exclude("META-INF/MANIFEST.MF") }
        from(rootProject.file("COPYING"), rootProject.file("COPYING.LESSER"))
    }

    register<Copy>("collectJar") {
        group = "build"
        // reobfJar produces the SRG-named release jar
        from(named("reobfJar").map { it.outputs.files })
        into(rootProject.layout.projectDirectory.dir("dist"))
    }
}
