@file:OptIn(ExperimentalForeignApi::class)

import platform.posix.popen
import platform.posix.fgets
import platform.posix.pclose
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.cinterop.ExperimentalForeignApi

object Shell {
    fun execute(command: String): String {
        val result = StringBuilder()
        val pipe = popen("$command 2>/dev/null", "r")
        if (pipe == null) {
            return ""
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
}
