plugins {
    kotlin("jvm") version "2.3.21"

    id("io.ktor.plugin") version "3.5.0"

    kotlin("plugin.serialization") version "2.1.0"
}

group = "ru.jinushi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(kotlin("stdlib-jdk8"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("io.ktor:ktor-server-core")

    implementation("io.ktor:ktor-server-cio")

    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-status-pages")

    testImplementation("io.ktor:ktor-server-test-host")

    implementation("io.ktor:ktor-client-core")

    // 2. Асинхронный движок для клиента (выбираем знакомый CIO на корутинах)
    implementation("io.ktor:ktor-client-cio")

    // 3. Плагины для клиента (чтобы клиент автоматически конвертировал JSON в DTO)
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
}

tasks.test {
    useJUnitPlatform()
}