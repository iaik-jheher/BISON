import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeoutException

plugins {
    kotlin("jvm") version "1.8.0"
    id("io.ktor.plugin") version "2.3.4"
    application
}

group = "ec"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(files("./dep/bison-QUICK-DIRTY-TEST-ONLY.jar"))
    implementation("com.nimbusds:oauth2-oidc-sdk:11.23")
    implementation("io.ktor:ktor-server-core-jvm:2.3.4")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.4")
    implementation("io.ktor:ktor-server-freemarker:2.3.4")
    implementation("io.ktor:ktor-server-status-pages-jvm:2.3.4")
    implementation("io.ktor:ktor-server-caching-headers:2.3.4")
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("bison.MainKt")
}

fun List<String>.execute(
    cwd: File = rootDir,
    timeoutAmount: Long = 10,
    timeoutUnit: TimeUnit = TimeUnit.SECONDS)
        : String =
    ProcessBuilder(this)
        .directory(cwd)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE).start()
        .also {
            val terminated = it.waitFor(timeoutAmount, timeoutUnit)
            if (!terminated) {
                it.destroy()
                throw TimeoutException()
            }
        }
        .let {
            val error = it.errorStream.bufferedReader().readText().trim()
            if (error.isNotEmpty())
                throw IOException(error)
            it.inputStream.bufferedReader().readText().trim()
        }

val gitHash = runCatching { listOf("git","rev-parse","--short","HEAD").execute() }.getOrElse { println(it); "<rev-error>" }
val gitTime = runCatching { listOf("git","show","-s","--format=%ai","HEAD").execute() }.getOrElse { println(it); "<time error>" }
val versionString = "commit $gitHash from $gitTime built ${
    OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xx"))}"
println("Setting VERSION to '$versionString'")
val versionFile = File(buildDir,"resources/main/bison/VERSION").also {
    it.ensureParentDirsCreated()
    it.writeText(versionString)
}

/*
         * Creates a [DockerImageRegistry] from a given [project], [username] and [password],
         * and an optional [hostname] and [namespace].
         *
         * The [hostname], [namespace], and [project] are combined in order to generate
         * the full image name, e.g.:
         * - hostname/namespace/project
         * - hostname/project
         * - project
*/

ktor {
    docker {
        externalRegistry.set(
            io.ktor.plugin.features.DockerImageRegistry.externalRegistry(
                hostname = providers.environmentVariable("CI_REGISTRY"),
                namespace = providers.environmentVariable("BISON_IMAGE_NAMESPACE"),
                project = provider { "idp" },
                username = providers.environmentVariable("CI_REGISTRY_USER"),
                password = providers.environmentVariable("CI_REGISTRY_PASSWORD")
            )
        )
    }
}

