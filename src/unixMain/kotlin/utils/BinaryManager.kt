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

        val url = "https://github.com/frida/frida/releases/download/$FRIDA_VERSION/$name-$FRIDA_VERSION-android-$arch.$extension"
        val xzPath = "$localPath.xz"
        
        println("[BinaryManager] Downloading $url...")
        val response = client.get(url)
        val bytes = response.readRawBytes()
        
        val xzFile = fopen(xzPath, "wb")
        if (xzFile == null) throw Exception("Failed to open $xzPath for writing")
        
        try {
            if (bytes.isNotEmpty()) {
                fwrite(bytes.refTo(0), 1u, bytes.size.toULong(), xzFile)
            }
        } finally {
            fclose(xzFile)
        }

        println("[BinaryManager] Decompressing $xzPath...")
        val xzResult = Shell.execute("xz -d $xzPath")
        if (xzResult.exitCode != 0) {
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
