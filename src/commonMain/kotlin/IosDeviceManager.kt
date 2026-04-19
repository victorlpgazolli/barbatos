object IosDeviceManager {
    fun getConnectedDevices(): List<DeviceInfo> {
        val devices = mutableListOf<DeviceInfo>()
        val udidOutput = Shell.execute("idevice_id -l")
        
        udidOutput.trim().lines().forEach { udid ->
            if (udid.isNotBlank()) {
                val nameOutput = Shell.execute("ideviceinfo -u $udid -k DeviceName").trim()
                val name = if (nameOutput.isNotBlank()) nameOutput else "iOS Device"
                devices.add(DeviceInfo(serial = udid, model = name, status = "iOS"))
            }
        }
        
        return devices
    }
}
