package bridge

import frida.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import model.bridge.FridaMessage
import model.bridge.FridaPayload
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.stderr

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
    fprintf(stderr, "[DEBUG] message=%s\n", message?.toKString())
    fflush(stderr)
    val jsonStr = message?.toKString() ?: return

    try {
        val msg = jsonParser.decodeFromString<FridaMessage>(jsonStr)
        fprintf(stderr, "[DEBUG] typeof msg = %s\n", msg.type)
        fflush(stderr)

        if (msg.type == "send" && msg.payload != null) {
            when (msg.type) {
                "send" -> {
                    when (val parsedPayload = jsonParser.decodeFromJsonElement<FridaPayload>(msg.payload)) {
                        is FridaPayload.RpcResponse -> {
                            fprintf(stderr, "[DEBUG] RpcResponse for reqId: %s, with status: %s\n", parsedPayload.reqId, parsedPayload.status)
                            fflush(stderr)
                            if (parsedPayload.status == "ok") {
                                val resultStr = parsedPayload.data?.toString() ?: "null"
                                FridaRpcManager.pendingResponses[parsedPayload.reqId] = resultStr
                            } else {
                                FridaRpcManager.pendingErrors[parsedPayload.reqId] = parsedPayload.error ?: "Unknown JS Error"
                            }
                        }
                        is FridaPayload.ClassChunk -> {
                            fprintf(stderr, "[DEBUG] ClassChunk for streamId: %s, sended %s items\n", parsedPayload.streamId, parsedPayload.chunk.size.toString())
                            fflush(stderr)
                            FridaRpcManager.onChunkReceived?.invoke(parsedPayload.chunk)
                        }
                        is FridaPayload.StreamEnd -> {
                            fprintf(stderr, "[DEBUG] StreamEnd for streamId: %s\n", parsedPayload.streamId)
                            fflush(stderr)
                            FridaRpcManager.isStreamCompleted = true
                        }
                        else -> Unit
                    }
                }
                "log" -> {
                    val logText = msg.payload.jsonPrimitive.content
                    fprintf(stderr, "[FRIDA LOG] [%s] %s\n", msg.level, logText)
                    fflush(stderr)
                }
                "error" -> {
                    fprintf(stderr, "[FRIDA ERROR] %s\n", msg.description)
                    fflush(stderr)
                }
            }
        } else if (msg.type == "error") {
            fprintf(stderr, "[FRIDA ERROR] %s\n", jsonStr)
            fflush(stderr)
        }
    } catch (e: Exception) {
        fprintf(stderr, "[DEBUG] JSON parse failed: %s \n Original JSON: %s\n", e.message, jsonStr)
        fflush(stderr)
    }
}
