object IosDeviceManager {
    fun getConnectedDevices(): List<DeviceInfo> {
        val devices = mutableListOf<DeviceInfo>()

        return try {
            val udidOutput = Shell.execute("idevice_id -l 2>&1")

            if (udidOutput.contains("command not found") || udidOutput.contains("not found")) {
                return devices
            }

            udidOutput.trim().lines().forEach { udid ->
                if (udid.isNotBlank() && !udid.contains("error")) {
                    try {
                        val nameOutput = Shell.execute("ideviceinfo -u $udid -k DeviceName 2>&1").trim()
                        val name = if (nameOutput.isNotBlank() && !nameOutput.contains("error")) nameOutput else "iOS Device"
                        devices.add(DeviceInfo(serial = udid, model = name, status = "iOS"))
                    } catch (e: Exception) {
                        // Silently skip devices that fail to get info
                    }
                }
            }
            devices
        } catch (e: Exception) {
            // If iOS tools not available, just return empty list
            devices
        }
    }
}
