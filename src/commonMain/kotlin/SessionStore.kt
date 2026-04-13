import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fputs

data class SessionEntry(val id: String, val createdAt: String)

data class SessionData(val sessions: MutableList<SessionEntry> = mutableListOf())

object SessionStore {
    private fun filePath(): String = "${CacheManager.cacheDir()}/sessions.toml"

    fun load(): SessionData {
        CacheManager.ensureCacheDir()
        val path = filePath()
        val file = fopen(path, "r") ?: return SessionData()
        val lines = mutableListOf<String>()
        val buffer = ByteArray(1024)
        while (fgets(buffer.refTo(0), buffer.size, file) != null) {
            lines.add(buffer.toKString().trimEnd('\n', '\r'))
        }
        fclose(file)
        return parse(lines)
    }

    fun save(data: SessionData) {
        CacheManager.ensureCacheDir()
        val path = filePath()
        val file = fopen(path, "w") ?: return
        val content = serialize(data)
        fputs(content, file)
        fclose(file)
    }

    fun addSession(name: String, createdAt: String) {
        val data = load()
        data.sessions.add(SessionEntry(name, createdAt))
        save(data)
    }

    fun removeSession(name: String) {
        val data = load()
        data.sessions.removeAll { it.id == name }
        save(data)
    }

    private fun parse(lines: List<String>): SessionData {
        val data = SessionData()
        val ids = mutableListOf<String>()
        var currentSection = ""
        var currentCreatedAt = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("ids")) {
                val bracketStart = trimmed.indexOf('[')
                val bracketEnd = trimmed.indexOf(']')
                if (bracketStart >= 0 && bracketEnd > bracketStart) {
                    val inner = trimmed.substring(bracketStart + 1, bracketEnd)
                    ids.addAll(
                        inner.split(",")
                            .map { it.trim().trim('"').trim() }
                            .filter { it.isNotEmpty() }
                    )
                }
                continue
            }

            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                if (currentSection.isNotEmpty() && currentCreatedAt.isNotEmpty()) {
                    data.sessions.add(SessionEntry(currentSection, currentCreatedAt))
                }
                currentSection = trimmed.substring(1, trimmed.length - 1).trim()
                currentCreatedAt = ""
                continue
            }

            if (trimmed.startsWith("created_at")) {
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx >= 0) {
                    currentCreatedAt = trimmed.substring(eqIdx + 1).trim().trim('"')
                }
                continue
            }
        }

        if (currentSection.isNotEmpty() && currentCreatedAt.isNotEmpty()) {
            data.sessions.add(SessionEntry(currentSection, currentCreatedAt))
        }

        return data
    }

    private fun serialize(data: SessionData): String {
        val buf = StringBuilder()
        val quoted = data.sessions.joinToString(", ") { "\"${it.id}\"" }
        buf.append("ids = [ $quoted ]\n\n")
        for (entry in data.sessions) {
            buf.append("[${entry.id}]\n")
            buf.append("created_at = \"${entry.createdAt}\"\n\n")
        }
        return buf.toString()
    }
}
