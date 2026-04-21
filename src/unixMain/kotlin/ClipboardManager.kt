
import platform.posix.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
object ClipboardManager {
    fun copyToClipboard(text: String): Boolean {
        val isWsl = isWsl()

        val command = when {
            isMacOs() -> "pbcopy"
            isWsl -> "clip.exe"
            hasCommand("xclip") -> "xclip -selection clipboard"
            hasCommand("xsel") -> "xsel -ib"
            else -> return false
        }

        val fp = popen(command, "w") ?: return false
        try {
            val utf8Bytes = text.encodeToByteArray()
            utf8Bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, utf8Bytes.size.toULong(), fp)
            }
        } finally {
            pclose(fp)
        }
        return true
    }

    private fun isWsl(): Boolean {
        val fp = fopen("/proc/version", "r") ?: return false
        try {
            memScoped {
                val buffer = allocArray<ByteVar>(1024)
                if (fgets(buffer, 1024, fp) != null) {
                    val versionStr = buffer.toKString()
                    return versionStr.contains("microsoft", ignoreCase = true) || 
                           versionStr.contains("WSL", ignoreCase = true)
                }
            }
        } finally {
            fclose(fp)
        }
        return false
    }

    private fun isMacOs(): Boolean {
        memScoped {
            val unameInfo = alloc<utsname>()
            if (uname(unameInfo.ptr) != 0) return false
            val sysname = unameInfo.sysname.toKString()
            return sysname.equals("Darwin", ignoreCase = true)
        }
    }
    
    private fun hasCommand(cmd: String): Boolean {
        val fp = popen("command -v $cmd", "r") ?: return false
        val result = pclose(fp)
        return result == 0
    }
}
