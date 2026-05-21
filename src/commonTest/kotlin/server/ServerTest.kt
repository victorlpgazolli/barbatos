package server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTest {
    @Test
    fun testPingEndpoint() = testApplication {
        application {
            module(bridge.MockFridaBridge())
        }
        val response = client.get("/ping")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status": "pong"}""", response.bodyAsText())
    }
}
