import kotlin.text.iterator

object StringUtils {
    fun extractParams(signature: String): String {
        val open  = signature.indexOf('(')
        val close = signature.lastIndexOf(')')
        if (open == -1 || close <= open + 1) return ""
        return signature.substring(open + 1, close)
            .split(',')
            .joinToString(", ") { it.trim().substringAfterLast('.') }
    }

    fun extractMemberName(signature: String): String {
        val beforeArgs = signature.split('(')[0].trim()
        val parts = beforeArgs.split(' ')
        val fullPath = parts.last()
        return fullPath.split('.').last()
    }

    fun splitTopLevelCommas(s: String): List<String> {
        val result  = mutableListOf<String>()
        var depth   = 0
        val current = StringBuilder()
        for (ch in s) {
            when (ch) {
                '{', '[', '(' -> { depth++; current.append(ch) }
                '}', ']', ')' -> { depth--; current.append(ch) }
                ',' -> if (depth == 0) {
                    result.add(current.toString().trim())
                    current.clear()
                } else {
                    current.append(ch)
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) result.add(current.toString().trim())
        return result
    }

    fun getRandomScrambleChar(): Char {
        val chars = "!@#$%^&*()1234567890ABCDEF"
        return chars.random()
    }

    fun formatClassName(className: String): String {
        val lastDot = className.lastIndexOf('.')
        val pkg = if (lastDot != -1) className.substring(0, lastDot) else ""
        val name = if (lastDot != -1) className.substring(lastDot + 1) else className
        return if (pkg.isNotEmpty()) "$name ($pkg)" else name
    }

    fun getScrambledText(old: String, new: String, progress: Double): String {
        val maxLen = maxOf(old.length, new.length)
        val pivot = (progress * (maxLen + 3)).toInt() - 1
        val chars = "!@#$%^&*()1234567890ABCDEF"
        
        return buildString {
            for (i in 0 until maxLen) {
                when {
                    i < pivot - 1 -> append(if (i < new.length) new[i] else ' ')
                    i > pivot + 1 -> append(if (i < old.length) old[i] else ' ')
                    else -> append(chars.random())
                }
            }
        }
    }
}