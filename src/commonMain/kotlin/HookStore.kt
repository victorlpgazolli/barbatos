import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fputs

object HookStore {
    private fun filePath(packageName: String): String {
        val safeName = packageName.replace(".", "_").replace(" ", "_")
        return "${CacheManager.cacheDir()}/hooks_$safeName.json"
    }

    fun load(packageName: String): Set<HookTarget> {
        if (packageName.isEmpty()) return emptySet()
        CacheManager.ensureCacheDir()
        val path = filePath(packageName)
        val file = fopen(path, "r") ?: return emptySet()

        val sb = StringBuilder()
        val buffer = ByteArray(4096)
        while (fgets(buffer.refTo(0), buffer.size, file) != null) {
            sb.append(buffer.toKString())
        }
        fclose(file)

        val content = sb.toString().trim()
        if (content.isEmpty()) return emptySet()

        return try {
            Json.Default.decodeFromString<Set<HookTarget>>(content)
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun save(packageName: String, hooks: Set<HookTarget>) {
        if (packageName.isEmpty()) return
        CacheManager.ensureCacheDir()
        val path = filePath(packageName)
        val file = fopen(path, "w") ?: return

        val json = Json { prettyPrint = true }
        val content = json.encodeToString(hooks)
        fputs(content, file)
        fclose(file)
    }
}