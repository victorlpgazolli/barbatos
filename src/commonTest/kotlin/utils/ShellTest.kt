package utils

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ShellTest {
    @Test
    fun testShellExecution() {
        val result = Shell.execute("echo 'hello_kmp'")
        assertTrue(result.output.contains("hello_kmp"), "Shell should execute command and return output")
        assertEquals(0, result.exitCode, "Exit code should be 0")
    }
}
