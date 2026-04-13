import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RpcClientTest {
    private fun makeMockClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler(handler)
            }
            install(ContentNegotiation) {
                json()
            }
        }
    }

    @Test
    fun testPingSuccess() = runBlocking {
        val mockClient = makeMockClient { _ ->
            respond(
                content = "pong",
                status = HttpStatusCode.OK
            )
        }
        val original = RpcClient.client
        RpcClient.client = mockClient
        try {
            assertTrue(RpcClient.ping())
        } finally {
            RpcClient.client = original
        }
    }

    @Test
    fun testPingFailure() = runBlocking {
        val mockClient = makeMockClient { _ ->
            respond(
                content = "error",
                status = HttpStatusCode.InternalServerError
            )
        }
        val original = RpcClient.client
        RpcClient.client = mockClient
        try {
            assertFalse(RpcClient.ping())
        } finally {
            RpcClient.client = original
        }
    }

    @Test
    fun testListClassesSuccess() = runBlocking {
        val responseJson = """{"jsonrpc":"2.0","result":["com.example.Foo","com.example.Bar"],"id":1}"""
        val mockClient = makeMockClient { _ ->
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val original = RpcClient.client
        RpcClient.client = mockClient
        try {
            val (result, error) = RpcClient.listClasses("Foo", "com.example")
            assertEquals(null, error)
            assertEquals(listOf("com.example.Foo", "com.example.Bar"), result)
        } finally {
            RpcClient.client = original
        }
    }

    @Test
    fun testListClassesRpcError() = runBlocking {
        val responseJson = """{"jsonrpc":"2.0","error":{"code":-32000,"message":"Bridge error"},"id":1}"""
        val mockClient = makeMockClient { _ ->
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val original = RpcClient.client
        RpcClient.client = mockClient
        try {
            val (result, error) = RpcClient.listClasses("Foo", "com.example")
            assertEquals(null, result)
            assertEquals("Bridge error", error)
        } finally {
            RpcClient.client = original
        }
    }

    @Test
    fun testInjectGadgetFromScratchProgress() = runBlocking {
        val responseJson = """
        {
            "jsonrpc": "2.0",
            "result": {
                "status": "running",
                "steps": [
                    {"id": "get_target", "title": "Identify target", "status": "completed"},
                    {"id": "setup_adb", "title": "Configure ADB", "status": "running"}
                ],
                "logs": ["Log line 1", "Log line 2"],
                "error_message": null
            },
            "id": 1
        }
        """.trimIndent()
        
        val mockClient = makeMockClient { _ ->
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val original = RpcClient.client
        RpcClient.client = mockClient
        try {
            val (result, error) = RpcClient.injectGadgetFromScratch()
            assertEquals(null, error)
            assertEquals("running", result?.status)
            assertEquals(2, result?.steps?.size)
            assertEquals("completed", result?.steps?.get(0)?.status)
            assertEquals(listOf("Log line 1", "Log line 2"), result?.logs)
        } finally {
            RpcClient.client = original
        }
    }
}
