import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fputs

object HistoryStore {
    private const val MAX_HISTORY_ENTRIES = 500

    private fun filePath(): String = "${CacheManager.cacheDir()}/history.txt"

    fun load(): MutableList<String> {
        CacheManager.ensureCacheDir()
        val path = filePath()
        val file = fopen(path, "r") ?: return mutableListOf()
        val lines = mutableListOf<String>()
        val buffer = ByteArray(4096)
        while (fgets(buffer.refTo(0), buffer.size, file) != null) {
            lines.add(buffer.toKString().trimEnd('\n', '\r'))
        }
        fclose(file)
        return lines
    }

    fun append(command: String) {
        CacheManager.ensureCacheDir()
        val path = filePath()
        val file = fopen(path, "a") ?: return
        fputs("$command\n", file)
        fclose(file)
    }

    fun save(commands: List<String>) {
        CacheManager.ensureCacheDir()
        val path = filePath()
        val file = fopen(path, "w") ?: return
        val content = commands.takeLast(MAX_HISTORY_ENTRIES).joinToString("\n")
        fputs(content, file)
        fclose(file)
    }

    fun clear() {
        CacheManager.ensureCacheDir()
        val path = filePath()
        val file = fopen(path, "w") ?: return
        fclose(file)
    }
}