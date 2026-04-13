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
}