package rpc.tools

import bridge.FakeFridaBridge
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import model.rpc.InspectInstanceResult
import model.rpc.InstanceAttribute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplorationToolsTest {

    // ─── CountInstancesTool ───────────────────────────────────────────────────

    @Test
    fun countInstances_returnsCount_forKnownClass() {
        val tool = CountInstancesTool(FakeFridaBridge())
        val args = buildJsonObject { put("className", "com.example.MainActivity") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("5"))
    }

    @Test
    fun countInstances_returnsZero_forUnknownClass() {
        val tool = CountInstancesTool(FakeFridaBridge())
        val args = buildJsonObject { put("className", "com.unknown.Class") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("0"))
    }

    @Test
    fun countInstances_returnsError_whenClassNameMissing() {
        val tool = CountInstancesTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing className"))
    }

    @Test
    fun countInstances_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(countInstancesFn = { _ -> throw RuntimeException("heap scan failed") })
        val tool = CountInstancesTool(bridge)
        val args = buildJsonObject { put("className", "com.example.MainActivity") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("heap scan failed"))
    }

    // ─── ListInstancesTool ────────────────────────────────────────────────────

    @Test
    fun listInstances_returnsInstances_forKnownClass() {
        val tool = ListInstancesTool(FakeFridaBridge())
        val args = buildJsonObject { put("className", "com.example.MainActivity") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("com.example.MainActivity@123"))
    }

    @Test
    fun listInstances_returnsTotalCount() {
        val tool = ListInstancesTool(FakeFridaBridge())
        val args = buildJsonObject { put("className", "com.example.MainActivity") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("totalCount"))
    }

    @Test
    fun listInstances_returnsError_whenClassNameMissing() {
        val tool = ListInstancesTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing className"))
    }

    @Test
    fun listInstances_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(listInstancesFn = { _ -> throw RuntimeException("frida not attached") })
        val tool = ListInstancesTool(bridge)
        val args = buildJsonObject { put("className", "com.example.MainActivity") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("frida not attached"))
    }

    // ─── InspectInstanceTool ──────────────────────────────────────────────────

    @Test
    fun inspectInstance_returnsAttributes_withDefaultPagination() {
        val tool = InspectInstanceTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity")
            put("id", "123")
        }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("mCount"))
        assertTrue(result.content.first().text!!.contains("5"))
    }

    @Test
    fun inspectInstance_passesCustomOffsetAndLimit_tobridge() {
        var capturedOffset = -1
        var capturedLimit = -1
        val bridge = FakeFridaBridge(
            inspectInstanceFn = { _, offset, limit ->
                capturedOffset = offset
                capturedLimit = limit
                InspectInstanceResult(listOf(InstanceAttribute("field", "String", "value")))
            }
        )
        val tool = InspectInstanceTool(bridge)
        val args = buildJsonObject {
            put("className", "com.example.MainActivity")
            put("id", "abc")
            put("offset", 20)
            put("limit", 10)
        }
        tool.invoke(args)
        assertEquals(20, capturedOffset)
        assertEquals(10, capturedLimit)
    }

    @Test
    fun inspectInstance_returnsError_whenClassNameMissing() {
        val tool = InspectInstanceTool(FakeFridaBridge())
        val args = buildJsonObject { put("id", "123") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing className"))
    }

    @Test
    fun inspectInstance_returnsError_whenIdMissing() {
        val tool = InspectInstanceTool(FakeFridaBridge())
        val args = buildJsonObject { put("className", "com.example.MainActivity") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing id"))
    }

    @Test
    fun inspectInstance_returnsError_whenNullArgs() {
        val tool = InspectInstanceTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertTrue(result.isError)
    }

    // ─── GetInstanceAddressesTool ─────────────────────────────────────────────

    @Test
    fun getInstanceAddresses_returnsBothAddresses() {
        val tool = GetInstanceAddressesTool(FakeFridaBridge())
        val args = buildJsonObject { put("className", "com.example.MainActivity") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("0x123"))
        assertTrue(result.content.first().text!!.contains("0x456"))
    }

    @Test
    fun getInstanceAddresses_returnsCustomAddresses_whenBridgeOverridden() {
        val bridge = FakeFridaBridge(getInstanceAddressesFn = { _ -> listOf("0xDEAD", "0xBEEF") })
        val tool = GetInstanceAddressesTool(bridge)
        val args = buildJsonObject { put("className", "any.Class") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("0xDEAD"))
        assertTrue(result.content.first().text!!.contains("0xBEEF"))
    }

    @Test
    fun getInstanceAddresses_returnsError_whenClassNameMissing() {
        val tool = GetInstanceAddressesTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing className"))
    }

    @Test
    fun getInstanceAddresses_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(getInstanceAddressesFn = { _ -> throw RuntimeException("no memory access") })
        val tool = GetInstanceAddressesTool(bridge)
        val args = buildJsonObject { put("className", "com.example.MainActivity") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("no memory access"))
    }
}
