// src/commonMain/kotlin/rpc/tools/ListClassesTool.kt
package rpc.tools

import bridge.FridaBridge
import ext.collectListClasses
import kotlinx.serialization.json.*
import rpc.*

class ListClassesTool(private val bridge: FridaBridge) : McpTool {
    override val name = "list_classes"
    override val description = "List loaded Java/ObjC classes in the target process."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("search_param") { put("type", "string"); put("description", "Filter classes by name") }
        }
    }

    override fun execute(args: JsonObject?): McpCallToolResult {
        val search = args?.get("search_param")?.jsonPrimitive?.content ?: ""

        val collected = mutableListOf<String>()

        bridge.collectListClasses(search, "", 0, 200) { classInfo ->
            val line = classInfo.toString()
            collected += line
        }

        return McpCallToolResult(listOf(McpContent("text", collected.joinToString("\n"))))
    }
}
