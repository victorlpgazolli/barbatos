package rpc.tools

import bridge.FakeFridaBridge
import model.rpc.CheckResponse
import model.rpc.HealthCheckResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonitoringToolsTest {

    @Test
    fun healthCheck_returnsOkStatus_withDefaultBridge() {
        val tool = HealthCheckTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("overall"))
        assertTrue(result.content.first().text!!.contains("ok"))
    }

    @Test
    fun healthCheck_returnsCustomChecks_whenBridgeOverridden() {
        val bridge = FakeFridaBridge(
            healthCheckFn = {
                HealthCheckResult(
                    overall = "degraded",
                    checks = mapOf(
                        "bridge" to CheckResponse("ok", "Bridge is running"),
                        "frida" to CheckResponse("error", "Frida not found")
                    )
                )
            }
        )
        val tool = HealthCheckTool(bridge)
        val result = tool.invoke(null)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("degraded"))
        assertTrue(result.content.first().text!!.contains("Frida not found"))
    }

    @Test
    fun healthCheck_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(healthCheckFn = { throw RuntimeException("health check unavailable") })
        val tool = HealthCheckTool(bridge)
        val result = tool.invoke(null)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("health check unavailable"))
    }
}
