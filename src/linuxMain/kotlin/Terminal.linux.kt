import platform.posix.termios
import kotlinx.cinterop.set
import platform.posix.ECHO
import platform.posix.ICANON
import platform.posix.ICRNL
import platform.posix.IEXTEN
import platform.posix.ISIG
import platform.posix.IXON
import platform.posix.VMIN

actual val TIOCGWINSZ: ULong = 0x5413u

actual fun termios.enableRawModePlatformFlags() {
    c_iflag = c_iflag and (ICRNL or IXON).toUInt().inv()
    c_lflag = c_lflag and (ECHO or ICANON or ISIG or IEXTEN).toUInt().inv()
    c_cc[VMIN] = 0u
}
