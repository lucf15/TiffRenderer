package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TiffRendererNativeJvmParsingTest {

    @Test
    fun osAndLibFileName_mac_resolvesToMacos() {
        assertEquals("macos" to "libtiffrenderer_jni_jvm.dylib", osAndLibFileName("Mac OS X"))
    }

    @Test
    fun osAndLibFileName_windows_resolvesToWindows() {
        assertEquals("windows" to "tiffrenderer_jni_jvm.dll", osAndLibFileName("Windows 11"))
    }

    @Test
    fun osAndLibFileName_linux_resolvesToLinux() {
        assertEquals("linux" to "libtiffrenderer_jni_jvm.so", osAndLibFileName("Linux"))
    }

    @Test
    fun osAndLibFileName_unrecognized_throwsUnsatisfiedLinkError() {
        assertFailsWith<UnsatisfiedLinkError> { osAndLibFileName("FreeBSD") }
    }

    @Test
    fun archName_aarch64OrArm64_resolvesToAarch64() {
        assertEquals("aarch64", archName("aarch64"))
        assertEquals("aarch64", archName("arm64"))
    }

    @Test
    fun archName_x86_64OrAmd64_resolvesToX86_64() {
        assertEquals("x86_64", archName("x86_64"))
        assertEquals("x86_64", archName("amd64"))
    }

    @Test
    fun archName_unrecognized_throwsUnsatisfiedLinkError() {
        assertFailsWith<UnsatisfiedLinkError> { archName("riscv64") }
    }
}
