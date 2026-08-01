package model.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

interface McpTool {
    val name: String
    val description: String
    val inputSchema: JsonObject
    fun execute(args: JsonObject?): McpCallToolResult
}


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
data class McpCallToolResult(
    val content: List<McpContent>,
    val isError: Boolean = false
)