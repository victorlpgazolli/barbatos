internal val SPINNER_FRAMES = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

object ListRenderer {
    fun computeViewport(totalItems: Int, selectedIndex: Int, maxVisible: Int): Pair<Int, Int> {
        if (totalItems <= maxVisible) {
            return 0 to totalItems
        }
        val start = (selectedIndex - maxVisible / 2).coerceIn(0, totalItems - maxVisible)
        val end = (start + maxVisible).coerceAtMost(totalItems)
        return start to end
    }


    fun spinnerFrame(frameCount: Int): String {
        return SPINNER_FRAMES[frameCount % SPINNER_FRAMES.size]
    }

}

expect fun ListRenderer.selectionPrefix(isSelected: Boolean, indent: String = ""): String
expect fun ListRenderer.renderScrollIndicator(buf: StringBuilder, startIdx: Int, endIdx: Int, totalItems: Int, width: Int)