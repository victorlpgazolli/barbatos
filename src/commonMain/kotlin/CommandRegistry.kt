object CommandRegistry {
    private val commands = listOf(
        Command("debug", "start a new debug session"),
        Command("exit", "quit the application"),
        Command("clear", "clear the command history"),
    )

    fun search(query: String): List<Command> {
        if (query.isEmpty()) return emptyList()
        val lower = query.lowercase()
        val startsWith = commands.filter { it.name.lowercase().startsWith(lower) }.sortedBy { it.name }
        val contains = commands.filter {
            it.name.lowercase().contains(lower) && !it.name.lowercase().startsWith(lower)
        }.sortedBy { it.name }
        return startsWith + contains
    }

    fun all(): List<Command> = commands
}