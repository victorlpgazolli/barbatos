package mcp
import kotlinx.serialization.json.*

class McpServer {
    fun processRequest(requestJson: String): String {
        val json = try {
            Json.parseToJsonElement(requestJson).jsonObject
        } catch (e: Exception) {
            return """{"jsonrpc":"2.0","error":{"code":-32700,"message":"Parse error"}}"""
        }

        val id = json["id"]
        val method = json["method"]?.jsonPrimitive?.content
        
        return when (method) {
            "initialize" -> buildJsonObject {
                put("jsonrpc", "2.0")
                if (id != null) put("id", id)
                put("result", buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
                    put("serverInfo", buildJsonObject { put("name", "barbatos-bridge-kmp"); put("version", "1.0.0") })
                })
            }.toString()
            
            "tools/list" -> buildJsonObject {
                put("jsonrpc", "2.0")
                if (id != null) put("id", id)
                put("result", buildJsonObject {
                    put("tools", buildJsonArray {
                        add(buildJsonObject {
                            put("name", "barbatos_list_classes")
                            put("description", "Retrieves loaded Java classes in the target process.")
                            put("inputSchema", buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("search_param", buildJsonObject { put("type", "string") })
                                })
                            })
                        })
                    })
                })
            }.toString()
            
            "tools/call" -> buildJsonObject {
                put("jsonrpc", "2.0")
                if (id != null) put("id", id)
                put("result", buildJsonObject {
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "Tool executed")
                        })
                    })
                })
            }.toString()
            
            else -> buildJsonObject {
                put("jsonrpc", "2.0")
                if (id != null) put("id", id)
                put("error", buildJsonObject {
                    put("code", -32601)
                    put("message", "Method not found")
                })
            }.toString()
        }
    }
}
