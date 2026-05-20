package rpc

import bridge.FridaBridge
import kotlinx.serialization.json.*

class RpcHandler(private val bridge: FridaBridge) {
    val jsonParser = Json { ignoreUnknownKeys = true }

    fun handle(requestJson: String): String {
        val req = try {
            jsonParser.decodeFromString<RpcRequest>(requestJson)
        } catch (e: Exception) {
            return jsonParser.encodeToString(RpcErrorResponse.serializer(), RpcErrorResponse(error = RpcError("parse_error", "Invalid JSON")))
        }

        return try {
            val result = processMethod(req.method, req.params)
            jsonParser.encodeToString(RpcResponse.serializer(), RpcResponse(result = result, id = req.id))
        } catch (e: Exception) {
            jsonParser.encodeToString(RpcErrorResponse.serializer(), RpcErrorResponse(error = RpcError("unknown_error", e.message ?: "Error"), id = req.id))
        }
    }

    private fun processMethod(method: String, params: JsonElement?): JsonElement {
        return when (method) {
            "listClasses" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<ListClassesParams>(it) } ?: ListClassesParams()
                val res = bridge.listClasses(p.search_param, p.app_package, p.offset, p.limit)
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
            else -> throw Exception("Method $method not found")
        }
    }
}