package mcp
import kotlin.test.Test
import kotlin.test.assertTrue

class McpServerTest {
    @Test
    fun testToolsList() {
        val server = McpServer()
        val response = server.processRequest("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        assertTrue(response.contains("barbatos_list_classes"))
    }
}
