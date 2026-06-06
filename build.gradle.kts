plugins {
    val ktorVersion = "3.5.0"

    kotlin("jvm") version "2.3.21"

    id("io.ktor.plugin") version ktorVersion

    kotlin("plugin.serialization") version "2.1.0"
}

kotlin {
    jvmToolchain(21)
}

group = "ru.jinushi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val ktorVersion = "3.5.0"

    testImplementation(kotlin("test"))

    implementation(kotlin("stdlib-jdk8"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("io.ktor:ktor-server-core")

    implementation("io.ktor:ktor-server-cio")

    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-swagger")

    testImplementation("io.ktor:ktor-server-test-host")

    implementation("io.ktor:ktor-client-core")

    implementation("io.ktor:ktor-client-cio")

    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-websockets")
    implementation(platform("io.ktor:ktor-bom:$ktorVersion"))
}

tasks.test {
    useJUnitPlatform()
}