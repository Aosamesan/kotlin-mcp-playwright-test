import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.shadowJar

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "1.8.0"
    java
    id("com.gradleup.shadow") version "9.0.0-beta11"
}

group = "dev.skystar1"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    val mcpVersion = rootProject.property("mcp.version") as String

    implementation("io.modelcontextprotocol:kotlin-sdk:$mcpVersion")
    implementation("com.microsoft.playwright:playwright:1.51.0")
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "dev.skystar1.MainKt"
    }
}