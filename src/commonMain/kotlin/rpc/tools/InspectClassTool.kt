// src/commonMain/kotlin/rpc/tools/InspectClassTool.kt
package rpc.tools

import model.bridge.FridaBridge
import kotlinx.serialization.json.*
import model.mcp.McpCallToolResult
import model.mcp.McpContent
import model.mcp.McpTool

class InspectClassTool(private val bridge: FridaBridge) : McpTool {
    override val name = "inspect_class"
    override val description = "Inspect fields and methods of a specific class."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("className") { put("type", "string"); put("description", "Full class name") }
        }
        putJsonArray("required") { add("className") }
    }

    override fun execute(args: JsonObject?): McpCallToolResult {
        val className = args?.get("className")?.jsonPrimitive?.content ?: return McpCallToolResult(
            listOf(McpContent("text", "Missing className")),
            true
        )
        val res = bridge.inspectClass(className)
        val output = """
            Methods:
            ${res.methods.joinToString("\n")}
            
            Static Attributes:
            ${res.staticAttributes.joinToString("\n")}
            
            Instance Attributes:
            ${res.instanceAttributes.joinToString("\n")}
        """.trimIndent()
        return McpCallToolResult(listOf(McpContent("text", output)))
    }
}
