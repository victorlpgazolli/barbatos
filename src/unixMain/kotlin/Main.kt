import bridge.NativeFridaBridge
import mcp.McpHandler
import platform.posix.fprintf
import platform.posix.stderr
import rpc.RpcHandler

fun main(args: Array<String>) {
    val isMock = args.contains("--mock")
    val bridge = if (isMock) {
        fprintf(stderr, "Using MockFridaBridge (Simulation Mode)\n")
        error("invalid state - mock not implemented")
    } else {
        NativeFridaBridge()
    }
    
    if (args.contains("mcp") || args.contains("--mcp")) {
        // IMPORTANT: Redirect all system logs to stderr to keep stdout clean for JSON-RPC
        fprintf(stderr, "Starting Barbatos MCP Server (Stdio Mode)...\n")
        fprintf(stderr, "Note: This transport is synchronous/blocking in v1.\n")
        val rpcHandler = RpcHandler(bridge)

        val mcpHandler = McpHandler {
            rpcHandler.processMethod(it.name, it.arguments)
        }

        while (true) {
            val line = readlnOrNull() ?: break
            if (line.isBlank()) continue
            
            fprintf(stderr, ">> %s\n", line)
            platform.posix.fflush(platform.posix.stderr)
            
            try {
                val response = mcpHandler.handle(line)
                if (response != null) {
                    fprintf(stderr, "<< %s\n", response)
                    platform.posix.fflush(platform.posix.stderr)
                    println(response)
                    platform.posix.fflush(platform.posix.stdout)
                }
            } catch (e: Exception) {
                val error = """{"jsonrpc": "2.0", "error": {"code": -32603, "message": "Fatal: ${e.message}"}, "id": null}"""
                println(error)
                platform.posix.fflush(platform.posix.stdout)
                fprintf(stderr, "MCP Loop Error: %s\n", e.message)
                platform.posix.fflush(platform.posix.stderr)
            }
        }
    } else {
        println("Starting KMP Bridge (HTTP Mode) on port 8080...")
        try {
            startServer(bridge)
        } catch (e: Exception) {
            println("[SERVER] fatal error: ${e.message}")
            bridge.close()
        }
    }
}
