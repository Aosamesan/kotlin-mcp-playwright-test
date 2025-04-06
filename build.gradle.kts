plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "1.8.0"
    id("io.ktor.plugin") version "3.1.2"
}

group = "dev.skystar1"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    val mcpVersion: String by project
    val playwrightVersion: String by project
    val cliktVersion: String by project
    val ktorClientVersion: String by project

    implementation("io.modelcontextprotocol:kotlin-sdk:$mcpVersion")
    implementation("com.microsoft.playwright:playwright:$playwrightVersion")
    implementation("com.github.ajalt.clikt:clikt:$cliktVersion")
    implementation("io.ktor:ktor-client-core:$ktorClientVersion")
    implementation("io.ktor:ktor-client-cio:$ktorClientVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorClientVersion")
    implementation("io.ktor:ktor-client-cio-jvm:3.1.2")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.skystar1.MainKt")
}