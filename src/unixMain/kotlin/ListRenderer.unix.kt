
actual fun ListRenderer.selectionPrefix(isSelected: Boolean, indent: String): String {
    return if (isSelected) "$indent${Ansi.BRAND_BLUE}› ${Ansi.RESET}" else "$indent  "
}
actual fun ListRenderer.renderScrollIndicator(
    buf: StringBuilder,
    startIdx: Int,
    endIdx: Int,
    totalItems: Int,
    width: Int
) {
    if (totalItems == 0) return
    val indicator = "[ ${startIdx + 1}-$endIdx / $totalItems ]"
    val padding = maxOf(0, width - indicator.length - 2)
    buf.append(" ".repeat(padding)).append(Ansi.DIM).append(indicator).append(Ansi.RESET).append("\n")
}