import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.STDIN_FILENO
import platform.posix.read

sealed class KeyEvent {
    data class Char(val c: kotlin.Char) : KeyEvent()
    data object Enter : KeyEvent()
    data object Tab : KeyEvent()
    data object Backspace : KeyEvent()
    data object ArrowUp : KeyEvent()
    data object ArrowDown : KeyEvent()
    data object ArrowLeft : KeyEvent()
    data object ArrowRight : KeyEvent()
    data object Esc : KeyEvent()
    data object Delete : KeyEvent()
    data object OptionBackspace : KeyEvent()
    data object OptionLeft : KeyEvent()
    data object OptionRight : KeyEvent()
    data object CmdLeft : KeyEvent() // Home
    data object CmdRight : KeyEvent() // End
    data object CmdBackspace : KeyEvent()
    data object CtrlA : KeyEvent()
    data object CtrlE : KeyEvent()
    data object CtrlC : KeyEvent()
    data object MouseScrollUp : KeyEvent()
    data object MouseScrollDown : KeyEvent()
    data object Timeout : KeyEvent()
    data object Unknown : KeyEvent()
}

object InputHandler {
    private fun readByte(): Int {
        val buffer = ByteArray(1)
        val bytesRead = buffer.usePinned { pinned ->
            read(STDIN_FILENO, pinned.addressOf(0), 1u)
        }
        if (bytesRead <= 0) return -1
        return buffer[0].toInt() and 0xFF
    }

    fun readKey(): KeyEvent {
        val c = readByte()
        if (c == -1) return KeyEvent.Timeout

        return when (c) {
            1 -> KeyEvent.CtrlA // Ctrl-A
            3 -> KeyEvent.CtrlC
            5 -> KeyEvent.CtrlE // Ctrl-E
            9 -> KeyEvent.Tab
            10, 13 -> KeyEvent.Enter
            21 -> KeyEvent.CmdBackspace // Ctrl-U
            23 -> KeyEvent.OptionBackspace // Ctrl-W
            127 -> KeyEvent.Backspace
            27 -> {
                val next = readByte()
                if (next == -1) {
                    KeyEvent.Esc
                } else if (next == '['.code) {
                    var b = readByte()
                    var seqStr = ""
                    if (b == '<'.code) {
                        // SGR mouse mode
                        var mouseSeq = ""
                        var lastChar = ' '
                        while (true) {
                            val nextByte = readByte()
                            if (nextByte == -1) break
                            val c = nextByte.toChar()
                            if (c == 'm' || c == 'M') {
                                lastChar = c
                                break
                            }
                            mouseSeq += c
                        }
                        val parts = mouseSeq.split(';')
                        if (parts.size >= 1) {
                            val button = parts[0].toIntOrNull() ?: 0
                            return when (button) {
                                64 -> KeyEvent.MouseScrollUp
                                65 -> KeyEvent.MouseScrollDown
                                else -> KeyEvent.Unknown
                            }
                        }
                        return KeyEvent.Unknown
                    }
                    while (b != -1 && b < 0x40) {
                        seqStr += b.toChar()
                        b = readByte()
                    }
                    when (b) {
                        'A'.code -> KeyEvent.ArrowUp
                        'B'.code -> KeyEvent.ArrowDown
                        'C'.code -> {
                            when {
                                seqStr.contains("9") || seqStr.contains("10") -> KeyEvent.CmdRight
                                seqStr.contains("3") || seqStr.contains("5") -> KeyEvent.OptionRight
                                else -> KeyEvent.ArrowRight
                            }
                        }
                        'D'.code -> {
                            when {
                                seqStr.contains("9") || seqStr.contains("10") -> KeyEvent.CmdLeft
                                seqStr.contains("3") || seqStr.contains("5") -> KeyEvent.OptionLeft
                                else -> KeyEvent.ArrowLeft
                            }
                        }
                        'H'.code -> KeyEvent.CmdLeft // Home
                        'F'.code -> KeyEvent.CmdRight // End
                        '~'.code -> when {
                            seqStr == "3" || seqStr.endsWith(";3") -> KeyEvent.Delete
                            seqStr == "1" || seqStr == "7" -> KeyEvent.CmdLeft // Home
                            seqStr == "4" || seqStr == "8" -> KeyEvent.CmdRight // End
                            else -> KeyEvent.Unknown
                        }
                        else -> KeyEvent.Unknown
                    }
                } else if (next == 'O'.code) {
                    when (readByte()) {
                        'H'.code -> KeyEvent.CmdLeft
                        'F'.code -> KeyEvent.CmdRight
                        else -> KeyEvent.Unknown
                    }
                } else if (next == 127 || next == 8) {
                    KeyEvent.OptionBackspace
                } else if (next == 'b'.code) {
                    KeyEvent.OptionLeft
                } else if (next == 'f'.code) {
                    KeyEvent.OptionRight
                } else {
                    KeyEvent.Unknown
                }
            }
            else -> {
                if (c in 32..126) {
                    KeyEvent.Char(c.toChar())
                } else {
                    KeyEvent.Unknown
                }
            }
        }
    }
}
