@file:OptIn(kotlinx.validation.ExperimentalBCVApi::class, org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.KotlinMultiplatform
import kotlinx.validation.ApiValidationExtension

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.resources)
    alias(libs.plugins.binary.compatibility.validator) apply false
}

group = "io.github.lucf15"
version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"

val bcvKlibPipelineHostRecognized = System.getProperty("os.name").lowercase().contains("mac") ||
        System.getProperty("os.arch").lowercase() in setOf("x86_64", "amd64")
if (bcvKlibPipelineHostRecognized) {
    apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")
}

kotlin {
    applyDefaultHierarchyTemplate()
    explicitApi()
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "io.github.lucf15.tiffrenderer.core"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        withHostTest {}
        withDeviceTest {}
    }

    jvm()

    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

    val nativeCore = project(":lib:native")

    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        val iosLibDir = nativeCore.layout.buildDirectory.dir("ios/${iosTarget.name}").get().asFile

        val tiffcoreCinterop = iosTarget.compilations.getByName("main").cinterops.create("tiffcore") {
            defFile("src/nativeInterop/cinterop/tiffcore.def")
            packageName("tiffcore")
            compilerOpts("-I${iosLibDir.path}")
            extraOpts("-libraryPath", iosLibDir.path)
        }

        tasks.named(tiffcoreCinterop.interopProcessingTaskName) {
            dependsOn(nativeCore.tasks.named("buildTiffCoreForIos"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }

        val jvmAndroidMain = create("jvmAndroidMain") {
            dependsOn(commonMain.get())
        }
        androidMain {
            dependsOn(jvmAndroidMain)
            dependencies {
                implementation(project(":lib:native"))
            }
        }
        jvmMain {
            dependsOn(jvmAndroidMain)
            resources.srcDir(nativeCore.layout.buildDirectory.dir("jvm"))
        }
        wasmJsMain {
            resources.srcDir(nativeCore.layout.buildDirectory.dir("wasm"))
        }
        wasmJsTest {
            resources.srcDir(nativeCore.layout.buildDirectory.dir("wasm"))
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val integrationTest = create("integrationTest") {
            dependsOn(commonTest.get())
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.resources)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.get().dependsOn(integrationTest)

        matching { it.name == "iosTest" }.configureEach { dependsOn(integrationTest) }

        getByName("androidDeviceTest") {
            dependsOn(integrationTest)
            dependencies {
                implementation(libs.junit)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}

if (bcvKlibPipelineHostRecognized) {
    configure<ApiValidationExtension> {
        klib {
            enabled = true
        }
    }
}

tasks.named("jvmProcessResources") {
    dependsOn(project(":lib:native").tasks.named("buildTiffRendererJniForJvm"))
}

tasks.named("wasmJsProcessResources") {
    dependsOn(project(":lib:native").tasks.named("buildTiffCoreForWasm"))
}

tasks.named("wasmJsTestProcessResources") {
    dependsOn(project(":lib:native").tasks.named("buildTiffCoreForWasm"))
}

tasks.matching { it.name == "wasmJsTestCopyResources" }.configureEach {
    dependsOn(project(":lib:native").tasks.named("buildTiffCoreForWasm"))
}

tasks.named<Jar>("jvmJar") {
    manifest {
        attributes("Implementation-Version" to version.toString())
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "tiffrenderer", version.toString())

    configure(KotlinMultiplatform())

    pom {
        name = "TiffRenderer"
        description = "A Kotlin Multiplatform library that decodes and rasterizes TIFF files, including multi-page/multi-directory ones."
        inceptionYear = "2026"
        url = "https://github.com/lucf15/TiffRenderer"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                id = "lucf15"
                name = "lucf15"
                url = "https://github.com/lucf15"
            }
        }

        scm {
            url = "https://github.com/lucf15/TiffRenderer"
            connection = "scm:git:git://github.com/lucf15/TiffRenderer.git"
            developerConnection = "scm:git:ssh://git@github.com/lucf15/TiffRenderer.git"
        }
    }
}
