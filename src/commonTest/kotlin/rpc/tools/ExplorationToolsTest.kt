package rpc.tools

import bridge.MockFridaBridge
import rpc.McpHandler
import kotlin.test.Test
import kotlin.test.assertTrue

class ExplorationToolsTest {
    @Test
    fun testCountInstancesTool() {
        val bridge = MockFridaBridge()
        val tools = listOf(CountInstancesTool(bridge))
        val handler = McpHandler(tools)
        val request = """{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "count_instances", "arguments": {"className": "com.example.MainActivity"}}, "id": 1}"""
        val response = handler.handle(request)!!
        assertTrue(response!!.contains("5"), "Should return 5 instances")
    }

    @Test
    fun testListInstancesTool() {
        val bridge = MockFridaBridge()
        val tools = listOf(ListInstancesTool(bridge))
        val handler = McpHandler(tools)
        val request = """{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "list_instances", "arguments": {"className": "com.example.MainActivity"}}, "id": 1}"""
        val response = handler.handle(request)!!
        assertTrue(response!!.contains("com.example.MainActivity@123"), "Should return instance string")
    }

    @Test
    fun testInspectInstanceTool() {
        val bridge = MockFridaBridge()
        val tools = listOf(InspectInstanceTool(bridge))
        val handler = McpHandler(tools)
        val request = """{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "inspect_instance", "arguments": {"className": "com.example.MainActivity", "id": "123"}}, "id": 1}"""
        val response = handler.handle(request)!!
        assertTrue(response!!.contains("mCount"), "Should return mCount attribute")
        assertTrue(response!!.contains("5"), "Should return value 5")
    }

    @Test
    fun testGetInstanceAddressesTool() {
        val bridge = MockFridaBridge()
        val tools = listOf(GetInstanceAddressesTool(bridge))
        val handler = McpHandler(tools)
        val request = """{"jsonrpc": "2.0", "method": "tools/call", "params": {"name": "get_instance_addresses", "arguments": {"className": "com.example.MainActivity"}}, "id": 1}"""
        val response = handler.handle(request)!!
        assertTrue(response!!.contains("0x123"), "Should return address 0x123")
        assertTrue(response!!.contains("0x456"), "Should return address 0x456")
    }
}
