package utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellTest {

    @Test
    fun execute_returnsOutput_forSimpleCommand() {
        val result = Shell.execute("echo 'hello_kmp'")
        assertTrue(result.output.contains("hello_kmp"), "Shell should execute command and return output")
        assertEquals(0, result.exitCode, "Exit code should be 0")
    }

    @Test
    fun execute_returnsExitCode0_forSuccessfulCommand() {
        val result = Shell.execute("true")
        assertEquals(0, result.exitCode, "true command should exit with 0")
    }

    @Test
    fun execute_returnsNonZeroExitCode_forFailingCommand() {
        val result = Shell.execute("false")
        assertFalse(result.exitCode == 0, "false command should exit with non-zero code")
        assertEquals(1, result.exitCode, "false command should exit with 1")
    }

    @Test
    fun execute_capturesStderrInOutput_whenRedirectEnabled() {
        val result = Shell.execute("echo 'stderr_msg' >&2", redirectStderr = true)
        assertTrue(result.output.contains("stderr_msg"), "Stderr should be captured when redirectStderr=true")
    }

    @Test
    fun execute_doesNotCaptureStderr_whenRedirectDisabled() {
        val result = Shell.execute("echo 'stderr_only' >&2", redirectStderr = false)
        assertFalse(result.output.contains("stderr_only"), "Stderr should not appear in output when redirectStderr=false")
    }

    @Test
    fun execute_returnsMultilineOutput() {
        val result = Shell.execute("printf 'line1\\nline2\\nline3'")
        assertTrue(result.output.contains("line1"))
        assertTrue(result.output.contains("line2"))
        assertTrue(result.output.contains("line3"))
    }

    @Test
    fun execute_returnsEmptyOutput_forCommandWithNoOutput() {
        val result = Shell.execute("true")
        assertEquals("", result.output.trim(), "Command with no output should return empty string")
    }
}
