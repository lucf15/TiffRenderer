import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":sample:shared"))
            implementation(project(":lib:core"))
            implementation(compose.ui)
        }
        wasmJsMain {
            resources.srcDir(project(":lib:native").layout.buildDirectory.dir("wasm"))
        }
    }
}

tasks.named("wasmJsProcessResources") {
    dependsOn(project(":lib:native").tasks.named("buildTiffCoreForWasm"))
}
