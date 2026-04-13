import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import platform.posix.ECHO
import platform.posix.ICANON
import platform.posix.ICRNL
import platform.posix.IEXTEN
import platform.posix.ISIG
import platform.posix.IXON
import platform.posix.SIGWINCH
import platform.posix.STDIN_FILENO
import platform.posix.TCSAFLUSH
import platform.posix.VMIN
import platform.posix.VTIME
import platform.posix.fflush
import platform.posix.ioctl
import platform.posix.memcpy
import platform.posix.signal
import platform.posix.stdout
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios
import platform.posix.winsize

object Terminal {
    private val savedBytes = ByteArray(sizeOf<termios>().toInt())
    var resized: Boolean = false

    fun enableRawMode() {
        memScoped {
            val orig = alloc<termios>()
            tcgetattr(STDIN_FILENO, orig.ptr)

            savedBytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), orig.ptr, sizeOf<termios>().toULong())
            }

            val raw = alloc<termios>()
            memcpy(raw.ptr, orig.ptr, sizeOf<termios>().toULong())

            raw.enableRawModePlatformFlags()
            raw.c_cc[VTIME] = 1u

            tcsetattr(STDIN_FILENO, TCSAFLUSH, raw.ptr)
        }

        signal(SIGWINCH, staticCFunction<Int, Unit> { _ ->
            resized = true
        })
    }

    fun disableRawMode() {
        memScoped {
            val restore = alloc<termios>()
            savedBytes.usePinned { pinned ->
                memcpy(restore.ptr, pinned.addressOf(0), sizeOf<termios>().toULong())
            }
            tcsetattr(STDIN_FILENO, TCSAFLUSH, restore.ptr)
        }
    }

    fun getSize(): Pair<Int, Int> {
        memScoped {
            val ws = alloc<winsize>()
            ioctl(STDIN_FILENO, TIOCGWINSZ, ws.ptr)
            val cols = ws.ws_col.toInt()
            val rows = ws.ws_row.toInt()
            return Pair(if (cols > 0) cols else 80, if (rows > 0) rows else 24)
        }
    }

    fun flush() {
        fflush(stdout)
    }
}

expect val TIOCGWINSZ: ULong
expect fun termios.enableRawModePlatformFlags()