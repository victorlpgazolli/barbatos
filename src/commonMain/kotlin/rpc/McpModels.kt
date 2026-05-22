package rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class McpInitializeParams(
    val protocolVersion: String,
    val capabilities: JsonObject,
    val clientInfo: McpClientInfo
)

@Serializable
data class McpClientInfo(val name: String, val version: String)

@Serializable
data class McpInitializeResult(
    val protocolVersion: String,
    val capabilities: JsonObject,
    val serverInfo: McpServerInfo
)

@Serializable
data class McpServerInfo(val name: String, val version: String)

@Serializable
data class McpToolDef(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

@Serializable
data class McpToolsListResult(val tools: List<McpToolDef>)

@Serializable
data class McpCallToolParams(
    val name: String,
    val arguments: JsonObject? = null
)

@Serializable
data class McpContent(
    val type: String, // "text", "image", "resource"
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

@Serializable
data class McpCallToolResult(
    val content: List<McpContent>,
    val isError: Boolean = false
)

val mcpJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
