package utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

data class ShellResult(val output: String, val exitCode: Int)

object Shell {
    @OptIn(ExperimentalForeignApi::class)
    fun execute(command: String, redirectStderr: Boolean = true): ShellResult {
        val result = StringBuilder()
        val fullCommand = if (redirectStderr) "$command 2>&1" else command
        val pipe = popen(fullCommand, "r") ?: return ShellResult("", -1)
        try {
            val buffer = ByteArray(1024)
            while (fgets(buffer.refTo(0), buffer.size, pipe) != null) {
                result.append(buffer.toKString())
            }
            val status = pclose(pipe)
            val exitCode = if (status == -1) {
                -1
            } else {
                // On most Unix systems, the exit status is in the high byte.
                // We shift by 8 to get the 0-255 value.
                (status shr 8) and 0xFF
            }
            return ShellResult(result.toString(), exitCode)
        } catch (e: Exception) {
            pclose(pipe)
            return ShellResult(result.toString(), -1)
        }
    }
}
