package mcp

import kotlinx.cinterop.*
import platform.posix.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StdioTransport {
    @OptIn(ExperimentalForeignApi::class)
    fun readMessages(): Flow<String> = flow {
        val buffer = ByteArray(4096)
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
                            emit(pinned.get().toKString().trim())
                        } else if (bytesRead == 0L) {
                            running = false
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun writeMessage(message: String) {
        val bytes = (message + "\n").encodeToByteArray()
        bytes.usePinned { pinned ->
            write(STDOUT_FILENO, pinned.addressOf(0), bytes.size.toULong())
        }
    }
}
