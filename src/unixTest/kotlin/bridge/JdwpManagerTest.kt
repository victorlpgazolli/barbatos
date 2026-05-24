package bridge

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class JdwpManagerTest {

    @Test
    fun testInvalidBreakOnFormat() {
        val dummyAdb = object : AdbManager {
            override fun listDevices(): List<String> = emptyList()
            override fun installApk(serial: String, apkPath: String): String = ""
            override fun uninstallApk(serial: String, packageName: String): String = ""
            override fun forwardPort(serial: String, localPort: Int, remotePort: Int): String = ""
            override fun reversePort(serial: String, remotePort: Int, localPort: Int): String = ""
            override fun executeShellCommand(serial: String, command: String): String = ""
        }

        val manager = JdwpManagerImpl(dummyAdb)
        
        // breakOn without dots should fail
        val result = manager.load(
            target = "127.0.0.1",
            port = 8700,
            libraryPath = "/tmp/lib.so",
            breakOn = "InvalidFormatNoDots",
            packageName = "com.test",
            serial = "emulator-5554"
        )
        
        assertTrue(result.isFailure)
        assertEquals("Invalid breakOn format", result.exceptionOrNull()?.message)
    }

    @Test
    fun testConnectionRefused() {
        val dummyAdb = object : AdbManager {
            override fun listDevices(): List<String> = emptyList()
            override fun installApk(serial: String, apkPath: String): String = ""
            override fun uninstallApk(serial: String, packageName: String): String = ""
            override fun forwardPort(serial: String, localPort: Int, remotePort: Int): String = ""
            override fun reversePort(serial: String, remotePort: Int, localPort: Int): String = ""
            override fun executeShellCommand(serial: String, command: String): String = ""
        }

        val manager = JdwpManagerImpl(dummyAdb)
        
        // Assuming nothing is listening on port 9999, this should fail connecting
        val result = manager.load(
            target = "127.0.0.1",
            port = 9999,
            libraryPath = "/tmp/lib.so",
            breakOn = "android.os.Handler.dispatchMessage",
            packageName = "com.test",
            serial = "emulator-5554"
        )
        
        assertTrue(result.isFailure)
    }
}
