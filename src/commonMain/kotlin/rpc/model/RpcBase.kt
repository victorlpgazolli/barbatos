package rpc.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RpcRequest(val jsonrpc: String = "2.0", val method: String, val params: JsonElement? = null, val id: JsonElement? = null)

@Serializable
data class RpcError(val code: Int, val message: String, val data: JsonElement? = null)

@Serializable
data class RpcErrorResponse(val jsonrpc: String = "2.0", val error: RpcError, val id: JsonElement?)

@Serializable
data class RpcResponse(
    val jsonrpc: String = "2.0",
    val result: JsonElement,
    val id: JsonElement?
)

@Serializable
sealed class RpcParams {}