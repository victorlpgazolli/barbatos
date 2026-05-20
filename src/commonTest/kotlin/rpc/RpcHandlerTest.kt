package rpc

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import server.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RpcHandlerTest {
    @Test
    fun testRpcMethodNotFound() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "unknownMethod", "id": 1}""")
        }
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Method unknownMethod not found"))
    }

    @Test
    fun testListClasses() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "listClasses", "params": {"search_param": "MainActivity", "app_package": "com.example", "offset": 0, "limit": 10}, "id": 2}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("com.example.MainActivity"))
    }

    @Test
    fun testCountInstances() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "countInstances", "params": {"className": "com.example.MainActivity"}, "id": 3}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"result\":5"))
    }

    @Test
    fun testInspectClass() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "inspectClass", "params": {"className": "com.example.MainActivity"}, "id": 4}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("methods"))
    }

    @Test
    fun testListInstances() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "listInstances", "params": {"className": "com.example.MainActivity"}, "id": 5}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("totalCount"))
    }

    @Test
    fun testInspectInstance() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "inspectInstance", "params": {"className": "com.example.MainActivity", "id": "123"}, "id": 6}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("attributes"))
    }

    @Test
    fun testSetFieldValue() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "setFieldValue", "params": {"className": "com.example.MainActivity", "id": "123", "fieldName": "mCount", "type": "int", "newValue": "10"}, "id": 7}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Success"))
    }

    @Test
    fun testHookMethod() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "hookMethod", "params": {"className": "com.example.MainActivity", "methodSig": "onCreate(android.os.Bundle)"}, "id": 8}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Hooked"))
    }

    @Test
    fun testGetHookEvents() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "getHookEvents", "id": 9}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("com.example.MainActivity"))
    }

    @Test
    fun testSetMethodImplementation() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "setMethodImplementation", "params": {"className": "com.example.MainActivity", "methodSig": "onCreate(android.os.Bundle)", "code": "return null;"}, "id": 10}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Implementation replaced"))
    }

    @Test
    fun testRunOnce() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "runOnce", "params": {"className": "com.example.MainActivity", "methodSig": "onCreate(android.os.Bundle)", "code": "console.log('hi');"}, "id": 11}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Script executed"))
    }

    @Test
    fun testGetInstanceAddresses() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "getInstanceAddresses", "params": {"className": "com.example.MainActivity"}, "id": 12}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("0x123"))
    }
}
