import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen
import platform.posix.system

object TmuxManager {
    fun checkTmux(): Boolean {
        val fp = popen("which tmux 2>/dev/null", "r") ?: return false
        val buffer = ByteArray(256)
        val result = fgets(buffer.refTo(0), buffer.size, fp)
        pclose(fp)
        if (result == null) return false
        val path = buffer.toKString().trim()
        return path.isNotEmpty()
    }

    fun createSession(name: String): Boolean {
        if (!checkTmux()) return false
        val exitCode = system("tmux new-session -d -s $name $binaryPath --mode debug_entrypoint 2>/dev/null")
        return exitCode == 0
    }

    fun appendInspectWindow(className: String): Boolean {
        if (!checkTmux()) return false
        val exitCode = system("tmux split-window -h -p 70 $binaryPath --mode debug_inspect_class $className 2>/dev/null")
        return exitCode == 0
    }

    fun attachSession(name: String) {
        if (!checkTmux()) return
        Terminal.disableRawMode()
//        print(Ansi.SHOW_CURSOR)
//        print(Ansi.RESET)
        Terminal.flush()
        system("tmux attach-session -t $name 2>/dev/null")
        Terminal.enableRawMode()
    }

    fun sessionExists(name: String): Boolean {
        if (!checkTmux()) return false
        val exitCode = system("tmux has-session -t $name 2>/dev/null")
        return exitCode == 0
    }
}

expect val binaryPath: String
