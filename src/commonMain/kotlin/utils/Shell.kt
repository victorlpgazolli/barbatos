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
            // On Unix-like systems, pclose returns the exit status in the high byte (bits 8-15)
            // when the process exited normally. This aligns with WEXITSTATUS(status).
            val exitCode = if (status != -1) (status shr 8) and 0xFF else -1
            return ShellResult(result.toString(), exitCode)
        } catch (e: Exception) {
            pclose(pipe)
            return ShellResult(result.toString(), -1)
        }
    }
}
