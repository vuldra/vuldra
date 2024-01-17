import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

plugins {
    application
    kotlin("multiplatform") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("dev.petuska.npm.publish") version "3.4.2"
}

group = "cli"
version = "0.1.0"

val PROGRAM = "vuldra"

repositories {
    mavenCentral()
    @Suppress("DEPRECATION")
    jcenter() // https://github.com/Kotlin/kotlinx-nodejs
}

dependencies {
    testImplementation(Testing.junit.jupiter.params)
    testRuntimeOnly(Testing.junit.jupiter.engine)
}

application {
    mainClass.set("cli.JvmMainKt")
}

val hostOs = System.getProperty("os.name")
val nativeTarget = when {
    hostOs == "Mac OS X" -> "MacosX64"
    hostOs == "Linux" -> "LinuxX64"
    hostOs.startsWith("Windows") -> "MingwX64"
    else -> throw GradleException("Host $hostOs is not supported in Kotlin/Native.")
}

fun KotlinNativeTargetWithHostTests.configureTarget() =
    binaries { executable { entryPoint = "main" } }

kotlin {
    macosX64 { configureTarget() }
    mingwX64 { configureTarget() }
    linuxX64 { configureTarget() }

    val jvmTarget = jvm()

    js(IR) {
        nodejs()
        binaries.executable()
    }

    sourceSets {
        val ktorVersion = "2.3.7"

        all {
            languageSettings.apply {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }

        val commonMain by getting {
            dependencies {
                implementation("com.github.ajalt.clikt:clikt:_")
                implementation("com.github.ajalt.mordant:mordant:_")
                implementation("com.squareup.okio:okio:_")
                implementation("com.aallam.openai:openai-client:3.6.2")
                implementation(KotlinX.coroutines.core)
                implementation(KotlinX.serialization.core)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("io.github.detekt.sarif4k:sarif4k:0.5.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(Kotlin.test.common)
                implementation(Kotlin.test.annotationsCommon)
                implementation("com.squareup.okio:okio-fakefilesystem:_")
            }
        }
        getByName("jvmMain") {
            dependsOn(commonMain)
            dependencies {
                implementation(Ktor.client.okHttp)
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(Testing.junit.jupiter.api)
                implementation(Testing.junit.jupiter.engine)
                implementation(Kotlin.test.junit5)
            }
        }
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }
        val posixMain by creating {
            dependsOn(nativeMain)
        }
        val posixTest by creating {
            dependsOn(nativeTest)
        }
        arrayOf("macosX64", "linuxX64").forEach { targetName ->
            getByName("${targetName}Main").dependsOn(posixMain)
            getByName("${targetName}Test").dependsOn(posixTest)
        }
        arrayOf("macosX64", "linuxX64", "mingwX64").forEach { targetName ->
            getByName("${targetName}Main").dependsOn(nativeMain)
            getByName("${targetName}Test").dependsOn(nativeTest)

        }
        getByName("macosX64Main") {
            dependencies {
                implementation(Ktor.client.darwin)
            }
        }
        getByName("linuxX64Main") {
            dependencies {
                implementation(Ktor.client.cio)
            }
        }
        getByName("mingwX64Main") {
            dependencies {
                implementation("io.ktor:ktor-client-winhttp:$ktorVersion")
            }
        }
        getByName("jsMain") {
            dependencies {
                implementation("com.squareup.okio:okio-nodefilesystem:_")
                implementation("io.ktor:ktor-client-js:$ktorVersion")
                implementation(KotlinX.nodeJs)
            }
        }
        getByName("jsTest") {
            dependsOn(nativeTest)
            dependencies {
                implementation(Kotlin.test.jsRunner)
                implementation(kotlin("test-js"))
            }
        }

    }

    tasks.withType<JavaExec> {
        // code to make run task in kotlin multiplatform work
        val compilation = jvmTarget.compilations.getByName<KotlinJvmCompilation>("main")

        val classes = files(
            compilation.runtimeDependencyFiles,
            compilation.output.allOutputs
        )
        classpath(classes)
    }
    tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        archiveBaseName.set(project.name)
        archiveClassifier.set("")
        archiveVersion.set("")

        from(jvmTarget.compilations.getByName("main").output)
        configurations = mutableListOf(
            jvmTarget.compilations.getByName("main").compileDependencyFiles,
            jvmTarget.compilations.getByName("main").runtimeDependencyFiles
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Copy>("install") {
    group = "run"
    description = "Build the native executable and copy it to the root of the project"

    dependsOn("runDebugExecutable$nativeTarget")
    val targetLowercase = nativeTarget.first().lowercaseChar() + nativeTarget.substring(1)
    val folder = "build/bin/$targetLowercase/debugExecutable"
    from(folder) {
        include("${rootProject.name}.kexe")
        rename { PROGRAM }
    }
    into(rootDir)
    doLast {
        println("You can now test the compiled binary in the root of the project.")
        println("If you want to install binary system-wide, run:")
        println("$ sudo cp $folder/${rootProject.name}.kexe /usr/local/bin/$PROGRAM")
    }
}

tasks.register("allRun") {
    group = "run"
    description = "Run $PROGRAM on the JVM, on Node and natively"
    dependsOn("run", "jsNodeRun", "runDebugExecutable$nativeTarget")
}

tasks.register("runOnGitHub") {
    group = "run"
    description = "CI with Github Actions : .github/workflows/runOnGitHub.yml"
    dependsOn("allTests", "allRun")
}

// See https://github.com/mpetuska/npm-publish
npmPublishing {
    dry = false
    repositories {
        val token = System.getenv("NPM_AUTH_TOKEN")
        if (token == null) {
            println("No environment variable NPM_AUTH_TOKEN found, using dry-run for publish")
            dry = true
        } else {
            repository("npmjs") {
                registry = uri("https://registry.npmjs.org")
                authToken = token
            }
        }
    }
    publications {
        publication("js") {
            readme = file("README.md")
            packageJson {
                bin = mutableMapOf(
                    Pair(PROGRAM, "./$PROGRAM")
                )
                main = PROGRAM
                private = false
                keywords = jsonArray(
                    "kotlin", "git", "bash"
                )
            }
            files { assemblyDir -> // Specifies what files should be packaged. Preconfigured for default publications, yet can be extended if needed
                from("$assemblyDir/../dir")
                from("bin") {
                    include(PROGRAM)
                }
            }
        }

    }
}

interface Injected {
    @get:Inject
    val exec: ExecOperations
    @get:Inject
    val fs: FileSystemOperations
}

tasks.register("completions") {
    group = "run"
    description = "Generate Bash/Zsh/Fish completion files"
    dependsOn(":install")
    val injected = project.objects.newInstance<Injected>()
    val shells = listOf(
        Triple("bash", file("completions/vuldra.bash"), "/usr/local/etc/bash_completion.d"),
        Triple("zsh", file("completions/_vuldra.zsh"), "/usr/local/share/zsh/site-functions"),
        Triple("fish", file("completions/vuldra.fish"), "/usr/local/share/fish/vendor_completions.d"),
    )
    for ((SHELL, FILE, INSTALL) in shells) {
        actions.add {
            println("Updating   $SHELL completion file at $FILE")
            injected.exec.exec {
                commandLine("vuldra", "--generate-completion", SHELL)
                standardOutput = FILE.outputStream()
            }
            println("Installing $SHELL completion into $INSTALL")
            injected.fs.copy {
                from(FILE)
                into(INSTALL)
            }
        }
    }
    doLast {
        println("On macOS, follow those instructions to configure shell completions")
        println("👀 https://docs.brew.sh/Shell-Completion 👀")
    }
}
