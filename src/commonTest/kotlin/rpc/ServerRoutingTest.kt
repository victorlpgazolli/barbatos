package rpc

import bridge.FakeFridaBridge
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import model.actions.result.ListClassesPartialResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import module

/**
 * HTTP smoke tests over the real routing tree produced by Server.module().
 */
class ServerRoutingTest {

    @Test
    fun v0ApiEndpoints_areReachable() = testApplication {
        application { module(FakeFridaBridge()) }

        val ping = client.get("/ping")
        assertEquals(HttpStatusCode.OK, ping.status)

        val health = client.post("/v0/api/health_check")
        assertEquals(HttpStatusCode.OK, health.status)
        assertTrue(health.bodyAsText().contains("overall"))

        val count = client.post("/v0/api/count_instances") {
            contentType(ContentType.Application.Json)
            setBody("""{"className":"com.example.MainActivity"}""")
        }
        assertEquals(HttpStatusCode.OK, count.status)
        assertTrue(count.bodyAsText().contains("count"))

        val invalid = client.post("/v0/api/count_instances") {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val unknown = client.post("/v0/api/nope") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.NotFound, unknown.status)
    }

    @Test
    fun listClassesStream_returnsNdjson() = testApplication {
        val bridge = FakeFridaBridge(
            listClassesStreamFn = { _, onChunk, onComplete ->
                runBlocking {
                    onChunk(ListClassesPartialResult(listOf("com.example.MainActivity")))
                }
                onComplete()
            }
        )
        application { module(bridge) }

        val stream = client.post("/v0/api/list_classes_stream") {
            contentType(ContentType.Application.Json)
            setBody("""{"search_param":"Main"}""")
        }
        assertEquals(HttpStatusCode.OK, stream.status)
        assertTrue(stream.bodyAsText().contains("com.example.MainActivity"))
    }
}