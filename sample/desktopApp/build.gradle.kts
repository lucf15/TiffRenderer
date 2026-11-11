plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    implementation(project(":sample:shared"))
    implementation(compose.desktop.currentOs)
}

if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
    val nativeResources = layout.buildDirectory.dir("generated/native-resources")
    val nativeLibrary = nativeResources.map { it.file("native/macos/libtiffwindow.dylib") }
    val nativeJdk = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
    val compileMacWindowControls by tasks.registering(Exec::class) {
        val source = layout.projectDirectory.file("src/macosMain/objc/WindowControls.m")
        inputs.file(source)
        outputs.file(nativeLibrary)
        doFirst {
            val jdk = nativeJdk.get().metadata.installationPath
            nativeLibrary.get().asFile.parentFile.mkdirs()
            commandLine("/usr/bin/xcrun", "clang", "-dynamiclib", "-fobjc-arc", "-O2",
                "-mmacosx-version-min=11.0", "-framework", "Cocoa",
                "-I${jdk.dir("include")}", "-I${jdk.dir("include/darwin")}",
                source.asFile, "-o", nativeLibrary.get().asFile)
        }
    }
    sourceSets.main { resources.srcDir(nativeResources) }
    tasks.named("processResources") { dependsOn(compileMacWindowControls) }
}

compose.desktop {
    application {
        mainClass = "io.github.lucf15.tiffrenderer.sample.desktop.MainKt"
    }
}
