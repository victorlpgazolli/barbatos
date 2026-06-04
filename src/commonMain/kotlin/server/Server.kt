package server

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*
import rpc.RpcHandler
import bridge.FridaBridge
import kotlinx.coroutines.*
import utils.EmbeddedScripts

fun Application.module(bridge: FridaBridge) {
    val rpcHandler = RpcHandler(bridge)
    val buildId = "VERIFY_CODE_CHANGE_2"
    println("Server module initialized. BuildId: $buildId")
    println(EmbeddedScripts.agent)

    routing {
        get("/ping") {
            call.respondText("""{"status": "pong", "build": "$buildId"}""", ContentType.Application.Json)
        }
        post("/rpc") {
            val body = call.receiveText()
            val result = rpcHandler.handle(body)
            call.respondText(result.body, ContentType.Application.Json, HttpStatusCode.fromValue(result.statusCode))
        }
        post("/stream/classes") {
            val body = call.receiveText()
            val json = Json.parseToJsonElement(body).jsonObject
            val searchParam = json["search_param"]?.jsonPrimitive?.content ?: ""
            
            call.respondBytesWriter(ContentType.parse("application/x-ndjson")) {
                val channel = this
                bridge.listClassesStream(searchParam, { chunk ->
                    val chunkJson = Json.encodeToString(JsonObject.serializer(), buildJsonObject {
                        put("chunk", buildJsonArray {
                            chunk.forEach { add(it) }
                        })
                    })
                    // No Kotlin Native, respondBytesWriter fornece um CoroutineScope
                    launch {
                        channel.writeStringUtf8(chunkJson + "\n")
                        channel.flush()
                    }
                }, {
                    // Complete
                })
            }
        }
    }
}

fun startServer(bridge: FridaBridge) {
    embeddedServer(CIO, port = 8080, host = "127.0.0.1") {
        module(bridge)
    }.start(wait = true)
}
