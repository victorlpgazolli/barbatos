import server.startServer
import bridge.NativeFridaBridge
import rpc.*
import rpc.tools.*
import platform.posix.fprintf
import platform.posix.stderr

fun main(args: Array<String>) {
    val bridge = NativeFridaBridge()
    
    if (args.contains("mcp") || args.contains("--mcp")) {
        // IMPORTANT: Redirect all system logs to stderr to keep stdout clean for JSON-RPC
        fprintf(stderr, "Starting Barbatos MCP Server (Stdio Mode)...\n")
        fprintf(stderr, "Note: This transport is synchronous/blocking in v1.\n")
        
        // Register tools
        val tools = listOf(
            ListClassesTool(bridge),
            InspectClassTool(bridge)
        )
        val mcpHandler = McpHandler(tools)
        
        while (true) {
            // Using readlnOrNull (modern Kotlin Native equivalent)
            val line = readlnOrNull() ?: break
            if (line.isBlank()) continue
            
            try {
                val response = mcpHandler.handle(line)
                // Guaranteed that only JSON goes to stdout
                println(response)
            } catch (e: Exception) {
                // Last line of defense for fatal crashes
                println("""{"jsonrpc": "2.0", "error": {"code": -32603, "message": "Fatal: ${e.message}"}, "id": null}""")
            }
        }
    } else {
        println("Starting KMP Bridge (HTTP Mode) on port 8080...")
        startServer(bridge)
    }
}
