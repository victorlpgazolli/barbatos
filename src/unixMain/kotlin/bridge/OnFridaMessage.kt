package bridge

import frida.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import model.bridge.FridaMessage
import model.bridge.FridaPayload

private val jsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}
// Top-level callback for Frida signals
fun onFridaMessage(
    script: CPointer<FridaScript>?,
    message: CPointer<gcharVar>?,
    data: CPointer<GBytes>?,
    userData: gpointer?
) {
    println("[DEBUG] message=${message?.toKString()}")
    val jsonStr = message?.toKString() ?: return

    try {
        val msg = jsonParser.decodeFromString<FridaMessage>(jsonStr)
        println("[DEBUG] typeof msg = ${msg.type}")

        if (msg.type == "send" && msg.payload != null) {
            when (msg.type) {
                "send" -> {
                    when (val parsedPayload = jsonParser.decodeFromJsonElement<FridaPayload>(msg.payload)) {
                        is FridaPayload.RpcResponse -> {
                            println("[DEBUG] RpcResponse for reqId: ${parsedPayload.reqId}, with status: ${parsedPayload.status}")
                            if (parsedPayload.status == "ok") {
                                val resultStr = parsedPayload.data?.toString() ?: "null"
                                FridaRpcManager.pendingResponses[parsedPayload.reqId] = resultStr
                            } else {
                                FridaRpcManager.pendingErrors[parsedPayload.reqId] = parsedPayload.error ?: "Unknown JS Error"
                            }
                        }
                        is FridaPayload.ClassChunk -> {
                            println("[DEBUG] ClassChunk for streamId: ${parsedPayload.streamId}, sended ${parsedPayload.chunk.size} items")
                            FridaRpcManager.onChunkReceived?.invoke(parsedPayload.chunk)
                        }
                        is FridaPayload.StreamEnd -> {
                            println("[DEBUG] StreamEnd for streamId: ${parsedPayload.streamId}")
                            FridaRpcManager.isStreamCompleted = true
                        }
                        else -> Unit
                    }
                }
                "log" -> {
                    val logText = msg.payload.jsonPrimitive.content
                    println("[FRIDA LOG] [${msg.level}] $logText")
                }
                "error" -> {
                    println("[FRIDA ERROR] ${msg.description}")
                }
            }
        } else if (msg.type == "error") {
            println("[FRIDA ERROR] $jsonStr")
        }
    } catch (e: Exception) {
        println("[DEBUG] JSON parse failed: ${e.message} \n Original JSON: $jsonStr")
    }
}
