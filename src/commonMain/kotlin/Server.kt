import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.request.*
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.util.logging.LogLevel
import io.ktor.util.logging.Logger
import io.ktor.utils.io.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.error
import io.modelcontextprotocol.kotlin.sdk.types.success
import rpc.RpcHandler
import model.bridge.FridaBridge
import platform.posix.system
import utils.EmbeddedScripts

fun createMcpServer(bridge: FridaBridge): Server {
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
            ) { request ->
                val result = try {
                    CallToolResult.success(
                        rpcHandler.processMethod(
                            method = tool.name,
                            params = request.arguments
                        ).toString()
                    )
                } catch (e: Exception) {
                    CallToolResult.error(
                        "${tool.name} failed: ${e.message}"
                    )
                }
                result
            }
        }
    }
    return mcpServer
}

fun Application.module(bridge: FridaBridge) {
    val rpcHandler = RpcHandler(bridge)
    println("[SERVER] Server module initialized.")
    println("[SERVER] Has embedded agent? ${EmbeddedScripts.agent.isNotEmpty()}")

    routing {
        docsRoutes()
        get("/ping") {
            call.respondText("""{"status": "pong"}""", ContentType.Application.Json)
        }
        post("/rpc") {
            val body = call.receiveText()

            if (rpcHandler.isStreamMethod(body)) {
                val ndjsonType = ContentType.parse("application/x-ndjson")
                call.respondBytesWriter(contentType = ndjsonType) {
                    try {
                        rpcHandler.handleStream(body) { line ->
                            try {
                                writeFully((line + "\n").encodeToByteArray())
                                flush()
                            } catch (e: Exception) {
                                println("[SERVER] Client disconnected ${e.message}")
                            }
                        }
                    } catch (e: Exception) {}
                }
            } else {
                val result = rpcHandler.handle(body)
                call.respondText(result.body, ContentType.Application.Json, HttpStatusCode.fromValue(result.statusCode))
            }
        }
    }
}

fun startServer(bridge: FridaBridge) {
    val port = 8080

    val cmd = $$"kill -9 $(lsof -t -i:$$port) 2>/dev/null"

    system(cmd)

    println("[SERVER] Swagger UI on http://127.0.0.1:$port/docs")

    embeddedServer(CIO, port = port, host = "127.0.0.1") {
        module(bridge)
    }.start(wait = true)
}


fun startServer(mcpServer: Server) {
    val port = 8080

    val cmd = $$"kill -9 $(lsof -t -i:$$port) 2>/dev/null"

    system(cmd)


    println("[SERVER] MCP Streamable HTTP endpoint on http://127.0.0.1:$port/mcp")

    val connector = EngineConnectorBuilder()
    connector.host = "127.0.0.1"
    connector.port = port

    embeddedServer(
        CIO,
        environment = environment,
        configure = {
            connectors.add(connector)
        },
    ) {
        mcpStreamableHttp {
            mcpServer
        }
    }.start(wait = true)
}
