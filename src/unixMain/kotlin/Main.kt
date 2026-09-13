import bridge.NativeFridaBridge
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import platform.posix.exit

fun main(args: Array<String>) {
    KotlinLoggingConfiguration.logStartupMessage = false

    val port = args.indexOf("-p").takeIf { it != -1 }?.let {
        args.getOrNull(it + 1)?.toIntOrNull()
    }

    when {
        args.contains("mcp") -> runMcp(port)
        args.contains("http") -> runHttp(port)
        args.contains("help") || args.contains("--help") || args.contains("-h") -> printHelp()
        else -> {
            printHelp()
            exit(0)
        }
    }
}

private fun runMcp(port: Int?) {
    val bridge = NativeFridaBridge()
    try {
        startServer(
            mcpServer = createMcpServer(bridge),
            port = port
        )
    } catch (e: Exception) {
        println("[SERVER] fatal error: ${e.message}")
        bridge.close()
    }
}

private fun runHttp(port: Int?) {
    val bridge = NativeFridaBridge()
    println("Starting Barbatos (HTTP REST mode) on port $port...")
    try {
        startServer(
            bridge = bridge,
            port = port
        )
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
          mcp              Serve the Model Context Protocol (MCP) over Streamable HTTP -> http://127.0.0.1:8080/mcp
               -p          Specify the port to listen on (default: 8080)

          http             Serve the HTTP REST API (one endpoint per method) -> POST http://127.0.0.1:8080/v0/api/<snake_case>
               -p          Specify the port to listen on (default: 8080)

          help | --help | -h
                           Show this help

        NOTES
          * A device must be connected via adb with a debuggable app in the foreground.
          * Both modes listen on 127.0.0.1:8080 and are mutually exclusive
            (starting one kills whatever occupies the port).
        """.trimIndent()
    )
}