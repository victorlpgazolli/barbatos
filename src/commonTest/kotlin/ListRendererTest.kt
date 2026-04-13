import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListRendererTest {
    @Test
    fun testComputeViewportAllVisible() {
        // 3 items, max 5 visible → show all
        val (start, end) = ListRenderer.computeViewport(3, 0, 5)
        assertEquals(0, start)
        assertEquals(3, end)
    }

    @Test
    fun testComputeViewportStartSelected() {
        // 10 items, selected index 0, max 5 visible
        val (start, end) = ListRenderer.computeViewport(10, 0, 5)
        assertEquals(0, start)
        assertEquals(5, end)
    }

    @Test
    fun testComputeViewportSelectedVisible() {
        // Selected item must be within the viewport window
        val maxVisible = 5
        val totalItems = 10
        for (selected in 0 until totalItems) {
            val (start, end) = ListRenderer.computeViewport(totalItems, selected, maxVisible)
            assertTrue(selected in start until end, "selected=$selected must be in [$start, $end)")
        }
    }

    @Test
    fun testComputeViewportEnd() {
        // 10 items, selected last item, max 5 visible → viewport at end
        val (start, end) = ListRenderer.computeViewport(10, 9, 5)
        assertEquals(10, end)
        assertEquals(5, start)
    }

    @Test
    fun testComputeViewportMiddle() {
        // 10 items, selected index 5, max 5 → centered
        val (start, end) = ListRenderer.computeViewport(10, 5, 5)
        assertTrue(5 in start until end)
        assertEquals(5, end - start)
    }
}
