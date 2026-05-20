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
}
