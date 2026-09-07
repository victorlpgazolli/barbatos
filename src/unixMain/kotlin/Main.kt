import bridge.NativeFridaBridge
import io.ktor.client.request.invoke
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonNull.content
import mcp.McpHandler
import platform.posix.fprintf
import platform.posix.stderr
import rpc.RpcHandler

fun main(args: Array<String>) {

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


    val mcpServer = Server(
        serverInfo = Implementation(
            name = "barbatos",
            version = "2.x"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(
                    listChanged = true,
                ),
                resources = ServerCapabilities.Resources(
                    listChanged = true,
                ),
                prompts = ServerCapabilities.Prompts(
                    listChanged = true
                )
            ),
        )
    )


    val rpcHandler = RpcHandler(bridge)


    mcpServer.apply {
        RpcHandler.tools.forEach { tool ->
            addTool(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.mcpScheme,
            ) {  request ->
                CallToolResult(
                    content = listOf(
                        TextContent(
                            text = rpcHandler.processMethod(
                                method = tool.name,
                                params = request.arguments
                            ).toString()
                        )
                    )
                )

            }
        }
    }
    try {
        startServer(mcpServer)
    } catch (e: Exception) {
        println("[SERVER] fatal error: ${e.message}")
        bridge.close()
    }

}
