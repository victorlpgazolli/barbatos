package bridge

import utils.Shell

class AdbManagerImpl : AdbManager {
    private fun adb(serial: String?, vararg args: String): String {
        val base = if (serial != null && serial.isNotEmpty()) "adb -s $serial" else "adb"
        val cmd = "$base ${args.joinToString(" ")}"
        val result = Shell.execute(cmd)
        if (result.exitCode != 0) {
            throw Exception("ADB command failed: $cmd\nOutput: ${result.output}")
        }
        return result.output
    }

    override fun listDevices(): List<String> {
        val result = Shell.execute("adb devices")
        return result.output.lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split("\\s+".toRegex())[0] }
    }

    override fun installApk(serial: String, apkPath: String): String =
        adb(serial, "install", apkPath)

    override fun uninstallApk(serial: String, packageName: String): String =
        adb(serial, "uninstall", packageName)

    override fun forwardPort(serial: String, localPort: Int, remotePort: Int): String =
        adb(serial, "forward", "tcp:$localPort", "tcp:$remotePort")

    override fun forwardJdwp(serial: String, localPort: Int, pid: Int): String =
        adb(serial, "forward", "tcp:$localPort", "jdwp:$pid")

    override fun removeForwardAll(serial: String): String =
        adb(serial, "forward", "--remove-all")

    override fun reversePort(serial: String, remotePort: Int, localPort: Int): String =
        adb(serial, "reverse", "tcp:$remotePort", "tcp:$localPort")

    override fun executeShellCommand(serial: String, command: String): String =
        adb(serial, "shell", command)

    override fun pushFile(serial: String, localPath: String, remotePath: String): String =
        adb(serial, "push", localPath, remotePath)

    override fun pullFile(serial: String, remotePath: String, localPath: String): String =
        adb(serial, "pull", remotePath, localPath)
}
