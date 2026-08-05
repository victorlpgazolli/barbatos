package rpc

import bridge.FakeFridaBridge
import model.rpc.GenericStatusResult
import model.rpc.InjectionProgressResult
import model.rpc.PrepareEnvResult
import rpc.tools.CheckIosDeployStatusTool
import rpc.tools.CheckIosJailbreakStatusTool
import rpc.tools.InjectGadgetFromScratchTool
import rpc.tools.InjectJailbrokenIosTool
import rpc.tools.InjectJdwpTool
import rpc.tools.PatchAndInstallIosAppTool
import rpc.tools.PrepareEnvironmentTool
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvironmentToolsTest {

    // ─── PrepareEnvironmentTool ───────────────────────────────────────────────

    @Test
    fun prepareEnvironment_returnsResult_withTargetArg() {
        val tool = PrepareEnvironmentTool(FakeFridaBridge())
        val args = buildJsonObject { put("target", "com.example.app") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("1234"))
        assertTrue(result.content.first().text!!.contains("com.example"))
    }

    @Test
    fun prepareEnvironment_usesGadgetDefault_whenTargetMissing() {
        val bridge = FakeFridaBridge(
            prepareEnvironmentFn = { target, _ -> PrepareEnvResult(9999, "gadget", 8080, "Attached to $target") }
        )
        val tool = PrepareEnvironmentTool(bridge)
        // args is null → should fall back to "Gadget" default
        val result = tool.invoke(null)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("Attached to Gadget"))
    }

    @Test
    fun prepareEnvironment_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(prepareEnvironmentFn = { _, _ -> throw RuntimeException("attach failed") })
        val tool = PrepareEnvironmentTool(bridge)
        val args = buildJsonObject { put("target", "com.example.app") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("attach failed"))
    }

    // ─── InjectGadgetFromScratchTool ──────────────────────────────────────────

    @Test
    fun injectGadgetFromScratch_returnsCompleted_withDefaultArgs() {
        val tool = InjectGadgetFromScratchTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("completed"))
    }

    @Test
    fun injectGadgetFromScratch_passesCustomArgs_tobridge() {
        var capturedWithLogs = false
        var capturedLimit = 0
        val bridge = FakeFridaBridge(
            injectGadgetFromScratchFn = { withLogs, limit ->
                capturedWithLogs = withLogs
                capturedLimit = limit
                InjectionProgressResult("completed", emptyList())
            }
        )
        val tool = InjectGadgetFromScratchTool(bridge)
        val args = buildJsonObject { put("with_logs", false); put("limit", 25) }
        tool.invoke(args)
        kotlin.test.assertEquals(false, capturedWithLogs)
        kotlin.test.assertEquals(25, capturedLimit)
    }

    @Test
    fun injectGadgetFromScratch_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(injectGadgetFromScratchFn = { _, _ -> throw RuntimeException("injection failed") })
        val tool = InjectGadgetFromScratchTool(bridge)
        val result = tool.invoke(null)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("injection failed"))
    }

    // ─── InjectJdwpTool ───────────────────────────────────────────────────────

    @Test
    fun injectJdwp_returnsSuccess_withAllArgs() {
        val tool = InjectJdwpTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("target", "device1")
            put("port", 8080)
            put("package_name", "com.example")
        }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("Success"))
    }

    @Test
    fun injectJdwp_returnsError_whenTargetMissing() {
        val tool = InjectJdwpTool(FakeFridaBridge())
        val args = buildJsonObject { put("port", 8080); put("package_name", "com.example") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing target"))
    }

    @Test
    fun injectJdwp_returnsError_whenPortMissing() {
        val tool = InjectJdwpTool(FakeFridaBridge())
        val args = buildJsonObject { put("target", "device1"); put("package_name", "com.example") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing port"))
    }

    @Test
    fun injectJdwp_returnsError_whenPackageNameMissing() {
        val tool = InjectJdwpTool(FakeFridaBridge())
        val args = buildJsonObject { put("target", "device1"); put("port", 8080) }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing package_name"))
    }

    @Test
    fun injectJdwp_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(injectJdwpFn = { _, _, _ -> throw RuntimeException("jdwp error") })
        val tool = InjectJdwpTool(bridge)
        val args = buildJsonObject { put("target", "device1"); put("port", 8080); put("package_name", "com.example") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("jdwp error"))
    }

    // ─── PatchAndInstallIosAppTool ────────────────────────────────────────────

    @Test
    fun patchAndInstallIosApp_returnsSuccess_withAppPath() {
        val tool = PatchAndInstallIosAppTool(FakeFridaBridge())
        val args = buildJsonObject { put("appPath", "/path/to/app.ipa") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("success"))
    }

    @Test
    fun patchAndInstallIosApp_returnsError_whenAppPathMissing() {
        val tool = PatchAndInstallIosAppTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing appPath"))
    }

    @Test
    fun patchAndInstallIosApp_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(patchAndInstallIosAppFn = { _ -> throw RuntimeException("codesign failed") })
        val tool = PatchAndInstallIosAppTool(bridge)
        val args = buildJsonObject { put("appPath", "/path/to/app.ipa") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("codesign failed"))
    }

    // ─── CheckIosJailbreakStatusTool ──────────────────────────────────────────

    @Test
    fun checkIosJailbreakStatus_returnsJailbroken_withSerial() {
        val tool = CheckIosJailbreakStatusTool(FakeFridaBridge())
        val args = buildJsonObject { put("serial", "ABC123") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("jailbroken"))
    }

    @Test
    fun checkIosJailbreakStatus_returnsError_whenSerialMissing() {
        val tool = CheckIosJailbreakStatusTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing serial"))
    }

    @Test
    fun checkIosJailbreakStatus_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(checkIosJailbreakStatusFn = { _ -> throw RuntimeException("device unreachable") })
        val tool = CheckIosJailbreakStatusTool(bridge)
        val args = buildJsonObject { put("serial", "ABC123") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("device unreachable"))
    }

    // ─── InjectJailbrokenIosTool ──────────────────────────────────────────────

    @Test
    fun injectJailbrokenIos_returnsSuccess_withSerial() {
        val tool = InjectJailbrokenIosTool(FakeFridaBridge())
        val args = buildJsonObject { put("serial", "ABC123") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("success"))
    }

    @Test
    fun injectJailbrokenIos_returnsError_whenSerialMissing() {
        val tool = InjectJailbrokenIosTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing serial"))
    }

    @Test
    fun injectJailbrokenIos_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(injectJailbrokenIosFn = { _ -> throw RuntimeException("frida-ios-dump failed") })
        val tool = InjectJailbrokenIosTool(bridge)
        val args = buildJsonObject { put("serial", "ABC123") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("frida-ios-dump failed"))
    }

    // ─── CheckIosDeployStatusTool ─────────────────────────────────────────────

    @Test
    fun checkIosDeployStatus_returnsCompleted_withDefaultBridge() {
        val tool = CheckIosDeployStatusTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("completed"))
    }

    @Test
    fun checkIosDeployStatus_returnsCustomStatus_whenBridgeOverridden() {
        val bridge = FakeFridaBridge(checkIosDeployStatusFn = { GenericStatusResult("pending", null) })
        val tool = CheckIosDeployStatusTool(bridge)
        val result = tool.invoke(null)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("pending"))
    }

    @Test
    fun checkIosDeployStatus_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(checkIosDeployStatusFn = { throw RuntimeException("ios-deploy not found") })
        val tool = CheckIosDeployStatusTool(bridge)
        val result = tool.invoke(null)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("ios-deploy not found"))
    }
}
