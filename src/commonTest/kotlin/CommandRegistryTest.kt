import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandRegistryTest {
    @Test
    fun testSearchPrefixPriority() {
        // "e" should match "exit" (startsWith) before "clear" (no match) or "debug" (no match)
        val results = CommandRegistry.search("e")
        assertTrue(results.isNotEmpty())
        assertEquals("exit", results[0].name)
    }

    @Test
    fun testSearchEmpty() {
        val results = CommandRegistry.search("")
        assertEquals(0, results.size)
    }

    @Test
    fun testSearchExactMatch() {
        val results = CommandRegistry.search("debug")
        assertEquals(1, results.size)
        assertEquals("debug", results[0].name)
    }

    @Test
    fun testSearchNoMatch() {
        val results = CommandRegistry.search("zzz")
        assertEquals(0, results.size)
    }

    @Test
    fun testSearchCaseInsensitive() {
        val results = CommandRegistry.search("D")
        assertTrue(results.isNotEmpty())
        assertEquals("debug", results[0].name)
    }
}
