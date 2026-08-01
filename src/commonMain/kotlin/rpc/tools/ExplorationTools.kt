package rpc.tools

import model.bridge.FridaBridge
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import model.mcp.McpCallToolResult
import model.mcp.McpContent
import model.mcp.McpTool
import model.mcp.mcpJson

class CountInstancesTool(private val bridge: FridaBridge) : McpTool {
    override val name = "count_instances"
    override val description = "Count the number of active instances of a specific class."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("className") { put("type", "string"); put("description", "Full class name") }
        }
        putJsonArray("required") { add("className") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val className = args?.get("className")?.jsonPrimitive?.content ?: return McpCallToolResult(
                listOf(McpContent("text", "Missing className")),
                true
            )
            val res = bridge.countInstances(className)
            McpCallToolResult(listOf(McpContent("text", res.toString())))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class ListInstancesTool(private val bridge: FridaBridge) : McpTool {
    override val name = "list_instances"
    override val description = "List active instances of a class."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("className") { put("type", "string"); put("description", "Full class name") }
        }
        putJsonArray("required") { add("className") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val className = args?.get("className")?.jsonPrimitive?.content ?: return McpCallToolResult(
                listOf(McpContent("text", "Missing className")),
                true
            )
            val res = bridge.listInstances(className)
            McpCallToolResult(listOf(McpContent("text", mcpJson.encodeToString(res))))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class InspectInstanceTool(private val bridge: FridaBridge) : McpTool {
    override val name = "inspect_instance"
    override val description = "Inspect attributes of a specific instance."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("className") { put("type", "string"); put("description", "Full class name") }
            putJsonObject("id") { put("type", "string"); put("description", "Instance ID") }
            putJsonObject("offset") { put("type", "integer"); put("description", "Pagination offset") }
            putJsonObject("limit") { put("type", "integer"); put("description", "Pagination limit") }
        }
        putJsonArray("required") { add("className"); add("id") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val className = args?.get("className")?.jsonPrimitive?.content ?: return McpCallToolResult(
                listOf(McpContent("text", "Missing className")),
                true
            )
            val id = args?.get("id")?.jsonPrimitive?.content ?: return McpCallToolResult(
                listOf(
                    McpContent("text", "Missing id")
                ), true
            )
            val offset = args?.get("offset")?.jsonPrimitive?.intOrNull ?: 0
            val limit = args?.get("limit")?.jsonPrimitive?.intOrNull ?: 50
            val res = bridge.inspectInstance(id, offset, limit)
            McpCallToolResult(listOf(McpContent("text", mcpJson.encodeToString(res))))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class GetInstanceAddressesTool(private val bridge: FridaBridge) : McpTool {
    override val name = "get_instance_addresses"
    override val description = "Get memory addresses of instances of a class."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("className") { put("type", "string"); put("description", "Full class name") }
        }
        putJsonArray("required") { add("className") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val className = args?.get("className")?.jsonPrimitive?.content ?: return McpCallToolResult(
                listOf(McpContent("text", "Missing className")),
                true
            )
            val res = bridge.getInstanceAddresses(className)
            McpCallToolResult(listOf(McpContent("text", mcpJson.encodeToString(res))))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}
