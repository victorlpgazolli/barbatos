import kotlinx.cinterop.set
import platform.posix.ECHO
import platform.posix.ICANON
import platform.posix.ICRNL
import platform.posix.IEXTEN
import platform.posix.ISIG
import platform.posix.IXON
import platform.posix.VMIN
import platform.posix.termios

actual val TIOCGWINSZ: ULong = 0x40087468u

actual fun termios.enableRawModePlatformFlags() {
    c_iflag = c_iflag and (ICRNL or IXON).toULong().inv()
    c_lflag = c_lflag and (ECHO or ICANON or ISIG or IEXTEN).toULong().inv()
    c_cc[VMIN] = 0u
}