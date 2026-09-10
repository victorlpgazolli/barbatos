package rpc

import bridge.FakeFridaBridge
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import model.actions.result.CheckResponse
import model.actions.result.CountInstancesResult
import model.actions.result.HealthCheckResult
import model.rpc.ApiErrorResponse

/**
 * Unit tests for RpcHandler.
 *
 * All tests call RpcHandler.handle() / handleStream() / processMethod() directly.
 * No Ktor, no HTTP layer involved.
 */
class RpcHandlerTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ─── camelToSnake / route table ───────────────────────────────────────────

    @Test
    fun camelToSnake_convertsCamelCaseMethods() {
        assertEquals("inject_gadget_from_scratch", camelToSnake("injectGadgetFromScratch"))
        assertEquals("get_instance_addresses", camelToSnake("getInstanceAddresses"))
        assertEquals("set_method_implementation", camelToSnake("setMethodImplementation"))
        assertEquals("list_classes_stream", camelToSnake("listClassesStream"))
        assertEquals("health_check", camelToSnake("healthCheck"))
        assertEquals("count_instances", camelToSnake("countInstances"))
        assertEquals("run_once", camelToSnake("runOnce"))
    }

    @Test
    fun routeByPath_coversAllTools() {
        val handler = RpcHandler(FakeFridaBridge())
        assertEquals(RpcHandler.tools.size, RpcHandler.routeByPath.size)
        RpcHandler.tools.forEach { tool ->
            assertTrue(RpcHandler.routeByPath.containsKey(camelToSnake(tool.name)), "Missing route for ${tool.name}")
        }
    }

    // ─── isStreamMethod ───────────────────────────────────────────────────────

    @Test
    fun isStreamMethod_returnsTrue_forListClassesStream() {
        val handler = RpcHandler(FakeFridaBridge())
        assertTrue(handler.isStreamMethod("listClassesStream"))
    }

    @Test
    fun isStreamMethod_returnsFalse_forNonStreamMethod() {
        val handler = RpcHandler(FakeFridaBridge())
        assertFalse(handler.isStreamMethod("healthCheck"))
    }

    // ─── handle — parse errors ────────────────────────────────────────────────

    @Test
    fun handle_returnsParseError_onInvalidJson() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("healthCheck", "not-json")
        val err = json.decodeFromString<ApiErrorResponse>(result.body)
        assertEquals(-32700, err.error.code)
        assertEquals(400, result.statusCode)
    }

    @Test
    fun handle_returnsBadRequest_onEmptyBody_forMethodRequiringParams() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("countInstances")
        val err = json.decodeFromString<ApiErrorResponse>(result.body)
        assertEquals(-32700, err.error.code)
        assertEquals(400, result.statusCode)
    }

    // ─── handle — method not found ────────────────────────────────────────────

    @Test
    fun handle_returnsMethodNotFound_onUnknownMethod() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("unknownMethod")
        val err = json.decodeFromString<ApiErrorResponse>(result.body)
        assertEquals(-32601, err.error.code)
        assertEquals(404, result.statusCode)
    }

    // ─── handle — bridge throws → internal error ──────────────────────────────

    @Test
    fun handle_returnsInternalError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(countInstancesFn = { throw RuntimeException("bridge failure") })
        val handler = RpcHandler(bridge)
        val result = handler.handle("countInstances", """{"className":"com.example.MainActivity"}""")
        val err = json.decodeFromString<ApiErrorResponse>(result.body)
        assertEquals(-32603, err.error.code)
        assertEquals(500, result.statusCode)
        assertTrue(err.error.message.contains("bridge failure"))
    }

    // ─── handle — success responses have no envelope ──────────────────────────

    @Test
    fun handle_successResponse_isPlainResult() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("countInstances", """{"className":"com.example.MainActivity"}""")
        assertEquals(200, result.statusCode)
        assertFalse(result.body.contains("jsonrpc"))
        assertFalse(result.body.contains("\"id\""))
        assertTrue(result.body.contains("count"))
    }

    // ─── handle — each method happy path ─────────────────────────────────────

    @Test
    fun handle_countInstances_returnsCount() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("countInstances", """{"className":"com.example.MainActivity"}""")
        assertEquals(200, result.statusCode)
        assertTrue(result.body.contains("5"))
    }

    @Test
    fun handle_countInstances_returnsZero_forUnknownClass() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("countInstances", """{"className":"com.unknown.Class"}""")
        assertTrue(result.body.contains("0"))
    }

    @Test
    fun handle_inspectClass_returnsMethods() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("inspectClass", """{"className":"com.example.MainActivity"}""")
        assertTrue(result.body.contains("methods"))
        assertTrue(result.body.contains("onCreate"))
    }

    @Test
    fun handle_listInstances_returnsTotalCount() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("listInstances", """{"className":"com.example.MainActivity"}""")
        assertTrue(result.body.contains("totalCount"))
    }

    @Test
    fun handle_inspectInstance_returnsAttributes() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("inspectInstance", """{"className":"com.example.MainActivity","id":"123"}""")
        assertTrue(result.body.contains("attributes"))
        assertTrue(result.body.contains("mCount"))
    }

    @Test
    fun handle_setFieldValue_returnsSuccessMessage() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("setFieldValue", """{"className":"com.example.MainActivity","id":"123","fieldName":"mCount","type":"int","newValue":"10"}""")
        assertTrue(result.body.contains("Success"))
        assertTrue(result.body.contains("mCount"))
    }

    @Test
    fun handle_hookMethod_returnsHookedMessage() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("hookMethod", """{"className":"com.example.MainActivity","methodSig":"onCreate(android.os.Bundle)"}""")
        assertTrue(result.body.contains("Hooked"))
    }

    @Test
    fun handle_setMethodImplementation_returnsReplacedMessage() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("setMethodImplementation", """{"className":"com.example.MainActivity","methodSig":"onCreate(android.os.Bundle)","code":"return null;"}""")
        assertTrue(result.body.contains("Implementation replaced"))
    }

    @Test
    fun handle_runOnce_returnsScriptExecuted() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("runOnce", """{"className":"com.example.MainActivity","methodSig":"onCreate(android.os.Bundle)","code":"console.log('hi');"}""")
        assertTrue(result.body.contains("Script executed"))
    }

    @Test
    fun handle_getInstanceAddresses_returnsAddressList() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("getInstanceAddresses", """{"className":"com.example.MainActivity"}""")
        assertTrue(result.body.contains("0x123"))
        assertTrue(result.body.contains("0x456"))
    }

    @Test
    fun handle_injectGadgetFromScratch_withoutBody_usesDefaults() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("injectGadgetFromScratch")
        val res = json.decodeFromString<JsonObject>(result.body)
        assertEquals(200, result.statusCode)
        assertTrue(res.containsKey("steps"))
    }

    @Test
    fun handle_injectGadgetFromScratch_returnsStatus() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("injectGadgetFromScratch", """{"with_logs":true,"limit":100}""")
        assertTrue(result.body.contains("status"))
        assertTrue(result.body.contains("completed"))
    }

    @Test
    fun handle_healthCheck_returnsOverall() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("healthCheck")
        assertTrue(result.body.contains("overall"))
        assertTrue(result.body.contains("ok"))
    }

    @Test
    fun handle_getHookEvents_returnsEvents() {
        val handler = RpcHandler(FakeFridaBridge())
        val result = handler.handle("getHookEvents")
        assertTrue(result.body.contains("events"))
    }

    // ─── handle — custom bridge response ──────────────────────────────────────

    @Test
    fun handle_healthCheck_returnsCustomValue_whenBridgeOverridden() {
        val bridge = FakeFridaBridge(healthCheckFn = {
            HealthCheckResult("degraded", mapOf("bridge" to CheckResponse("error", "down")))
        })
        val handler = RpcHandler(bridge)
        val result = handler.handle("healthCheck")
        assertTrue(result.body.contains("degraded"))
    }

    @Test
    fun handle_countInstances_returnsCustomCount_whenBridgeOverridden() {
        val bridge = FakeFridaBridge(countInstancesFn = { _ -> CountInstancesResult(42) })
        val handler = RpcHandler(bridge)
        val result = handler.handle("countInstances", """{"className":"any.Class"}""")
        assertTrue(result.body.contains("42"))
    }

    // ─── handleStream ─────────────────────────────────────────────────────────

    @Test
    fun handleStream_emitsPlainChunks_forListClassesStream() {
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
                "listClassesStream",
                """{"search_param":"Main"}"""
            ) { line -> emitted.add(line) }
        }

        assertTrue(emitted.isNotEmpty(), "Should emit at least one chunk or error message via stream")
        assertTrue(emitted.any { it.contains("com.example.MainActivity") }, "Should emit MainActivity")
        assertTrue(emitted.none { it.contains("jsonrpc") }, "Chunks must not carry the JSON-RPC envelope")
        assertTrue(emitted.any { it.contains("\"list\"") }, "Chunk must be the plain partial result")
    }

    @Test
    fun handleStream_emitsError_onInvalidJson() {
        val handler = RpcHandler(FakeFridaBridge())
        val emitted = mutableListOf<String>()
        runBlocking {
            handler.handleStream("listClassesStream", "not-json") { line -> emitted.add(line) }
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
                "listClassesStream",
                """{"search_param":"Main"}"""
            ) { line -> emitted.add(line) }
        }
        assertTrue(emitted.any { it.contains("-32603") })
    }
}