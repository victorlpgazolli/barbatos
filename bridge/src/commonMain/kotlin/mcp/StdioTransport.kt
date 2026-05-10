package mcp

import kotlinx.cinterop.*
import platform.posix.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StdioTransport {
    @OptIn(ExperimentalForeignApi::class)
    fun readMessages(): Flow<String> = flow {
        val buffer = ByteArray(8192)
        var running = true
        while (running) {
            memScoped {
                val pfd = alloc<pollfd>()
                pfd.fd = STDIN_FILENO
                pfd.events = POLLIN.toShort()
                val ready = poll(pfd.ptr, 1u, 100)
                
                if (ready > 0) {
                    buffer.usePinned { pinned ->
                        val bytesRead = read(STDIN_FILENO, pinned.addressOf(0), buffer.size.toULong())
                        if (bytesRead > 0) {
                            // Extract only the actual bytes read to avoid stale data/reading past end
                            val message = pinned.get().toKString().substring(0, bytesRead.toInt()).trim()
                            if (message.isNotEmpty()) {
                                emit(message)
                            }
                        } else if (bytesRead == 0L) {
                            running = false // EOF
                        } else {
                            // Error -1: handle or break
                            running = false
                        }
                    }
                } else if (ready < 0) {
                    running = false // Poll error
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun writeMessage(message: String) {
        val bytes = (message + "\n").encodeToByteArray()
        var totalWritten = 0L
        bytes.usePinned { pinned ->
            while (totalWritten < bytes.size) {
                val written = write(STDOUT_FILENO, pinned.addressOf(totalWritten.toInt()), (bytes.size - totalWritten).toULong())
                if (written < 0) {
                    // Error - handle errno or throw
                    break
                }
                if (written == 0L) break
                totalWritten += written
            }
        }
    }
}
