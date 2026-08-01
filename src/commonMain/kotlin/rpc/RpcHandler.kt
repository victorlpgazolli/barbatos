package rpc

import model.bridge.FridaBridge
import kotlinx.serialization.json.*
import model.rpc.ListClassesParams
import model.rpc.RpcError
import model.rpc.RpcErrorResponse
import model.rpc.RpcParams
import model.rpc.RpcRequest
import model.rpc.RpcResponse

data class HandlerResult(val body: String, val statusCode: Int)

class RpcHandler(private val bridge: FridaBridge) {
    val jsonParser = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun isStreamMethod(requestJson: String): Boolean {
        return try {
            val req = jsonParser.decodeFromString<RpcRequest>(requestJson)
            req.method == "listClassesStream"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun handleStream(requestJson: String, emit: suspend (String) -> Unit) {
        val req = try {
            jsonParser.decodeFromString<RpcRequest>(requestJson)
        } catch (e: Exception) {
            val errorStr = jsonParser.encodeToString(
                RpcErrorResponse.serializer(),
                RpcErrorResponse(error = RpcError(-32700, "Parse error: ${e.message}"), id = null)
            )
            emit(errorStr)
            return
        }

        if (req.method == "listClassesStream") {
            val p = req.params?.let { jsonParser.decodeFromJsonElement<ListClassesParams>(it) } ?: ListClassesParams()

            try {
                bridge.listClassesStream(
                    searchParam = p.search_param,
                    appPackage = p.app_package,
                    offset = p.offset,
                    limit = p.limit,
                    onChunk = { chunk ->
                        val res = RpcResponse(
                            result = jsonParser.encodeToJsonElement(chunk),
                            id = req.id
                        )
                        val jsonStr = jsonParser.encodeToString(RpcResponse.serializer(), res)

                        emit(jsonStr)
                    },
                    onComplete = {}
                )
            } catch (e: Exception) {
                val errorStr = jsonParser.encodeToString(
                    RpcErrorResponse.serializer(),
                    RpcErrorResponse(
                        error = RpcError(-32603, e.message ?: "Internal stream error"),
                        id = req.id
                    )
                )
                emit(errorStr)
            }
        }
    }

    fun handle(requestJson: String): HandlerResult {
        val req = try {
            jsonParser.decodeFromString<RpcRequest>(requestJson)
        } catch (e: Exception) {
            return HandlerResult(
                jsonParser.encodeToString(
                    RpcErrorResponse.serializer(),
                    RpcErrorResponse(
                        error = RpcError(-32700, "Parse error: ${e.message}"),
                        id = null
                    )
                ),
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
                jsonParser.encodeToString(
                    RpcErrorResponse.serializer(),
                    RpcErrorResponse(
                        error = RpcError(code, e.message ?: "Internal error"),
                        id = req.id
                    )
                ),
                200
            )
        }
    }

    private fun processMethod(method: String, params: JsonElement?): JsonElement {
        return when (method) {
            "debugPing" -> {
                val result = bridge.pingJava()
                jsonParser.encodeToJsonElement(result)
            }
            "testRpc" -> {
                val result = bridge.testRpc()
                jsonParser.encodeToJsonElement(result)
            }
            "countInstances" -> {
                val decodedParams = decodeToOrThrow<model.rpc.CountInstancesParams>(params)
                val result = bridge.countInstances(decodedParams.className)
                jsonParser.encodeToJsonElement(result)
            }
            "inspectClass" -> {
                val decodedParams = decodeToOrThrow<model.rpc.InspectClassParams>(params)
                val result = bridge.inspectClass(decodedParams.className)
                jsonParser.encodeToJsonElement(result)
            }
            "listInstances" -> {
                val decodedParams = decodeToOrThrow<model.rpc.ListInstancesParams>(params)
                val result = bridge.listInstances(decodedParams.className)
                jsonParser.encodeToJsonElement(result)
            }
            "inspectInstance" -> {
                val decodedParams = decodeToOrThrow<model.rpc.InspectInstanceParams>(params)
                val result = bridge.inspectInstance(decodedParams.id, decodedParams.offset, decodedParams.limit)
                jsonParser.encodeToJsonElement(result)
            }
            "setFieldValue" -> {
                val decodedParams = decodeToOrThrow<model.rpc.SetFieldValueParams>(params)
                val result = bridge.setFieldValue(decodedParams.className, decodedParams.id, decodedParams.fieldName, decodedParams.type, decodedParams.newValue)
                jsonParser.encodeToJsonElement(result)
            }
            "hookMethod" -> {
                val decodedParams = decodeToOrThrow<model.rpc.HookParams>(params)
                val result = bridge.hookMethod(decodedParams.className, decodedParams.methodSig)
                jsonParser.encodeToJsonElement(result)
            }
            "getHookEvents" -> {
                val result = bridge.getHookEvents()
                jsonParser.encodeToJsonElement(result)
            }
            "setMethodImplementation" -> {
                val decodedParams = decodeToOrThrow<model.rpc.SetMethodImplementationParams>(params)
                val result = bridge.setMethodImplementation(decodedParams.className, decodedParams.methodSig, decodedParams.code)
                jsonParser.encodeToJsonElement(result)
            }
            "runOnce" -> {
                val decodedParams = decodeToOrThrow<model.rpc.RunOnceParams>(params)
                val result = bridge.runOnce(decodedParams.className, decodedParams.methodSig, decodedParams.code)
                jsonParser.encodeToJsonElement(result)
            }
            "getInstanceAddresses" -> {
                val decodedParams = decodeToOrThrow<model.rpc.GetInstanceAddressesParams>(params)
                val res = bridge.getInstanceAddresses(decodedParams.className)
                jsonParser.encodeToJsonElement(res)
            }
            "prepareEnvironment" -> {
                val decodedParams = decodeToOrThrow<model.rpc.PrepareEnvParams>(params)
                val result = bridge.prepareEnvironment(decodedParams.target)
                jsonParser.encodeToJsonElement(result)
            }
            "injectGadgetFromScratch" -> {
                val decodedParams = decodeToOrThrow<model.rpc.InjectGadgetParams>(params)
                val result = bridge.injectGadgetFromScratch(decodedParams.with_logs, decodedParams.limit)
                jsonParser.encodeToJsonElement(result)
            }
            "injectJdwp" -> {
                val decodedParams = decodeToOrThrow<model.rpc.InjectJdwpParams>(params)
                val result = bridge.injectJdwp(decodedParams.target, decodedParams.port, decodedParams.package_name)
                jsonParser.encodeToJsonElement(result)
            }
            "healthCheck" -> {
                val result = bridge.healthCheck()
                jsonParser.encodeToJsonElement(result)
            }
            "patchAndInstallIosApp" -> {
                val decodedParams = decodeToOrThrow<model.rpc.PatchAndInstallIosAppParams>(params)
                val result = bridge.patchAndInstallIosApp(decodedParams.appPath)
                jsonParser.encodeToJsonElement(result)
            }
            "checkIosJailbreakStatus" -> {
                val decodedParams = decodeToOrThrow<model.rpc.IosJailbreakParams>(params)
                val result = bridge.checkIosJailbreakStatus(decodedParams.serial)
                jsonParser.encodeToJsonElement(result)
            }
            "injectJailbrokenIos" -> {
                val decodedParams = decodeToOrThrow<model.rpc.IosJailbreakParams>(params)
                val result = bridge.injectJailbrokenIos(decodedParams.serial)
                jsonParser.encodeToJsonElement(result)
            }
            "checkIosDeployStatus" -> {
                val result = bridge.checkIosDeployStatus()
                jsonParser.encodeToJsonElement(result)
            }
            else -> throw Exception("Method $method not found")
        }
    }
    private inline fun <reified T: RpcParams>decodeToOrThrow(params: JsonElement?): T =
        decodeToOrNull(params) ?: throw Exception("Missing params")

    private inline fun <reified T: RpcParams>decodeToOrNull(params: JsonElement?): T? =
        params?.let { jsonParser.decodeFromJsonElement<T>(it) }

}