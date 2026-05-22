package rpc.tools

import bridge.FridaBridge
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import rpc.*

class HealthCheckTool(private val bridge: FridaBridge) : McpTool {
    override val name = "health_check"
    override val description = "Health check for the bridge and server components."
    override val inputSchema = buildJsonObject { put("type", "object") }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val res = bridge.healthCheck()
            McpCallToolResult(listOf(McpContent("text", mcpJson.encodeToString(res))))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}
