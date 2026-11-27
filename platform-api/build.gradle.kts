plugins { `java-library` }

group = "dev.dbenton.tasplayback"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
tasks.withType<JavaCompile>().configureEach { options.release = 8; options.encoding = "UTF-8" }

dependencies { api(project(":core")) }
