package mcp

import kotlinx.serialization.json.buildJsonObject
import rpc.RpcHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpHandlerTest {

    // ─── initialize ───────────────────────────────────────────────────────────

    @Test
    fun handle_initialize_returnsProtocolVersion() {
        val handler = McpHandler { buildJsonObject { } }
        val request = """{"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1"}},"id":1}"""
        val response = handler.handle(request)
        assertTrue(response!!.contains("protocolVersion"), "Should return protocolVersion")
        assertTrue(response.contains("barbatos-bridge"), "Should return server name")
    }

    // ─── parse error ──────────────────────────────────────────────────────────

    @Test
    fun handle_returnsParseError_onInvalidJson() {
        val handler = McpHandler { buildJsonObject { } }
        val response = handler.handle("not-json")
        assertTrue(response!!.contains("-32700"), "Should return parse error code -32700")
    }

    // ─── method not found ─────────────────────────────────────────────────────

    @Test
    fun handle_returnsMethodNotFound_onUnknownMethod() {
        val handler = McpHandler { buildJsonObject { } }
        val response = handler.handle("""{"jsonrpc":"2.0","method":"invalidMethod","id":1}""")
        assertTrue(response!!.contains("-32601"), "Should return method not found code -32601")
    }

    // ─── notifications (id = null) ────────────────────────────────────────────

    @Test
    fun handle_returnsNull_forNotification_withNoId() {
        val handler = McpHandler { buildJsonObject { } }
        // JSON-RPC notification: no "id" field → should return null (no response)
        val response = handler.handle("""{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}""")
        assertNull(response, "Notification should return null (no response)")
    }

    // ─── resources/list ───────────────────────────────────────────────────────

    @Test
    fun handle_resourcesList_returnsEmptyList() {
        val handler = McpHandler { buildJsonObject { } }
        val response = handler.handle("""{"jsonrpc":"2.0","method":"resources/list","id":1}""")
        assertTrue(response!!.contains("resources"), "Should return resources key")
    }

    // ─── prompts/list ─────────────────────────────────────────────────────────

    @Test
    fun handle_promptsList_returnsEmptyList() {
        val handler = McpHandler { buildJsonObject { } }
        val response = handler.handle("""{"jsonrpc":"2.0","method":"prompts/list","id":1}""")
        assertTrue(response!!.contains("prompts"), "Should return prompts key")
    }

    // ─── tools/list ───────────────────────────────────────────────────────────

    @Test
    fun handle_toolsList_returnsRegisteredTools() {
        val handler = McpHandler { buildJsonObject { } }
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/list","id":1}""")
        RpcHandler.tools.forEach {
            assertTrue(response!!.contains(it.name), "Should list ${it.name} tool")
        }
    }

    @Test
    fun handle_toolsList_returnsEmptyList_whenNoToolsRegistered() {
        val handler = McpHandler { buildJsonObject { } }
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/list","id":1}""")
        assertTrue(response!!.contains("tools"), "Should return tools key")
        assertTrue(response.contains("[]"), "Should return empty tools list")
    }

    // ─── tools/call ───────────────────────────────────────────────────────────

    @Test
    fun handle_toolsCall_returnsToolNotFound_forUnknownTool() {
        val handler = McpHandler { buildJsonObject { } }
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"unknown_tool","arguments":{}},"id":1}""")
        assertTrue(response!!.contains("Tool not found"), "Should return Tool not found message")
    }

    @Test
    fun handle_toolsCall_returnsMissingParams_whenParamsAbsent() {
        val handler = McpHandler { buildJsonObject { } }
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/call","id":1}""")
        assertTrue(response!!.contains("Missing params"), "Should return Missing params error")
    }

    // ─── specific tool executions ─────────────────────────────────────────────

    @Test
    fun handle_hookMethodTool_returnsHookedMessage() {
        var callCount = 0
        val handler = McpHandler {
            callCount++
            buildJsonObject {  }
        }
        handler.handle("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"${RpcHandler.HOOK_METHOD.name}","arguments":{"className":"com.example.MainActivity","methodSig":"onCreate(android.os.Bundle)"}},"id":1}""")
        assertEquals(callCount, 1, "Tool should be called once")
    }
}