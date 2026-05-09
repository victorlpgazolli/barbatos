package ios

import utils.Shell
import platform.posix.getenv
import kotlinx.cinterop.toKString
import kotlinx.cinterop.ExperimentalForeignApi

object IosRepacker {
    fun parseDeviceId(output: String): String? {
        val lines = output.lines()
        if (lines.isEmpty() || lines[0].isBlank()) return null
        return lines[0].split(" ")[0].trim()
    }

    fun getDeviceId(): String? = parseDeviceId(Shell.execute("idevice_id -l"))

    @OptIn(ExperimentalForeignApi::class)
    fun repackAndInstall(appPath: String): Pair<String, String> {
        val deviceId = getDeviceId() ?: throw RuntimeException("No iOS device detected")
        val home = getenv("HOME")?.toKString() ?: "/tmp"
        val cachePath = "$home/.cache/frida/gadget-ios.dylib"
        
        Shell.execute("mkdir -p $appPath/Frameworks")
        Shell.execute("cp $cachePath $appPath/Frameworks/frida-gadget-ios.dylib")
        
        val bundleId = Shell.execute("plutil -extract CFBundleIdentifier raw $appPath/Info.plist").trim()
        return Pair(deviceId, bundleId)
    }
}
