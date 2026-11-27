plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11"

stonecutter parameters {
    constants["forge"] = current.project.endsWith("-forge")
    constants["legacyfabric"] = current.project.endsWith("-legacyfabric")
}

// `./gradlew buildAll`: tests core, builds every node, copies each release jar into dist/.
tasks.register("buildAll") {
    group = "build"
    description = "Builds every version node and collects the jars into dist/"
    dependsOn(":core:test")
    dependsOn(stonecutter.tasks.names("collectJar"))
}
