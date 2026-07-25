plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "io.github.lucf15.tiffrenderer"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    publishing {
        // Consumed by JitPack's `./gradlew :lib:publishToMavenLocal` (see jitpack.yml) —
        // JitPack derives the actual consumer coordinate (com.github.lucf15.TiffRenderer:lib)
        // from the repo/module itself and ignores groupId/artifactId below for that path, but
        // they're set for correctness if this ever also gets published to Maven Central.
        singleVariant("release") {
            withSourcesJar()
        }
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                // Keep only baseline + zlib/deflate codecs (built from libtiff's own bundled
                // sources / the NDK's system libz) — see CMakeLists.txt for why JPEG, WebP, LERC,
                // Zstd, LZMA and JBIG codecs are disabled for now.
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "consumer-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// AGP only creates the "release" component once the variant API has finished evaluating.
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "io.github.lucf15"
                artifactId = "tiffrenderer"
                // JitPack sets $VERSION to the requested tag; falls back for local testing.
                version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"
            }
        }
    }
}
