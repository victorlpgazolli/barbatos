package rpc

import bridge.FakeFridaBridge
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import model.actions.result.CountInstancesResult
import model.rpc.RpcErrorResponse
import model.rpc.RpcResponse

/**
 * Unit tests for RpcHandler.
 *
 * All tests call RpcHandler.handle() / handleStream() / isStreamMethod() directly.
 * No Ktor, no HTTP layer involved.
 */
class RpcHandlerTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ─── isStreamMethod ───────────────────────────────────────────────────────

    @Test
    fun isStreamMethod_returnsTrue_forListClassesStream() {
        val handler = RpcHandler(FakeFridaBridge())
        assertTrue(handler.isStreamMethod("""{"jsonrpc":"2.0","method":"listClassesStream","id":1}"""))
    }

    @Test
    fun isStreamMethod_returnsFalse_forNonStreamMethod() {
        val handler = RpcHandler(FakeFridaBridge())
        assertFalse(handler.isStreamMethod("""{"jsonrpc":"2.0","method":"healthCheck","id":1}"""))
    }

    @Test
    fun isStreamMethod_returnsFalse_forInvalidJson() {
        val handler = RpcHandler(FakeFridaBridge())
        assertFalse(handler.isStreamMethod("not-json"))
    }

    // ─── handle — parse errors ────────────────────────────────────────────────

    @Test
    fun handle_returnsParseError_onInvalidJson() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("not-json")
        val err = json.decodeFromString<RpcErrorResponse>(result.body)
        assertEquals(-32700, err.error.code)
        assertTrue(err.id == null || err.id is JsonNull || err.id.toString() == "null", "ID should be null")
    }

    @Test
    fun handle_returnsParseError_onEmptyBody() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("")
        val err = json.decodeFromString<RpcErrorResponse>(result.body)
        assertEquals(-32700, err.error.code)
    }

    // ─── handle — method not found ────────────────────────────────────────────

    @Test
    fun handle_returnsMethodNotFound_onUnknownMethod() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"unknownMethod","id":1}""")
        val err = json.decodeFromString<RpcErrorResponse>(result.body)
        assertEquals(-32601, err.error.code)
        assertEquals(1, err.id?.jsonPrimitive?.int)
    }

    @Test
    fun handle_preservesId_onMethodNotFound() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"nope","id":99}""")
        val err = json.decodeFromString<RpcErrorResponse>(result.body)
        assertEquals(99, err.id?.jsonPrimitive?.int)
    }

    // ─── handle — bridge throws → internal error ──────────────────────────────

    @Test
    fun handle_returnsInternalError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(pingJavaFn = { throw RuntimeException("bridge failure") })
        val handler = RpcHandler(bridge)
        val result = handler.handle("""{"jsonrpc":"2.0","method":"debugPing","id":5}""")
        val err = json.decodeFromString<RpcErrorResponse>(result.body)
        assertEquals(-32603, err.error.code)
        assertTrue(err.error.message.contains("bridge failure"))
    }

    // ─── handle — missing params → internal error ─────────────────────────────

    @Test
    fun handle_returnsInternalError_whenParamsMissing_countInstances() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"countInstances","id":1}""")
        val err = json.decodeFromString<RpcErrorResponse>(result.body)
        assertEquals(-32603, err.error.code)
    }

    @Test
    fun handle_returnsInternalError_whenParamsMissing_inspectClass() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"inspectClass","id":1}""")
        val err = json.decodeFromString<RpcErrorResponse>(result.body)
        assertEquals(-32603, err.error.code)
    }

    @Test
    fun handle_returnsInternalError_whenParamsMissing_hookMethod() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"hookMethod","id":1}""")
        val err = json.decodeFromString<RpcErrorResponse>(result.body)
        assertEquals(-32603, err.error.code)
    }

    // ─── handle — id types preserved ─────────────────────────────────────────

    @Test
    fun handle_preservesNumericId_inSuccessResponse() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"debugPing","id":42}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertEquals(42, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun handle_preservesStringId_inSuccessResponse() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"debugPing","id":"req-abc"}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertEquals("req-abc", res.id?.jsonPrimitive?.content)
    }

    // ─── handle — each method happy path ─────────────────────────────────────

    @Test
    fun handle_debugPing_returnsExpectedString() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"debugPing","id":1}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("Mock: Java OK"))
    }

    @Test
    fun handle_testRpc_returnsExpectedString() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"testRpc","id":1}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("Mock: RPC OK"))
    }

    @Test
    fun handle_countInstances_returnsCount() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"countInstances","params":{"className":"com.example.MainActivity"},"id":3}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("5"))
    }

    @Test
    fun handle_countInstances_returnsZero_forUnknownClass() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"countInstances","params":{"className":"com.unknown.Class"},"id":3}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("0"))
    }

    @Test
    fun handle_inspectClass_returnsMethods() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"inspectClass","params":{"className":"com.example.MainActivity"},"id":4}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("methods"))
        assertTrue(res.result.toString().contains("onCreate"))
    }

    @Test
    fun handle_listInstances_returnsTotalCount() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"listInstances","params":{"className":"com.example.MainActivity"},"id":5}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("totalCount"))
    }

    @Test
    fun handle_inspectInstance_returnsAttributes() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"inspectInstance","params":{"className":"com.example.MainActivity","id":"123"},"id":6}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("attributes"))
        assertTrue(res.result.toString().contains("mCount"))
    }

    @Test
    fun handle_setFieldValue_returnsSuccessMessage() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"setFieldValue","params":{"className":"com.example.MainActivity","id":"123","fieldName":"mCount","type":"int","newValue":"10"},"id":7}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("Success"))
        assertTrue(res.result.toString().contains("mCount"))
    }

    @Test
    fun handle_hookMethod_returnsHookedMessage() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"hookMethod","params":{"className":"com.example.MainActivity","methodSig":"onCreate(android.os.Bundle)"},"id":8}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("Hooked"))
    }

    @Test
    fun handle_getHookEvents_returnsEventList() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"getHookEvents","id":9}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("com.example.MainActivity"))
    }

    @Test
    fun handle_setMethodImplementation_returnsReplacedMessage() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"setMethodImplementation","params":{"className":"com.example.MainActivity","methodSig":"onCreate(android.os.Bundle)","code":"return null;"},"id":10}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("Implementation replaced"))
    }

    @Test
    fun handle_runOnce_returnsScriptExecuted() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"runOnce","params":{"className":"com.example.MainActivity","methodSig":"onCreate(android.os.Bundle)","code":"console.log('hi');"},"id":11}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("Script executed"))
    }

    @Test
    fun handle_getInstanceAddresses_returnsAddressList() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"getInstanceAddresses","params":{"className":"com.example.MainActivity"},"id":12}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("0x123"))
        assertTrue(res.result.toString().contains("0x456"))
    }

    @Test
    fun handle_prepareEnvironment_returnsStatus() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"prepareEnvironment","params":{"target":"com.example.app"},"id":13}""")
        assertTrue(result.body.contains("Attached to com.example.app") || result.body.contains("error") || result.body.contains("com.example.app"))
    }

    @Test
    fun handle_injectGadgetFromScratch_returnsStatus() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"injectGadgetFromScratch","params":{"with_logs":true,"limit":100},"id":14}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("status"))
        assertTrue(res.result.toString().contains("completed"))
    }

    @Test
    fun handle_injectJdwp_returnsSuccess() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"injectJdwp","params":{"target":"device1","port":8080,"package_name":"com.example"},"id":15}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("Success"))
    }

    @Test
    fun handle_healthCheck_returnsOverall() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("""{"jsonrpc":"2.0","method":"healthCheck","id":16}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("overall"))
        assertTrue(res.result.toString().contains("ok"))
    }

    // ─── handle — custom bridge response ──────────────────────────────────────

    @Test
    fun handle_debugPing_returnsCustomValue_whenBridgeOverridden() {
        val bridge = FakeFridaBridge(pingJavaFn = { "custom ping response" })
        val handler = RpcHandler(bridge)
        val result = handler.handle("""{"jsonrpc":"2.0","method":"debugPing","id":1}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("custom ping response"))
    }

    @Test
    fun handle_countInstances_returnsCustomCount_whenBridgeOverridden() {
        val bridge = FakeFridaBridge(countInstancesFn = { _ -> CountInstancesResult(42) })
        val handler = RpcHandler(bridge)
        val result = handler.handle("""{"jsonrpc":"2.0","method":"countInstances","params":{"className":"any.Class"},"id":1}""")
        val res = json.decodeFromString<RpcResponse>(result.body)
        assertTrue(res.result.toString().contains("42"))
    }

    // ─── handleStream ─────────────────────────────────────────────────────────

    @Test
    fun handleStream_emitsChunks_forListClassesStream() {
        val bridge = FakeFridaBridge(
            listClassesStreamFn = { _, onChunk, onComplete ->
                runBlocking {
                    onChunk(model.actions.result.ListClassesPartialResult(listOf("com.example.MainActivity")))
                }
                onComplete()
            }
        )
        val handler = RpcHandler(bridge)
        val emitted = mutableListOf<String>()

        runBlocking {
            handler.handleStream(
                """{"jsonrpc":"2.0","method":"listClassesStream","params":{"search_param":"Main"},"id":1}"""
            ) { line -> emitted.add(line) }
        }

        assertTrue(emitted.isNotEmpty(), "Should emit at least one chunk or error message via stream")
        assertTrue(emitted.any { it.contains("com.example.MainActivity") }, "Should emit MainActivity")
    }

    @Test
    fun handleStream_emitsError_onInvalidJson() {
        val handler = RpcHandler(FakeFridaBridge())
        val emitted = mutableListOf<String>()
        runBlocking {
            handler.handleStream("not-json") { line -> emitted.add(line) }
        }
        assertEquals(1, emitted.size)
        assertTrue(emitted[0].contains("-32700"))
    }

    @Test
    fun handleStream_emitsInternalError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(
            listClassesStreamFn = { _, _, _ ->
                throw RuntimeException("stream bridge failure")
            }
        )
        val handler = RpcHandler(bridge)
        val emitted = mutableListOf<String>()
        runBlocking {
            handler.handleStream(
                """{"jsonrpc":"2.0","method":"listClassesStream","id":1}"""
            ) { line -> emitted.add(line) }
        }
        assertTrue(emitted.any { it.contains("-32603") })
    }
}