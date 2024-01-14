plugins {
    kotlin("jvm") version "1.8.22"
    application
}

application {
    mainClass = "com.vuldra.MainKt"
}

group = "com.vuldra"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.aallam.openai:openai-client:3.6.2")
    implementation("com.github.ajalt.clikt:clikt:4.2.2")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

