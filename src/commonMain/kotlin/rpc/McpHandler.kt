package rpc

import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import rpc.model.RpcError
import rpc.model.RpcErrorResponse
import rpc.model.RpcRequest
import rpc.model.RpcResponse

/**
 * Custom exception to trigger the -32601 (Method not found) JSON-RPC error.
 */
class McpMethodNotFoundException(message: String) : Exception(message)

class McpHandler(private val tools: List<McpTool>) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun handle(requestJson: String): String? {
        val req = try {
            json.decodeFromString<RpcRequest>(requestJson)
        } catch (e: Exception) {
            return json.encodeToString(
                RpcErrorResponse(
                    error = RpcError(-32700, "Parse error"),
                    id = null
                )
            )
        }

        // If it's a notification (no id), we don't send a response
        if (req.id == null) {
            processMethod(req.method, req.params)
            return null
        }

        return try {
            val result = processMethod(req.method, req.params)
            json.encodeToString(RpcResponse(result = result, id = req.id))
        } catch (e: McpMethodNotFoundException) {
            json.encodeToString(
                RpcErrorResponse(
                    error = RpcError(
                        -32601,
                        e.message ?: "Method not found"
                    ), id = req.id
                )
            )
        } catch (e: Exception) {
            json.encodeToString(
                RpcErrorResponse(
                    error = RpcError(
                        -32603,
                        e.message ?: "Internal error"
                    ), id = req.id
                )
            )
        }
    }

    private fun processMethod(method: String, params: JsonElement?): JsonElement {
        return when (method) {
            "initialize" -> json.encodeToJsonElement(McpInitializeResult(
                protocolVersion = "2024-11-05",
                capabilities = buildJsonObject { 
                    putJsonObject("tools") {} 
                    putJsonObject("resources") {}
                    putJsonObject("prompts") {}
                },
                serverInfo = McpServerInfo("barbatos-bridge", "1.0.2")
            ))
            "notifications/initialized" -> JsonNull
            "prompts/list" -> {
                // Log to stderr so user can see it
                platform.posix.fprintf(platform.posix.stderr, "MCP: Handling prompts/list\n")
                platform.posix.fflush(platform.posix.stderr)
                buildJsonObject { putJsonArray("prompts") {} }
            }
            "resources/list" -> buildJsonObject { putJsonArray("resources") {} }
            "tools/list" -> json.encodeToJsonElement(McpToolsListResult(
                tools.map { McpToolDef(it.name, it.description, it.inputSchema) }
            ))
            "tools/call" -> {
                val p = params?.let { json.decodeFromJsonElement<McpCallToolParams>(it) }
                    ?: return json.encodeToJsonElement(McpCallToolResult(listOf(McpContent("text", "Missing params")), true))
                
                val tool = tools.find { it.name == p.name }
                    ?: return json.encodeToJsonElement(McpCallToolResult(listOf(McpContent("text", "Tool not found")), true))
                
                try {
                    json.encodeToJsonElement(tool.execute(p.arguments))
                } catch (e: Exception) {
                    json.encodeToJsonElement(McpCallToolResult(listOf(McpContent("text", "Execution error: ${e.message}")), true))
                }
            }
            else -> throw McpMethodNotFoundException("Method $method not found")
        }
    }
}
