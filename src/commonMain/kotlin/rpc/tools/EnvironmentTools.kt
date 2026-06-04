package rpc.tools

import bridge.FridaBridge
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import rpc.*

class PrepareEnvironmentTool(private val bridge: FridaBridge) : McpTool {
    override val name = "prepare_environment"
    override val description = "Prepare the target environment."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("target") { put("type", "string") }
        }
        putJsonArray("required") { add("target") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val target = args?.get("target")?.jsonPrimitive?.content ?: "Gadget"
            val res = bridge.prepareEnvironment(target,)
            McpCallToolResult(listOf(McpContent("text", mcpJson.encodeToString(res))))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class InjectGadgetFromScratchTool(private val bridge: FridaBridge) : McpTool {
    override val name = "inject_gadget_from_scratch"
    override val description = "Inject the Frida Gadget from scratch."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("with_logs") { put("type", "boolean") }
            putJsonObject("limit") { put("type", "integer") }
        }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val withLogs = args?.get("with_logs")?.jsonPrimitive?.booleanOrNull ?: true
            val limit = args?.get("limit")?.jsonPrimitive?.intOrNull ?: 100
            val res = bridge.injectGadgetFromScratch(withLogs, limit)
            McpCallToolResult(listOf(McpContent("text", mcpJson.encodeToString(res))))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class InjectJdwpTool(private val bridge: FridaBridge) : McpTool {
    override val name = "inject_jdwp"
    override val description = "Inject the JDWP protocol."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("target") { put("type", "string") }
            putJsonObject("port") { put("type", "integer") }
            putJsonObject("package_name") { put("type", "string") }
        }
        putJsonArray("required") { add("target"); add("port"); add("package_name") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val target = args?.get("target")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing target")), true)
            val port = args?.get("port")?.jsonPrimitive?.intOrNull ?: return McpCallToolResult(listOf(McpContent("text", "Missing port")), true)
            val packageName = args?.get("package_name")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing package_name")), true)
            
            val res = bridge.injectJdwp(target, port, packageName)
            McpCallToolResult(listOf(McpContent("text", res.toString())))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class PatchAndInstallIosAppTool(private val bridge: FridaBridge) : McpTool {
    override val name = "patch_and_install_ios_app"
    override val description = "Patch and install an iOS app."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("appPath") { put("type", "string") }
        }
        putJsonArray("required") { add("appPath") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val appPath = args?.get("appPath")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing appPath")), true)
            val res = bridge.patchAndInstallIosApp(appPath)
            McpCallToolResult(listOf(McpContent("text", res.toString())))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class CheckIosJailbreakStatusTool(private val bridge: FridaBridge) : McpTool {
    override val name = "check_ios_jailbreak_status"
    override val description = "Check iOS jailbreak status."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("serial") { put("type", "string") }
        }
        putJsonArray("required") { add("serial") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val serial = args?.get("serial")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing serial")), true)
            val res = bridge.checkIosJailbreakStatus(serial)
            McpCallToolResult(listOf(McpContent("text", res.toString())))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class InjectJailbrokenIosTool(private val bridge: FridaBridge) : McpTool {
    override val name = "inject_jailbroken_ios"
    override val description = "Inject into jailbroken iOS devices."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("serial") { put("type", "string") }
        }
        putJsonArray("required") { add("serial") }
    }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val serial = args?.get("serial")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing serial")), true)
            val res = bridge.injectJailbrokenIos(serial)
            McpCallToolResult(listOf(McpContent("text", res.toString())))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}

class CheckIosDeployStatusTool(private val bridge: FridaBridge) : McpTool {
    override val name = "check_ios_deploy_status"
    override val description = "Check ios-deploy status."
    override val inputSchema = buildJsonObject { put("type", "object") }
    override fun execute(args: JsonObject?): McpCallToolResult {
        return try {
            val res = bridge.checkIosDeployStatus()
            McpCallToolResult(listOf(McpContent("text", mcpJson.encodeToString(res))))
        } catch (e: Exception) {
            McpCallToolResult(listOf(McpContent("text", "Error: ${e.message}")), true)
        }
    }
}
