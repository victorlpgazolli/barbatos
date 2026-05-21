package rpc

import kotlinx.serialization.json.JsonObject

interface McpTool {
    val name: String
    val description: String
    val inputSchema: JsonObject
    fun execute(args: JsonObject?): McpCallToolResult
}
