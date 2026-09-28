import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "gg.arch"
version = "1.2.1"

// Optional local-only escape hatch: this repo's standard location is under a OneDrive-synced,
// non-ASCII-named directory, and OneDrive's real-time file scanning holds transient locks on
// freshly-written class files that intermittently break Gradle's own incremental-build cleanup
// (`Unable to delete directory ... build\classes\java\main`). Passing
// `-ParchBuildDir=<path outside OneDrive>` redirects Gradle's output there for a local build;
// CI and any normal `./gradlew build` without the property are unaffected (default `build/`).
providers.gradleProperty("archBuildDir").orNull?.let { customBuildDir ->
    layout.buildDirectory.set(file(customBuildDir))
}

val gitCommitHash: String = try {
    val out = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short=8", "HEAD")
        standardOutput = out
        isIgnoreExitValue = true
    }
    out.toString(Charsets.UTF_8).trim().ifBlank { "unknown" }
} catch (ex: Exception) {
    "unknown"
}
val buildTimestamp: String = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .format(ZonedDateTime.now(ZoneOffset.UTC)) + " UTC"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") // paper-api
    maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("me.clip:placeholderapi:2.12.3")

    // Shaded/relocated into the plugin jar -- the MaxMind DB reader (low-level, dependency-free
    // reader for .mmdb files; NOT the full geoip2 web-service client, which pulls in
    // jackson-databind and an HTTP client we don't need for a purely local database lookup).
    implementation("com.maxmind.db:maxmind-db:4.2.0")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.platform:junit-platform-console:1.11.0")

    testCompileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing"))
}

// See build-workaround note above: the built-in `Test` task's forked-worker classpath packing
// has a Windows bug with non-ASCII project paths that breaks every test with
// ClassNotFoundException even though compilation succeeds. Run via JavaExec instead.
tasks.test {
    enabled = false
}

val runJUnitTests = tasks.register<JavaExec>("runJUnitTests") {
    group = "verification"
    description = "Runs the JUnit 5 test suite via the console launcher (see comment above for why not the `test` task)."
    dependsOn(tasks.named("testClasses"))
    classpath = files(sourceSets["test"].output, sourceSets["main"].output, configurations["testRuntimeClasspath"])
    mainClass.set("org.junit.platform.console.ConsoleLauncher")
    args = listOf(
            "execute",
            "--scan-classpath",
            "--details=tree",
            "--fail-if-no-tests",
            "--exclude-engine=junit-vintage"
    )
}

tasks.named("check") {
    dependsOn(runJUnitTests)
}

tasks.processResources {
    val versionProperties = mapOf("version" to project.version.toString())
    val buildInfoProperties = mapOf(
            "version" to project.version.toString(),
            "commitHash" to gitCommitHash,
            "buildTime" to buildTimestamp
    )
    inputs.properties(buildInfoProperties)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(versionProperties)
    }
    filesMatching("build-info.properties") {
        expand(buildInfoProperties)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("ArchFlags")
    archiveClassifier.set("")
    relocate("com.maxmind", "gg.arch.archflags.libs.maxmind")
    minimize()
}

tasks.jar {
    archiveBaseName.set("ArchFlags")
    archiveClassifier.set("plain")
}

tasks.build {
    dependsOn(tasks.named("shadowJar"))
}
