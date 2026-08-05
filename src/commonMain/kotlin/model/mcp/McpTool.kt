package model.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import model.actions.ActionDescriptor

@Serializable
data class McpToolsListResult(val tools: List<ActionDescriptor>)

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