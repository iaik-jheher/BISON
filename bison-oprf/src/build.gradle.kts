plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "ec"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("cafe.cryptography:curve25519-elisabeth:0.1.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

tasks.jar {
    val dependencies = configurations.runtimeClasspath.get()
        .map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

application {
    mainClass.set("MainKt")
}