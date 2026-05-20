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
            else -> throw Exception("Method $method not found")
        }
    }
}