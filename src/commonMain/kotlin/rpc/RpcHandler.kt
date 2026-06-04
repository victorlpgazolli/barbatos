package rpc

import bridge.FridaBridge
import kotlinx.serialization.json.*

data class HandlerResult(val body: String, val statusCode: Int)

class RpcHandler(private val bridge: FridaBridge) {
    val jsonParser = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun handle(requestJson: String): HandlerResult {
        val req = try {
            jsonParser.decodeFromString<RpcRequest>(requestJson)
        } catch (e: Exception) {
            return HandlerResult(
                jsonParser.encodeToString(RpcErrorResponse.serializer(), RpcErrorResponse(error = RpcError(-32700, "Parse error: ${e.message}"), id = null)),
                200
            )
        }

        return try {
            val result = processMethod(req.method, req.params)
            HandlerResult(
                jsonParser.encodeToString(RpcResponse.serializer(), RpcResponse(result = result, id = req.id)),
                200
            )
        } catch (e: Exception) {
            val code = if (e.message?.contains("not found") == true) -32601 else -32603
            HandlerResult(
                jsonParser.encodeToString(RpcErrorResponse.serializer(), RpcErrorResponse(error = RpcError(code, e.message ?: "Internal error"), id = req.id)),
                200
            )
        }
    }

    private fun processMethod(method: String, params: JsonElement?): JsonElement {
        return when (method) {
            "listClasses" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<ListClassesParams>(it) } ?: ListClassesParams()
                val res = bridge.listClasses(p.search_param, p.app_package, p.offset, p.limit)
                jsonParser.encodeToJsonElement(res)
            }
            "debugPing" -> {
                val res = bridge.pingJava()
                jsonParser.encodeToJsonElement(res)
            }
            "testRpc" -> {
                val res = bridge.testRpc()
                jsonParser.encodeToJsonElement(res)
            }
            "countInstances" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<CountInstancesParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.countInstances(p.className)
                jsonParser.encodeToJsonElement(res)
            }
            "inspectClass" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<InspectClassParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.inspectClass(p.className)
                jsonParser.encodeToJsonElement(res)
            }
            "listInstances" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<ListInstancesParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.listInstances(p.className)
                jsonParser.encodeToJsonElement(res)
            }
            "inspectInstance" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<InspectInstanceParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.inspectInstance(p.className, p.id, p.offset, p.limit)
                jsonParser.encodeToJsonElement(res)
            }
            "setFieldValue" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<SetFieldValueParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.setFieldValue(p.className, p.id, p.fieldName, p.type, p.newValue)
                jsonParser.encodeToJsonElement(res)
            }
            "hookMethod" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<HookParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.hookMethod(p.className, p.methodSig)
                jsonParser.encodeToJsonElement(res)
            }
            "getHookEvents" -> {
                val res = bridge.getHookEvents()
                jsonParser.encodeToJsonElement(res)
            }
            "setMethodImplementation" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<SetMethodImplementationParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.setMethodImplementation(p.className, p.methodSig, p.code)
                jsonParser.encodeToJsonElement(res)
            }
            "runOnce" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<RunOnceParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.runOnce(p.className, p.methodSig, p.code)
                jsonParser.encodeToJsonElement(res)
            }
            "getInstanceAddresses" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<GetInstanceAddressesParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.getInstanceAddresses(p.className)
                jsonParser.encodeToJsonElement(res)
            }
            "prepareEnvironment" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<PrepareEnvParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.prepareEnvironment(p.target)
                jsonParser.encodeToJsonElement(res)
            }
            "injectGadgetFromScratch" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<InjectGadgetParams>(it) } ?: InjectGadgetParams()
                val res = bridge.injectGadgetFromScratch(p.with_logs, p.limit)
                jsonParser.encodeToJsonElement(res)
            }
            "injectJdwp" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<InjectJdwpParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.injectJdwp(p.target, p.port, p.package_name)
                jsonParser.encodeToJsonElement(res)
            }
            "healthCheck" -> {
                val res = bridge.healthCheck()
                jsonParser.encodeToJsonElement(res)
            }
            "patchAndInstallIosApp" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<PatchAndInstallIosAppParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.patchAndInstallIosApp(p.appPath)
                jsonParser.encodeToJsonElement(res)
            }
            "checkIosJailbreakStatus" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<IosJailbreakParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.checkIosJailbreakStatus(p.serial)
                jsonParser.encodeToJsonElement(res)
            }
            "injectJailbrokenIos" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<IosJailbreakParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.injectJailbrokenIos(p.serial)
                jsonParser.encodeToJsonElement(res)
            }
            "checkIosDeployStatus" -> {
                val res = bridge.checkIosDeployStatus()
                jsonParser.encodeToJsonElement(res)
            }
            else -> throw Exception("Method $method not found")
        }
    }
}