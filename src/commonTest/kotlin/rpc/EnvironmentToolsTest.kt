package rpc

import bridge.MockFridaBridge
import rpc.tools.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.*

class EnvironmentToolsTest {

    @Test
    fun testPrepareEnvironmentTool() {
        val bridge = MockFridaBridge()
        val tool = PrepareEnvironmentTool(bridge)
        val result = tool.execute(null)
        assertTrue(!result.isError)
        assertTrue(result.content.first().text!!.contains("1234"))
        assertTrue(result.content.first().text!!.contains("com.example"))
    }

    @Test
    fun testInjectGadgetFromScratchTool() {
        val bridge = MockFridaBridge()
        val tool = InjectGadgetFromScratchTool(bridge)
        val args = buildJsonObject {
            put("with_logs", true)
            put("limit", 50)
        }
        val result = tool.execute(args)
        assertTrue(!result.isError)
        assertTrue(result.content.first().text!!.contains("completed"))
    }

    @Test
    fun testInjectJdwpTool() {
        val bridge = MockFridaBridge()
        val tool = InjectJdwpTool(bridge)
        val args = buildJsonObject {
            put("target", "device1")
            put("port", 8080)
            put("package_name", "com.example")
        }
        val result = tool.execute(args)
        assertTrue(!result.isError)
        assertTrue(result.content.first().text!!.contains("Success"))
    }

    @Test
    fun testPatchAndInstallIosAppTool() {
        val bridge = MockFridaBridge()
        val tool = PatchAndInstallIosAppTool(bridge)
        val args = buildJsonObject {
            put("appPath", "/path/to/app")
        }
        val result = tool.execute(args)
        assertTrue(!result.isError)
        assertTrue(result.content.first().text!!.contains("success"))
    }

    @Test
    fun testCheckIosJailbreakStatusTool() {
        val bridge = MockFridaBridge()
        val tool = CheckIosJailbreakStatusTool(bridge)
        val args = buildJsonObject {
            put("serial", "12345")
        }
        val result = tool.execute(args)
        assertTrue(!result.isError)
        assertTrue(result.content.first().text!!.contains("jailbroken"))
    }

    @Test
    fun testInjectJailbrokenIosTool() {
        val bridge = MockFridaBridge()
        val tool = InjectJailbrokenIosTool(bridge)
        val args = buildJsonObject {
            put("serial", "12345")
        }
        val result = tool.execute(args)
        assertTrue(!result.isError)
        assertTrue(result.content.first().text!!.contains("success"))
    }

    @Test
    fun testCheckIosDeployStatusTool() {
        val bridge = MockFridaBridge()
        val tool = CheckIosDeployStatusTool(bridge)
        val result = tool.execute(null)
        assertTrue(!result.isError)
        assertTrue(result.content.first().text!!.contains("completed"))
    }
}
