package rpc

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import server.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

class RpcHandlerTest {
    @Test
    fun testRpcMethodNotFound() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "unknownMethod", "id": 1}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val errorRes = kotlinx.serialization.json.Json.decodeFromString<RpcErrorResponse>(body)
        assertEquals(-32601, errorRes.error.code)
        assertEquals(1, errorRes.id?.jsonPrimitive?.int)
    }

    @Test
    fun testListClasses() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "listClasses", "params": {"search_param": "MainActivity", "app_package": "com.example", "offset": 0, "limit": 10}, "id": 2}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val res = kotlinx.serialization.json.Json.decodeFromString<RpcResponse>(body)
        assertTrue(res.result.toString().contains("com.example.MainActivity"))
        assertEquals(2, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testCountInstances() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "countInstances", "params": {"className": "com.example.MainActivity"}, "id": 3}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertEquals("5", res.result.toString())
        assertEquals(3, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testInspectClass() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "inspectClass", "params": {"className": "com.example.MainActivity"}, "id": 4}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("methods"))
        assertEquals(4, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testListInstances() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "listInstances", "params": {"className": "com.example.MainActivity"}, "id": 5}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("totalCount"))
        assertEquals(5, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testInspectInstance() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "inspectInstance", "params": {"className": "com.example.MainActivity", "id": "123"}, "id": 6}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("attributes"))
        assertEquals(6, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testSetFieldValue() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "setFieldValue", "params": {"className": "com.example.MainActivity", "id": "123", "fieldName": "mCount", "type": "int", "newValue": "10"}, "id": 7}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("Success"))
        assertEquals(7, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testHookMethod() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "hookMethod", "params": {"className": "com.example.MainActivity", "methodSig": "onCreate(android.os.Bundle)"}, "id": 8}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("Hooked"))
        assertEquals(8, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testGetHookEvents() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "getHookEvents", "id": 9}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("com.example.MainActivity"))
        assertEquals(9, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testSetMethodImplementation() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "setMethodImplementation", "params": {"className": "com.example.MainActivity", "methodSig": "onCreate(android.os.Bundle)", "code": "return null;"}, "id": 10}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("Implementation replaced"))
        assertEquals(10, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testRunOnce() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "runOnce", "params": {"className": "com.example.MainActivity", "methodSig": "onCreate(android.os.Bundle)", "code": "console.log('hi');"}, "id": 11}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("Script executed"))
        assertEquals(11, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testGetInstanceAddresses() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "getInstanceAddresses", "params": {"className": "com.example.MainActivity"}, "id": 12}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("0x123"))
        assertEquals(12, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testPrepareEnvironment() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "prepareEnvironment", "params": {"target": "com.example.app"}, "id": 13}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("package_name"))
        assertEquals(13, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testInjectGadgetFromScratch() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "injectGadgetFromScratch", "params": {"with_logs": true, "limit": 100}, "id": 14}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("status"))
        assertEquals(14, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testInjectJdwp() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "injectJdwp", "params": {"target": "device1", "port": 8080, "package_name": "com.example"}, "id": 15}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("Success"))
        assertEquals(15, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testHealthCheck() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "healthCheck", "id": 16}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("overall"))
        assertEquals(16, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testPatchAndInstallIosApp() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "patchAndInstallIosApp", "params": {"appPath": "/path/to/app"}, "id": 17}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertEquals("\"success\"", res.result.toString())
        assertEquals(17, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testCheckIosJailbreakStatus() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "checkIosJailbreakStatus", "params": {"serial": "12345"}, "id": 18}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertEquals("\"jailbroken\"", res.result.toString())
        assertEquals(18, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testInjectJailbrokenIos() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "injectJailbrokenIos", "params": {"serial": "12345"}, "id": 19}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertEquals("\"success\"", res.result.toString())
        assertEquals(19, res.id?.jsonPrimitive?.int)
    }

    @Test
    fun testCheckIosDeployStatus() = testApplication {
        val bridge = bridge.MockFridaBridge()
        application { module(bridge) }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "checkIosDeployStatus", "id": 20}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val res = kotlinx.serialization.json.Json.decodeFromString(RpcResponse.serializer(), response.bodyAsText())
        assertTrue(res.result.toString().contains("status"))
        assertEquals(20, res.id?.jsonPrimitive?.int)
    }
}
