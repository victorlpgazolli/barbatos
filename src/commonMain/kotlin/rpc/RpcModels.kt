package rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RpcRequest(val jsonrpc: String, val method: String, val params: JsonElement? = null, val id: Int? = null)

@Serializable
data class RpcResponse(val jsonrpc: String = "2.0", val result: JsonElement? = null, val id: Int? = null)

@Serializable
data class RpcError(val status: String, val error_message: String)

@Serializable
data class RpcErrorResponse(val jsonrpc: String = "2.0", val error: RpcError, val id: Int? = null)

@Serializable data class ListClassesParams(val search_param: String = "", val app_package: String = "", val offset: Int = 0, val limit: Int = 200)
@Serializable data class CountInstancesParams(val className: String)