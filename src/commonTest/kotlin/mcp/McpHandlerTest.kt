package mcp

import bridge.FakeFridaBridge
import rpc.tools.CountInstancesTool
import rpc.tools.HealthCheckTool
import rpc.tools.HookMethodTool
import rpc.tools.InspectClassTool
import rpc.tools.ListClassesTool
import rpc.tools.SetFieldValueTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpHandlerTest {

    // ─── initialize ───────────────────────────────────────────────────────────

    @Test
    fun handle_initialize_returnsProtocolVersion() {
        val handler = McpHandler(emptyList())
        val request = """{"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1"}},"id":1}"""
        val response = handler.handle(request)
        assertTrue(response!!.contains("protocolVersion"), "Should return protocolVersion")
        assertTrue(response.contains("barbatos-bridge"), "Should return server name")
    }

    // ─── parse error ──────────────────────────────────────────────────────────

    @Test
    fun handle_returnsParseError_onInvalidJson() {
        val handler = McpHandler(emptyList())
        val response = handler.handle("not-json")
        assertTrue(response!!.contains("-32700"), "Should return parse error code -32700")
    }

    // ─── method not found ─────────────────────────────────────────────────────

    @Test
    fun handle_returnsMethodNotFound_onUnknownMethod() {
        val handler = McpHandler(emptyList())
        val response = handler.handle("""{"jsonrpc":"2.0","method":"invalidMethod","id":1}""")
        assertTrue(response!!.contains("-32601"), "Should return method not found code -32601")
    }

    // ─── notifications (id = null) ────────────────────────────────────────────

    @Test
    fun handle_returnsNull_forNotification_withNoId() {
        val handler = McpHandler(emptyList())
        // JSON-RPC notification: no "id" field → should return null (no response)
        val response = handler.handle("""{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}""")
        assertNull(response, "Notification should return null (no response)")
    }

    // ─── resources/list ───────────────────────────────────────────────────────

    @Test
    fun handle_resourcesList_returnsEmptyList() {
        val handler = McpHandler(emptyList())
        val response = handler.handle("""{"jsonrpc":"2.0","method":"resources/list","id":1}""")
        assertTrue(response!!.contains("resources"), "Should return resources key")
    }

    // ─── prompts/list ─────────────────────────────────────────────────────────

    @Test
    fun handle_promptsList_returnsEmptyList() {
        val handler = McpHandler(emptyList())
        val response = handler.handle("""{"jsonrpc":"2.0","method":"prompts/list","id":1}""")
        assertTrue(response!!.contains("prompts"), "Should return prompts key")
    }

    // ─── tools/list ───────────────────────────────────────────────────────────

    @Test
    fun handle_toolsList_returnsRegisteredTools() {
        val bridge = FakeFridaBridge()
        val tools = listOf(HealthCheckTool(bridge), CountInstancesTool(bridge))
        val handler = McpHandler(tools)
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/list","id":1}""")
        assertTrue(response!!.contains("health_check"), "Should list health_check tool")
        assertTrue(response.contains("count_instances"), "Should list count_instances tool")
    }

    @Test
    fun handle_toolsList_returnsEmptyList_whenNoToolsRegistered() {
        val handler = McpHandler(emptyList())
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/list","id":1}""")
        assertTrue(response!!.contains("tools"), "Should return tools key")
        assertTrue(response.contains("[]"), "Should return empty tools list")
    }

    // ─── tools/call ───────────────────────────────────────────────────────────

    @Test
    fun handle_toolsCall_returnsToolNotFound_forUnknownTool() {
        val handler = McpHandler(emptyList())
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"unknown_tool","arguments":{}},"id":1}""")
        assertTrue(response!!.contains("Tool not found"), "Should return Tool not found message")
    }

    @Test
    fun handle_toolsCall_returnsMissingParams_whenParamsAbsent() {
        val handler = McpHandler(emptyList())
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/call","id":1}""")
        assertTrue(response!!.contains("Missing params"), "Should return Missing params error")
    }

    @Test
    fun handle_toolsCall_returnsErrorContent_whenToolThrows() {
        val bridge = FakeFridaBridge(healthCheckFn = { throw RuntimeException("bridge down") })
        val handler = McpHandler(listOf(HealthCheckTool(bridge)))
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"health_check","arguments":{}},"id":1}""")
        assertTrue(response!!.contains("Execution error") || response.contains("bridge down"),
            "Should return execution error on bridge failure")
    }

    // ─── specific tool executions ─────────────────────────────────────────────

    @Test
    fun handle_listClassesTool_returnsMainActivity() {
        val bridge = FakeFridaBridge()
        val handler = McpHandler(listOf(ListClassesTool(bridge)))
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"list_classes","arguments":{"search_param":"MainActivity"}},"id":1}""")
        assertTrue(response!!.contains("com.example.MainActivity"), "Should return MainActivity")
    }

    @Test
    fun handle_inspectClassTool_returnsMethodsAndAttributes() {
        val bridge = FakeFridaBridge()
        val handler = McpHandler(listOf(InspectClassTool(bridge)))
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"inspect_class","arguments":{"className":"com.example.MainActivity"}},"id":1}""")
        assertTrue(response!!.contains("onCreate"), "Should return onCreate method")
        assertTrue(response.contains("TAG"), "Should return TAG static attribute")
        assertTrue(response.contains("mCount"), "Should return mCount instance attribute")
    }

    @Test
    fun handle_healthCheckTool_returnsStatus() {
        val bridge = FakeFridaBridge()
        val handler = McpHandler(listOf(HealthCheckTool(bridge)))
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"health_check","arguments":{}},"id":1}""")
        assertTrue(response!!.contains("status"), "Should return status")
        assertTrue(response.contains("ok"), "Should return ok status")
    }

    @Test
    fun handle_setFieldValueTool_returnsSuccessMessage() {
        val bridge = FakeFridaBridge()
        val handler = McpHandler(listOf(SetFieldValueTool(bridge)))
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"set_field_value","arguments":{"className":"com.example.MainActivity","id":"123","fieldName":"mCount","type":"int","newValue":"99"}},"id":1}""")
        assertTrue(response!!.contains("mCount set to 99"), "Should return success with new value")
    }

    @Test
    fun handle_hookMethodTool_returnsHookedMessage() {
        val bridge = FakeFridaBridge()
        val handler = McpHandler(listOf(HookMethodTool(bridge)))
        val response = handler.handle("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"hook_method","arguments":{"className":"com.example.MainActivity","methodSig":"onCreate(android.os.Bundle)"}},"id":1}""")
        assertTrue(response!!.contains("Hooked com.example.MainActivity.onCreate"), "Should return hooked message")
    }
}