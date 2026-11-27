plugins { `java-library` }

group = "dev.dbenton.tasplayback"

repositories { mavenCentral() }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
tasks.withType<JavaCompile>().configureEach { options.release = 8; options.encoding = "UTF-8" }

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }

// core ships inside the Java 8 nodes (1.12.2 - 1.16.5): run the same tests on a Java 8 JVM too.
val testJava8 by tasks.registering(Test::class) {
    description = "Runs the core tests on Java 8"
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(8) }
}
tasks.check { dependsOn(testJava8) }
