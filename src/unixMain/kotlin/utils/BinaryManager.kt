package utils

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import platform.posix.*
import kotlinx.cinterop.*

object BinaryManager {
    private val client = HttpClient()
    private const val FRIDA_VERSION = "17.9.1"

    fun mapArch(uname: String): String = when {
        uname.contains("arm64") || uname.contains("aarch64") -> "arm64"
        uname.contains("arm") -> "arm"
        uname.contains("x86_64") -> "x86_64"
        uname.contains("i686") || uname.contains("i386") || uname.contains("x86") -> "x86"
        else -> "unknown"
    }

    @OptIn(ExperimentalForeignApi::class)
    fun getLocalPath(name: String, arch: String, ext: String): String {
        val home = getenv("HOME")?.toKString() ?: "/tmp"
        return "$home/.cache/barbatos/$name-$arch.$ext"
    }

    @OptIn(ExperimentalForeignApi::class)
    suspend fun ensureBinary(name: String, arch: String, extension: String): String {
        val localPath = getLocalPath(name, arch, extension)
        
        // Check if file exists
        val statBuf = nativeHeap.alloc<stat>()
        if (stat(localPath, statBuf.ptr) == 0) {
            return localPath
        }

        // Ensure directory exists
        val home = getenv("HOME")?.toKString() ?: "/tmp"
        val dir = "$home/.cache/barbatos"
        Shell.execute("mkdir -p $dir")

        // Frida only publishes xz-compressed release assets. Callers pass the extension of the
        // FINAL (decompressed) local file (e.g. "so" for the gadget), which doesn't necessarily
        // match the remote asset name — the remote file always has an extra ".xz" suffix, unless
        // the caller already accounts for it (e.g. frida-server's extension is "xz" itself).
        val remoteExtension = if (extension.endsWith("xz")) extension else "$extension.xz"
        val url = "https://github.com/frida/frida/releases/download/$FRIDA_VERSION/$name-$FRIDA_VERSION-android-$arch.$remoteExtension"
        val xzPath = "$localPath.xz"

        println("[BinaryManager] Downloading $url...")
        val response = client.get(url)
        val bytes = response.readRawBytes()

        // Validate the response before caching anything: a non-2xx status (e.g. 404) or a
        // suspiciously tiny body means we got an error page/text, not the real binary. Writing
        // that to disk would permanently poison the cache with a file that always fails to
        // decompress on every subsequent run.
        if (!response.status.value.let { it in 200..299 }) {
            throw Exception("Download failed: HTTP ${response.status.value} for $url")
        }
        if (bytes.size < 1024) {
            throw Exception(
                "Download failed: response for $url was only ${bytes.size} bytes, " +
                    "expected a real binary (got: ${bytes.decodeToString().take(200)})"
            )
        }

        val xzFile = fopen(xzPath, "wb")
        if (xzFile == null) throw Exception("Failed to open $xzPath for writing")

        try {
            fwrite(bytes.refTo(0), 1u, bytes.size.toULong(), xzFile)
        } finally {
            fclose(xzFile)
        }

        println("[BinaryManager] Decompressing $xzPath...")
        val xzResult = Shell.execute("xz -d $xzPath")
        if (xzResult.exitCode != 0) {
            // Clean up the bad archive so it doesn't get mistaken for a valid cache entry later.
            remove(xzPath)
            // Try fallback to 'unzip' or similar if xz is missing?
            // For now, assume xz is present on unix systems as per plan.
            if (xzResult.output.contains("not found")) {
                throw Exception("xz command not found. Please install xz-utils.")
            }
            throw Exception("Decompression failed: ${xzResult.output}")
        }

        return localPath
    }
}
