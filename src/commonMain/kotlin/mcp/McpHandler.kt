package mcp

import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import model.mcp.McpCallToolParams
import model.mcp.McpCallToolResult
import model.mcp.McpContent
import model.mcp.McpInitializeResult
import model.mcp.McpServerInfo
import model.mcp.McpToolsListResult
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.stderr
import model.rpc.RpcError
import model.rpc.RpcErrorResponse
import model.rpc.RpcRequest
import model.rpc.RpcResponse
import rpc.RpcHandler.Companion.tools

/**
 * Custom exception to trigger the -32601 (Method not found) JSON-RPC error.
 */
class McpMethodNotFoundException(message: String) : Exception(message)

class McpHandler(
    private val execute: (McpCallToolParams) -> JsonElement,
) {
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
            "initialize" -> json.encodeToJsonElement(
                McpInitializeResult(
                    protocolVersion = "2024-11-05",
                    capabilities = buildJsonObject {
                        putJsonObject("tools") {}
                        putJsonObject("resources") {}
                        putJsonObject("prompts") {}
                    },
                    serverInfo = McpServerInfo("barbatos-bridge", "1.0.2")
                )
            )
            "notifications/initialized" -> JsonNull
            "prompts/list" -> {
                // Log to stderr so user can see it
                fprintf(stderr, "MCP: Handling prompts/list\n")
                fflush(stderr)
                buildJsonObject { putJsonArray("prompts") {} }
            }
            "resources/list" -> buildJsonObject { putJsonArray("resources") {} }
            "tools/list" -> json.encodeToJsonElement(McpToolsListResult(tools))
            "tools/call" -> {
                val decodedParams = params?.let { json.decodeFromJsonElement<McpCallToolParams>(it) }
                    ?: return json.encodeToJsonElement(
                        McpCallToolResult(
                            listOf(
                                McpContent(
                                    "text",
                                    "Missing params"
                                )
                            ), true
                        )
                    )

                return try {
                    execute(decodedParams)
                } catch (e: Exception) {
                    json.encodeToJsonElement(
                        McpCallToolResult(
                            listOf(
                                McpContent(
                                    "text",
                                    "Execution error: ${e.message}"
                                )
                            ), true
                        )
                    )
                }
            }
            else -> throw McpMethodNotFoundException("Method $method not found")
        }
    }
}
