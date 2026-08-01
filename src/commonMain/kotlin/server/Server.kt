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
    println("Server module initialized.")
    println("Has embedded agent? ${EmbeddedScripts.agent.isNotEmpty()}")

    routing {
        get("/ping") {
            call.respondText("""{"status": "pong"}""", ContentType.Application.Json)
        }
        post("/rpc") {
            val body = call.receiveText()

            if (rpcHandler.isStreamMethod(body)) {
                val ndjsonType = ContentType.parse("application/x-ndjson")

                call.respondBytesWriter(contentType = ndjsonType) {
                    runBlocking {
                        rpcHandler.handleStream(body) { line ->
                            writeFully((line + "\n").encodeToByteArray())
                            flush()
                        }
                    }
                }
            } else {
                val result = rpcHandler.handle(body)
                call.respondText(result.body, ContentType.Application.Json, HttpStatusCode.fromValue(result.statusCode))
            }
        }
    }
}

fun startServer(bridge: FridaBridge) {
    embeddedServer(CIO, port = 8080, host = "127.0.0.1") {
        module(bridge)
    }.start(wait = true)
}
