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
}

tasks.test {
    useJUnitPlatform()
}