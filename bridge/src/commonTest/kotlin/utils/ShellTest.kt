package utils

import kotlin.test.Test
import kotlin.test.assertTrue

class ShellTest {
    @Test
    fun testShellExecution() {
        val output = Shell.execute("echo 'hello_kmp'")
        assertTrue(output.contains("hello_kmp"), "Shell should execute command and return output")
    }
}
