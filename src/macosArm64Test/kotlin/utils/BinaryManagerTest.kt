package utils

import kotlin.test.Test
import kotlin.test.assertEquals
import platform.posix.getenv
import kotlinx.cinterop.toKString
import kotlinx.cinterop.ExperimentalForeignApi

class BinaryManagerTest {
    @Test
    @OptIn(ExperimentalForeignApi::class)
    fun testArchMapping() {
        val manager = BinaryManager
        assertEquals("arm64", manager.mapArch("aarch64"))
        assertEquals("arm", manager.mapArch("armv7l"))
        assertEquals("x86_64", manager.mapArch("x86_64"))
        assertEquals("x86", manager.mapArch("i686"))
        assertEquals("x86", manager.mapArch("i386"))
    }

    @Test
    @OptIn(ExperimentalForeignApi::class)
    fun testCachePath() {
        val home = getenv("HOME")?.toKString() ?: "/tmp"
        val expected = "$home/.cache/barbatos/frida-gadget-arm64.so"
        assertEquals(expected, BinaryManager.getLocalPath("frida-gadget", "arm64", "so"))
    }
}
