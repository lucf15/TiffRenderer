import com.vanniktech.maven.publish.KotlinMultiplatform

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

group = "io.github.lucf15"
version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "io.github.lucf15.tiffrenderer.core"
        compileSdk = 36
        minSdk = 24
        withHostTest {}
        withDeviceTest {}
    }

    jvm()

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
        androidMain.dependencies {
            implementation(project(":lib:native"))
        }
        jvmMain {
            resources.srcDir(nativeCore.layout.buildDirectory.dir("jvm"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}

tasks.named("jvmProcessResources") {
    dependsOn(project(":lib:native").tasks.named("buildTiffRendererJniForJvm"))
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
