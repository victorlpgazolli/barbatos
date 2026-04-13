import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.toKString
import platform.posix.F_OK
import platform.posix.access
import platform.posix.getenv
import platform.posix.mkdir

object CacheManager {
    private const val CACHE_DIR_NAME = "barbatos"

    fun cacheDir(): String {
        val home = getenv("HOME")?.toKString() ?: "/tmp"
        return "$home/.cache/$CACHE_DIR_NAME"
    }

    @OptIn(UnsafeNumber::class)
    fun ensureCacheDir() {
        val path = cacheDir()
        if (access(path, F_OK) != 0) {
            mkdir(path, 0b111111101u)
        }
    }
}
