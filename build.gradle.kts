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

// Large per-era differences live in their own packages (fabric/<era>/) with their own mixin config.
// A node compiles only its era; nodes without one get the common bootstrap only (until ported, see PLAN M6).
val allEras = listOf("mc12111")
val era: String? = when (mcVersion) {
    "1.21.11" -> "mc12111"
    else -> null
}
sourceSets.main {
    java { allEras.filter { it != era }.forEach { exclude("dev/dbenton/tasplayback/fabric/$it/**") } }
    resources { allEras.filter { it != era }.forEach { exclude("tasplayback.$it.mixins.json") } }
}
val mixinConfigs = listOf("tasplayback.mixins.json") + listOfNotNull(era?.let { "tasplayback.$it.mixins.json" })
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
        // Extra client JVM args for scripted test runs: ./gradlew :<node>:runClient -PtaspJvm="-Dtasplayback.devtest=a ..."
        (findProperty("taspJvm") as String?)?.split(' ')?.filter { it.isNotBlank() }?.let { vmArgs(it) }
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
            "mixins" to mixinConfigs.joinToString(", ") { "\"$it\"" },
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
