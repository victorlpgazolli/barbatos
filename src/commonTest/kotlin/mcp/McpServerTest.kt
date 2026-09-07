import bridge.FakeFridaBridge
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.testing.ChannelTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.runBlocking
import rpc.RpcHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the MCP server built with the official Kotlin SDK.
 *
 * These tests exercise the exact production wiring produced by createMcpServer(bridge)
 * (from commonMain Server.kt) over an in-memory ChannelTransport, using FakeFridaBridge
 * as the Frida substitute.
 */
@OptIn(ExperimentalMcpApi::class)
class McpServerTest {

    private class Connection(
        val client: Client,
        val linked: ChannelTransport.LinkedTransports,
    )

    private suspend fun buildConnection(): Connection {
        val linked = ChannelTransport.createLinkedPair()
        val server = createMcpServer(FakeFridaBridge())
        server.createSession(linked.serverTransport)
        val client = Client(clientInfo = Implementation(name = "test-client", version = "1.0"))
        client.connect(linked.clientTransport)
        return Connection(client = client, linked = linked)
    }

    @Test
    fun toolsList_exposesAllRpcTools() = runBlocking {
        val connection = buildConnection()
        try {
            val tools = connection.client.listTools().tools
            assertEquals(RpcHandler.tools.size, tools.size, "Every RPC tool must be exposed over MCP")
            assertEquals(RpcHandler.tools.map { it.name }.sorted(), tools.map { it.name }.sorted())
        } finally {
            connection.client.close()
        }
    }

    @Test
    fun toolsList_countInstances_hasInputSchemaProperties() = runBlocking {
        val connection = buildConnection()
        try {
            val tools = connection.client.listTools().tools
            val countInstances = tools.first { it.name == "countInstances" }
            val properties = requireNotNull(countInstances.inputSchema.properties) { "countInstances must declare input schema properties" }
            assertTrue(properties.containsKey("className"))
            assertTrue(requireNotNull(countInstances.inputSchema.required).contains("className"))
        } finally {
            connection.client.close()
        }
    }

    @Test
    fun toolsCall_healthCheck_returnsOk() = runBlocking {
        val connection = buildConnection()
        try {
            val result = connection.client.callTool("healthCheck", emptyMap())
            assertEquals(false, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("overall"))
            assertTrue(text.contains("ok"))
        } finally {
            connection.client.close()
        }
    }

    @Test
    fun toolsCall_countInstances_returnsCount() = runBlocking {
        val connection = buildConnection()
        try {
            val result = connection.client.callTool("countInstances", mapOf("className" to "com.example.MainActivity"))
            assertEquals(false, result.isError)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("5"), "FakeFridaBridge reports 5 instances, got: $text")
        } finally {
            connection.client.close()
        }
    }

    @Test
    fun toolsCall_returnsErrorResult_whenParamsMissing() = runBlocking {
        val connection = buildConnection()
        try {
            val result = connection.client.callTool("countInstances", emptyMap())
            assertEquals(true, result.isError, "Missing required params must surface as an error result, not a crash")
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("countInstances failed"), "Error text must name the failing tool, got: $text")
        } finally {
            connection.client.close()
        }
    }
}