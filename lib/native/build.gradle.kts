import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.gradle.api.tasks.PathSensitivity

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"

val skipAndroidNativeBuild = System.getenv("TIFFRENDERER_SKIP_ANDROID_NATIVE_BUILD") == "true"
val prebuiltJniLibsDir = System.getenv("TIFFRENDERER_PREBUILT_JNI_LIBS_DIR")

android {
    namespace = "io.github.lucf15.tiffrenderer.nativelib"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    ndkVersion = libs.versions.androidNdk.get()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (!skipAndroidNativeBuild) {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DANDROID_STL=c++_static")
                    if (System.getenv("TIFFRENDERER_WERROR") == "true") {
                        arguments += "-DTIFFRENDERER_WERROR=ON"
                    }
                }
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    if (!skipAndroidNativeBuild) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = libs.versions.cmake.get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "consumer-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmToolchain.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmToolchain.get())
    }

    buildFeatures {
        buildConfig = false
    }

}

if (skipAndroidNativeBuild) {
    androidComponents {
        onVariants { variant ->
            variant.sources.jniLibs?.addStaticSourceDirectory(requireNotNull(prebuiltJniLibsDir) {
                "TIFFRENDERER_PREBUILT_JNI_LIBS_DIR must be set when TIFFRENDERER_SKIP_ANDROID_NATIVE_BUILD=true"
            })
        }
    }
}

dependencies {
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

val buildTiffCoreForIos = tasks.register<Exec>("buildTiffCoreForIos") {
    group = "build"
    description = "Cross-compiles tiffrenderer_core for iosArm64/iosSimulatorArm64 via CMake."

    onlyIf { System.getenv("TIFFRENDERER_SKIP_IOS_NATIVE_BUILD") != "true" }

    val cppDir = file("src/main/cpp")
    val outputDir = layout.buildDirectory.dir("ios")

    workingDir = cppDir
    commandLine("./build-ios.sh", outputDir.get().asFile.absolutePath)

    inputs.dir(cppDir).withPropertyName("cppSources").ignoreEmptyDirectories()
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(outputDir).withPropertyName("iosLibs")
    outputs.cacheIf { true }
}

val buildTiffCoreForWasm = tasks.register<Exec>("buildTiffCoreForWasm") {
    group = "build"
    description = "Cross-compiles tiffrenderer_core into a wasmJs ES module via Emscripten/CMake."

    onlyIf { System.getenv("TIFFRENDERER_SKIP_WASM_NATIVE_BUILD") != "true" }

    val cppDir = file("src/main/cpp")
    val outputDir = layout.buildDirectory.dir("wasm")

    workingDir = cppDir
    commandLine("./build-wasm.sh", outputDir.get().asFile.absolutePath)

    inputs.dir(cppDir).withPropertyName("cppSources").ignoreEmptyDirectories()
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(outputDir).withPropertyName("wasmModule")
    outputs.cacheIf { true }
}

val buildTiffRendererJniForJvm = tasks.register<Exec>("buildTiffRendererJniForJvm") {
    group = "build"
    description = "Builds tiffrenderer_jni_jvm for the host OS/arch via CMake."

    onlyIf { System.getenv("TIFFRENDERER_SKIP_JVM_NATIVE_BUILD") != "true" }

    val cppDir = file("src/main/cpp")
    val outputDir = layout.buildDirectory.dir("jvm/natives")
    val javaHome = System.getProperty("java.home")

    val gitBashCandidates = listOf(
        "C:\\Program Files\\Git\\bin\\bash.exe",
        "C:\\Program Files (x86)\\Git\\bin\\bash.exe",
    )
    val bashExecutable = gitBashCandidates.firstOrNull { File(it).exists() } ?: "bash"

    fun toBashPath(path: String) = path.replace('\\', '/')
    commandLine(
        bashExecutable,
        toBashPath(cppDir.resolve("build-jvm.sh").absolutePath),
        toBashPath(outputDir.get().asFile.absolutePath),
        toBashPath(javaHome),
    )

    inputs.dir(cppDir).withPropertyName("cppSources").ignoreEmptyDirectories()
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(outputDir).withPropertyName("jvmNatives")
    outputs.cacheIf { true }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates("io.github.lucf15", "tiffrenderer-native", version.toString())

    configure(AndroidSingleVariantLibrary())

    pom {
        name = "TiffRenderer Native"
        description = "Native (JNI) binaries backing TiffRenderer's Android target."
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
