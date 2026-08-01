package model.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject


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
data class McpContent(
    val type: String, // "text", "image", "resource"
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)


val mcpJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
