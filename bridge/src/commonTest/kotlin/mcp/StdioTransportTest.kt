package mcp
import kotlin.test.Test
import kotlin.test.assertTrue

class StdioTransportTest {
    @Test
    fun testTransportInstantiates() {
        val transport = StdioTransport()
        assertTrue(transport != null)
    }
}
