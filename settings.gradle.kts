pluginManagement {
    repositories {
        // Specific repos first (with content filters) so Maven Central is probed as little as possible.
        exclusiveContent {
            forRepository { maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" } }
            filter { includeGroupByRegex("dev\\.kikugie.*") }
        }
        exclusiveContent {
            forRepository {
                maven("https://nexus.gtnewhorizons.com/repository/public/") { name = "GTNH (RetroFuturaGradle)" }
            }
            filter { includeGroupByRegex("com\\.gtnewhorizons.*") }
        }
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://repo.legacyfabric.net/legacyfabric/") { name = "LegacyFabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.10"
    id("dev.kikugie.loom-back-compat") version "0.4.3"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "tasplayback"

// Version-neutral modules, plain Java 8, bundled into every jar.
include("core", "platform-api")

stonecutter {
    create(rootProject) {
        // Modern Fabric nodes (Mojang mappings / unobfuscated). Shared source: src/
        // Buildscript: build.gradle.kts
        versions("1.14.4", "1.16.5", "1.20.1", "1.21.11", "26.3")

        // 1.12.2 nodes. Shared MCP-named source: legacy/src/
        branch("legacy") {
            versions("1.12.2-forge" to "1.12.2").buildscript("build.forge.gradle.kts")
            versions("1.12.2-legacyfabric" to "1.12.2").buildscript("build.legacyfabric.gradle.kts")
        }

        vcsVersion = "1.21.11"
    }
}
