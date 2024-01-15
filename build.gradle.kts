plugins {
    kotlin("multiplatform") version "1.8.22"
    application
}

application {
    mainClass.set("com.vuldra.VuldraKt")
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
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
    jvm() {

    }
    js(IR) {
        nodejs {
            binaries.executable()
        }
    }
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "com.vulrda.VuldraKt"
            }
        }
    }
    sourceSets {
        val okioVersion = "3.7.0"
        val ktorVersion = "2.3.7"
        val commonMain by getting {
            dependencies {
                implementation("com.squareup.okio:okio:$okioVersion")
                implementation("com.aallam.openai:openai-client:3.6.2")
                implementation("com.github.ajalt.clikt:clikt:4.2.2")
                implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation("com.squareup.okio:okio-fakefilesystem:$okioVersion")
                implementation(kotlin("test"))
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-curl:$ktorVersion")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-apache5:$ktorVersion")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:$ktorVersion")
                implementation("com.squareup.okio:okio-nodefilesystem:$okioVersion")
            }
        }
    }
}

