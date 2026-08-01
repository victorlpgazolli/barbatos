package model.bridge

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

@Serializable
data class FridaMessage(
    val type: String,
    val level: String? = null,
    val description: String? = null,
    val payload: JsonElement? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class FridaPayload {
    @Serializable
    @SerialName("rpc_request")
    data class RpcRequest(
        val type: String = "rpc_request",
        val reqId: String,
        val method: String,
        val args: List<String> = emptyList()
    ): FridaPayload()

    @Serializable
    @SerialName("rpc_response")
    data class RpcResponse(
        val reqId: String,
        val status: String,
        val data: JsonElement? = null,
        val error: String? = null
    ) : FridaPayload()

    @Serializable
    @SerialName("class_chunk")
    data class ClassChunk(
        val streamId: String,
        val chunk: List<String>
    ) : FridaPayload()

    @Serializable
    @SerialName("class_stream_end")
    data class StreamEnd(
        val streamId: String
    ) : FridaPayload()
}

