package bridge

interface AdbManager {
    fun listDevices(): List<String>
    fun installApk(serial: String, apkPath: String): String
    fun uninstallApk(serial: String, packageName: String): String
    fun forwardPort(serial: String, localPort: Int, remotePort: Int): String
    fun forwardJdwp(serial: String, localPort: Int, pid: Int): String
    fun removeForwardAll(serial: String): String
    fun reversePort(serial: String, remotePort: Int, localPort: Int): String
    fun executeShellCommand(serial: String, command: String): String
    fun pushFile(serial: String, localPath: String, remotePath: String): String
    fun pullFile(serial: String, remotePath: String, localPath: String): String
}