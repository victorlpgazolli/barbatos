package rpc.tools

import bridge.MockFridaBridge
import rpc.McpHandler
import kotlin.test.Test
import kotlin.test.assertTrue

class ModificationToolsTest {

    @Test
    fun testSetFieldValueTool() {
        val bridge = MockFridaBridge()
        val tools = listOf(SetFieldValueTool(bridge))
        val handler = McpHandler(tools)
        val request = """{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "set_field_value", "arguments": {"className": "com.example.MainActivity", "id": "123", "fieldName": "mCount", "type": "int", "newValue": "10"}}, "id": 1}"""
        val response = handler.handle(request)
        assertTrue(response!!.contains("Success: mCount set to 10"), "Should return success message for field value change")
    }

    @Test
    fun testHookMethodTool() {
        val bridge = MockFridaBridge()
        val tools = listOf(HookMethodTool(bridge))
        val handler = McpHandler(tools)
        val request = """{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "hook_method", "arguments": {"className": "com.example.MainActivity", "methodSig": "onCreate(android.os.Bundle)"}}, "id": 1}"""
        val response = handler.handle(request)
        assertTrue(response!!.contains("Hooked com.example.MainActivity.onCreate(android.os.Bundle)"), "Should return hooked message")
    }

    @Test
    fun testGetHookEventsTool() {
        val bridge = MockFridaBridge()
        val tools = listOf(GetHookEventsTool(bridge))
        val handler = McpHandler(tools)
        val request = """{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "get_hook_events", "arguments": {}}, "id": 1}"""
        val response = handler.handle(request)
        assertTrue(response!!.contains("com.example.MainActivity"), "Should return hook event class name")
        assertTrue(response!!.contains("onCreate(android.os.Bundle)"), "Should return hook event method signature")
    }

    @Test
    fun testSetMethodImplementationTool() {
        val bridge = MockFridaBridge()
        val tools = listOf(SetMethodImplementationTool(bridge))
        val handler = McpHandler(tools)
        val request = """{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "set_method_implementation", "arguments": {"className": "com.example.MainActivity", "methodSig": "onCreate(android.os.Bundle)", "code": "return;"}}, "id": 1}"""
        val response = handler.handle(request)
        assertTrue(response!!.contains("Implementation replaced for com.example.MainActivity.onCreate(android.os.Bundle)"), "Should return replaced implementation message")
    }

    @Test
    fun testRunOnceTool() {
        val bridge = MockFridaBridge()
        val tools = listOf(RunOnceTool(bridge))
        val handler = McpHandler(tools)
        val request = """{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "run_once", "arguments": {"className": "com.example.MainActivity", "methodSig": "onCreate(android.os.Bundle)", "code": "console.log('hi');"}}, "id": 1}"""
        val response = handler.handle(request)
        assertTrue(response!!.contains("Script executed"), "Should return executed script message")
    }
}
