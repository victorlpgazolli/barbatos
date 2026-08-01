package model.device

interface JdwpManager {
    fun load(
        target: String,
        port: Int,
        libraryPath: String,
        breakOn: String? = "android.os.Handler.dispatchMessage",
        packageName: String,
        serial: String
    ): Result<Unit>
}