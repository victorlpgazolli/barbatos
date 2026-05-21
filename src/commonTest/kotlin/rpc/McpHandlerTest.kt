package rpc

import bridge.MockFridaBridge
import rpc.tools.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.serialization.json.*

class McpHandlerTest {
    @Test
    fun testMcpInitialize() {
        val handler = McpHandler(emptyList())
        val request = """{"jsonrpc": "2.0", "method": "initialize", "params": {"protocolVersion": "2024-11-05", "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}}, "id": 1}"""
        val response = handler.handle(request)
        assertTrue(response.contains("protocolVersion"), "Should return MCP initialization")
    }

    @Test
    fun testMethodNotFound() {
        val handler = McpHandler(emptyList())
        val request = """{"jsonrpc": "2.0", "method": "invalidMethod", "id": 1}"""
        val response = handler.handle(request)
        // Check for specific JSON-RPC error code -32601
        assertTrue(response.contains("-32601"), "Should return Method not found code (-32601)")
    }

    @Test
    fun testListClassesTool() {
        val bridge = MockFridaBridge()
        val tools = listOf(ListClassesTool(bridge))
        val handler = McpHandler(tools)
        val request = """{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "list_classes", "arguments": {"search_param": "MainActivity"}}, "id": 1}"""
        val response = handler.handle(request)
        assertTrue(response.contains("com.example.MainActivity"), "Should return MainActivity in classes list")
    }

    @Test
    fun testInspectClassTool() {
        val bridge = MockFridaBridge()
        val tools = listOf(InspectClassTool(bridge))
        val handler = McpHandler(tools)
        val request = """{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "inspect_class", "arguments": {"className": "com.example.MainActivity"}}, "id": 1}"""
        val response = handler.handle(request)
        assertTrue(response.contains("onCreate"), "Should return onCreate method in class inspection")
        assertTrue(response.contains("TAG"), "Should return TAG static attribute")
        assertTrue(response.contains("mCount"), "Should return mCount instance attribute")
    }
}
