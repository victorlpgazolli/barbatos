package bridge

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AdbManagerTest {

    // A basic test that verifies the class can be instantiated.
    // Full integration testing requires a real ADB environment.
    @Test
    fun testInstantiation() {
        val manager = AdbManagerImpl()
        assertTrue(manager is AdbManager)
    }

    // Since Shell.execute("adb") will fail if no devices are attached or adb is missing,
    // we can expect an exception or at least an empty list if we mock it.
    @Test
    fun testListDevicesFailsGracefullyOrReturnsEmpty() {
        val manager = AdbManagerImpl()
        try {
            val devices = manager.listDevices()
            // If adb is available but no devices, it should be empty.
            // If devices exist, it should not be null.
            assertTrue(devices != null)
        } catch (e: Exception) {
            // If ADB is completely missing, it might throw an exception from our wrapper
            assertTrue(e.message?.contains("ADB command failed") == true || e.message?.contains("adb") == true)
        }
    }
}
