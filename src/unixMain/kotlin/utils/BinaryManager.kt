package utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

object BinaryManager {
    fun mapArch(uname: String): String = ""
    @OptIn(ExperimentalForeignApi::class)
    fun getLocalPath(name: String, arch: String, ext: String): String = ""
}
