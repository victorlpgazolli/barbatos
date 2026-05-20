package ios

object IosRepacker {
    fun parseDeviceId(output: String): String? {
        val regex = Regex("([0-9a-fA-F-]+)\\s+\\(USB\\)")
        return regex.find(output)?.groupValues?.get(1)
    }
}