package utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

object Shell {
    @OptIn(ExperimentalForeignApi::class)
    fun execute(command: String): String {
        val result = StringBuilder()
        val pipe = popen("$command 2>/dev/null", "r") ?: return ""
        try {
            val buffer = ByteArray(1024)
            while (fgets(buffer.refTo(0), buffer.size, pipe) != null) {
                result.append(buffer.toKString())
            }
            return result.toString()
        } finally {
            pclose(pipe)
        }
    }
}
