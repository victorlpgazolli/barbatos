package device

import utils.Shell

object AndroidEnv {
    fun getFrontmostApp(serial: String): Pair<String, Int> {
        val base = if (serial.isEmpty()) "adb" else "adb -s $serial"
        // Updated regex to be more robust for different Android versions
        val pkgCmd = "$base shell \"dumpsys window | grep -E 'mCurrentFocus' | xargs | cut -d' ' -f3 | cut -d'/' -f1\""
        val pkg = Shell.execute(pkgCmd).output.trim().replace("}", "")
        if (pkg.isEmpty()) throw Exception("Could not detect frontmost package")

        val pidCmd = "$base shell pidof $pkg"
        val pidOutput = Shell.execute(pidCmd).output.trim()
        val pid = pidOutput.split("\\s+".toRegex()).firstOrNull()?.toIntOrNull()
            ?: throw Exception("Could not find PID for $pkg (output: '$pidOutput')")

        return pkg to pid
    }

    fun isRooted(serial: String): Boolean {
        val base = if (serial.isEmpty()) "adb" else "adb -s $serial"
        val result = Shell.execute("$base shell su -c id")
        return result.output.contains("uid=0")
    }

    fun isDebuggable(serial: String, packageName: String): Boolean {
        val base = if (serial.isEmpty()) "adb" else "adb -s $serial"
        // Functional probe: run-as only succeeds for debuggable packages on non-rooted/user builds.
        // (dumpsys package | grep debuggable is a tautological check and unreliable across OEM
        // dumpsys formats, e.g. MIUI/Android 15 — see healthCheck's android_frontmost_app check.)
        val result = Shell.execute("$base shell run-as $packageName id")
        return result.output.contains("uid=")
    }
}
