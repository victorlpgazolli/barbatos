import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandExecutorTest {
    @Test
    fun testSortClassesPriority() {
        val classes = listOf(
            "[B",
            "com.example.app.MainActivity",
            "android.app.Activity",
            "com.example.Other",
            "[Ljava.lang.String;"
        )
        val sorted = CommandExecutor.sortClasses(classes, "com.example.app", "")

        // App package exact prefix gets priority 3 → first
        assertEquals("com.example.app.MainActivity", sorted[0])
        // First-two-segments match gets priority 2
        assertEquals("com.example.Other", sorted[1])
        // Other non-array gets priority 1
        assertEquals("android.app.Activity", sorted[2])
        // Arrays get priority -1 → bottom
        assertTrue(sorted[3].startsWith("["))
        assertTrue(sorted[4].startsWith("["))
    }

    @Test
    fun testSortClassesWithSearchQuery() {
        val classes = listOf("com.example.UiState", "com.example.BiometryUiState", "com.example.UiStateImpl")
        val sorted = CommandExecutor.sortClasses(classes, "com.example", "UiState")

        // Exact startsWith in class name gets +10 priority → sorted by name within that group
        assertEquals("com.example.UiState", sorted[0])
        assertEquals("com.example.UiStateImpl", sorted[1])
        assertEquals("com.example.BiometryUiState", sorted[2])
    }

    @Test
    fun testSortClassesArraysAlwaysLast() {
        val classes = listOf("[B", "[I", "com.example.Foo")
        val sorted = CommandExecutor.sortClasses(classes, "com.example", "")
        assertEquals("com.example.Foo", sorted[0])
        assertTrue(sorted[1].startsWith("["))
        assertTrue(sorted[2].startsWith("["))
    }

    @Test
    fun testSortClassesEmptyList() {
        val sorted = CommandExecutor.sortClasses(emptyList(), "com.example", "")
        assertEquals(0, sorted.size)
    }

    @Test
    fun testSyntheticClassFiltering() {
        val classes = listOf(
            "com.example.MainActivity",
            "com.example.MainActivity\$1",
            "com.example.MainActivity\$Companion",
            "android.app.Activity"
        )

        // Default: hide synthetic (contain $)
        val defaultSorted = CommandExecutor.sortClasses(classes, "com.example", "")
        assertEquals(2, defaultSorted.size)
        assertTrue(defaultSorted.none { it.contains('$') })

        // showSynthetic = true: show all
        val allShown = CommandExecutor.sortClasses(classes, "com.example", "", showSynthetic = true)
        assertEquals(4, allShown.size)
        assertTrue(allShown.any { it.contains('$') })

        // searchQuery contains $: show all
        val searchWithDollar = CommandExecutor.sortClasses(classes, "com.example", "MainActivity\$")
        assertEquals(4, searchWithDollar.size)
        assertTrue(searchWithDollar.any { it.contains('$') })
    }
}
