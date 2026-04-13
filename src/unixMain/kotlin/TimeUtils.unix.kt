import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.clock_gettime
import platform.posix.clockid_t
import platform.posix.timespec

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long {
    memScoped {
        val ts = alloc<timespec>()
        clock_gettime(0.convert<clockid_t>(), ts.ptr)
        return ts.tv_sec * 1000L + ts.tv_nsec / 1_000_000L
    }
}