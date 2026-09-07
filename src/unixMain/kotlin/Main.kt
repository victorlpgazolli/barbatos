import bridge.NativeFridaBridge
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration

fun main(args: Array<String>) {
    KotlinLoggingConfiguration.logStartupMessage = false

    val bridge = NativeFridaBridge()

    val isMcp = args.contains("mcp") || args.contains("--mcp")
    if (isMcp.not()) {
        println("Starting KMP Bridge (HTTP Mode) on port 8080...")
        try {
            startServer(bridge)
        } catch (e: Exception) {
            println("[SERVER] fatal error: ${e.message}")
            bridge.close()
        }
        return
    }


    val mcpServer = createMcpServer(bridge)

    try {
        startServer(mcpServer)
    } catch (e: Exception) {
        println("[SERVER] fatal error: ${e.message}")
        bridge.close()
    }

}
