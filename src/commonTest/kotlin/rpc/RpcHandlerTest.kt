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
}
