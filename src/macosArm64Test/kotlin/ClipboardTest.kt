import kotlin.test.Test

class ClipboardTest {
    @Test
    fun testClipboardCanBeCalled() {
        ClipboardManager.copyToClipboard("Test from barbatos")
    }
}
