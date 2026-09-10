import bridge.NativeFridaBridge
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration

fun main(args: Array<String>) {
    KotlinLoggingConfiguration.logStartupMessage = false

    when {
        args.contains("mcp") || args.contains("--mcp") -> runMcp()
        args.contains("rpc") || args.contains("--rpc") -> runRpc()
        else -> printHelp()
    }
}

private fun runMcp() {
    val bridge = NativeFridaBridge()
    try {
        startServer(createMcpServer(bridge))
    } catch (e: Exception) {
        println("[SERVER] fatal error: ${e.message}")
        bridge.close()
    }
}

private fun runRpc() {
    val bridge = NativeFridaBridge()
    println("Starting Barbatos (HTTP REST mode) on port 8080...")
    try {
        startServer(bridge)
    } catch (e: Exception) {
        println("[SERVER] fatal error: ${e.message}")
        bridge.close()
    }
}

private fun printHelp() {
    println(
        """
        Barbatos — Android Frida bridge

        USAGE
          barbatos [mode]

        MODES
          (no arguments)   Show this help
          mcp              Serve the Model Context Protocol (MCP) over Streamable HTTP
                           ->  http://127.0.0.1:8080/mcp
                           For MCP clients: opencode, Claude Desktop, Cursor, MCP Inspector.
          rpc              Serve the HTTP REST API (one endpoint per method)
                           ->  POST http://127.0.0.1:8080/v0/api/<snake_case>
                           (also GET /ping, /docs, /openapi.yaml)
                           For curl, scripts and REST-based tooling.
          help | --help | -h
                           Show this help

        NOTES
          * A device must be connected via adb with a debuggable app in the foreground.
          * Both modes listen on 127.0.0.1:8080 and are mutually exclusive
            (starting one kills whatever occupies the port).
        """.trimIndent()
    )
}