package rpc.tools

import bridge.FridaBridge
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import rpc.*

class SetFieldValueTool(private val bridge: FridaBridge) : McpTool {
    override val name = "set_field_value"
    override val description = "Change the value of a field in a specific instance."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("className") { put("type", "string") }
            putJsonObject("id") { put("type", "string") }
            putJsonObject("fieldName") { put("type", "string") }
            putJsonObject("type") { put("type", "string") }
            putJsonObject("newValue") { put("type", "string") }
        }
        putJsonArray("required") { add("className"); add("id"); add("fieldName"); add("type"); add("newValue") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val className = args?.get("className")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing className")), true)
            val id = args?.get("id")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing id")), true)
            val fieldName = args?.get("fieldName")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing fieldName")), true)
            val type = args?.get("type")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing type")), true)
            val newValue = args?.get("newValue")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing newValue")), true)
            
            val res = bridge.setFieldValue(className, id, fieldName, type, newValue)
            McpCallToolResult(listOf(McpContent("text", res.toString())))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class HookMethodTool(private val bridge: FridaBridge) : McpTool {
    override val name = "hook_method"
    override val description = "Intercept calls to a specific method."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("className") { put("type", "string") }
            putJsonObject("methodSig") { put("type", "string") }
        }
        putJsonArray("required") { add("className"); add("methodSig") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val className = args?.get("className")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing className")), true)
            val methodSig = args?.get("methodSig")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing methodSig")), true)
            
            val res = bridge.hookMethod(className, methodSig)
            McpCallToolResult(listOf(McpContent("text", res.toString())))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class GetHookEventsTool(private val bridge: FridaBridge) : McpTool {
    override val name = "get_hook_events"
    override val description = "Retrieve events intercepted by active hooks."
    override val inputSchema = buildJsonObject { put("type", "object") }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val res = bridge.getHookEvents()
            McpCallToolResult(listOf(McpContent("text", mcpJson.encodeToString(res))))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class SetMethodImplementationTool(private val bridge: FridaBridge) : McpTool {
    override val name = "set_method_implementation"
    override val description = "Completely replace a method's implementation."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("className") { put("type", "string") }
            putJsonObject("methodSig") { put("type", "string") }
            putJsonObject("code") { put("type", "string") }
        }
        putJsonArray("required") { add("className"); add("methodSig"); add("code") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val className = args?.get("className")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing className")), true)
            val methodSig = args?.get("methodSig")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing methodSig")), true)
            val code = args?.get("code")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing code")), true)
            
            val res = bridge.setMethodImplementation(className, methodSig, code)
            McpCallToolResult(listOf(McpContent("text", res.toString())))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class RunOnceTool(private val bridge: FridaBridge) : McpTool {
    override val name = "run_once"
    override val description = "Execute a snippet of code in the app context once."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("className") { put("type", "string") }
            putJsonObject("methodSig") { put("type", "string") }
            putJsonObject("code") { put("type", "string") }
        }
        putJsonArray("required") { add("className"); add("methodSig"); add("code") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val className = args?.get("className")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing className")), true)
            val methodSig = args?.get("methodSig")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing methodSig")), true)
            val code = args?.get("code")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing code")), true)
            
            val res = bridge.runOnce(className, methodSig, code)
            McpCallToolResult(listOf(McpContent("text", res.toString())))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}
