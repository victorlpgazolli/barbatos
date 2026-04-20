@file:OptIn(ExperimentalForeignApi::class)

import platform.posix.popen
import platform.posix.fgets
import platform.posix.pclose
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.cinterop.ExperimentalForeignApi

object AdbDeviceManager {
    fun getConnectedDevices(): Pair<List<DeviceInfo>, String?> {
        return try {
            val output = executeAdbCommand("devices -l")
            val devices = parseDevicesOutput(output)
            Pair(devices, null)
        } catch (e: Exception) {
            Pair(emptyList(), "Failed to enumerate devices: ${e.message}")
        }
    }

    private fun executeAdbCommand(args: String): String {
        val result = StringBuilder()
        val pipe = popen("adb $args 2>/dev/null", "r")
        if (pipe == null) {
            throw Exception("Failed to execute adb command")
        }

        return try {
            val buffer = ByteArray(1024)
            while (fgets(buffer.refTo(0), buffer.size, pipe) != null) {
                result.append(buffer.toKString())
            }
            result.toString()
        } finally {
            pclose(pipe)
        }
    }

    private fun parseDevicesOutput(output: String): List<DeviceInfo> {
        val devices = mutableListOf<DeviceInfo>()
        val lines = output.split("\n").drop(1) // Skip "List of attached devices" header

        for (line in lines) {
            if (line.isBlank()) continue

            val trimmedLine = line.trim()
            val parts = trimmedLine.split("\\s+".toRegex())
            if (parts.size < 2) continue

            val serial = parts[0]
            val status = parts[1]

            // Only include "device" status (online, debuggable)
            if (status != "device") continue

            // Extract model from "model:Pixel6Pro" part
            val modelPart = parts.find { it.startsWith("model:") }
            val model = if (modelPart != null) {
                modelPart.substring(6).replace("_", " ")
            } else {
                "Unknown Device"
            }

            devices.add(DeviceInfo(serial, model, status))
        }

        return devices.sortedBy { it.serial }
    }
}
