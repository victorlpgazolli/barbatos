import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.utils.io.*
import rpc.RpcHandler
import model.bridge.FridaBridge
import utils.EmbeddedScripts

fun Application.module(bridge: FridaBridge) {
    val rpcHandler = RpcHandler(bridge)
    println("[SERVER] Server module initialized.")
    println("[SERVER] Has embedded agent? ${EmbeddedScripts.agent.isNotEmpty()}")

    routing {
        get("/ping") {
            call.respondText("""{"status": "pong"}""", ContentType.Application.Json)
        }
        post("/rpc") {
            val body = call.receiveText()

            if (rpcHandler.isStreamMethod(body)) {
                val ndjsonType = ContentType.parse("application/x-ndjson")
                call.respondBytesWriter(contentType = ndjsonType) {
                    try {
                        rpcHandler.handleStream(body) { line ->
                            try {
                                writeFully((line + "\n").encodeToByteArray())
                                flush()
                            } catch (e: Exception) {
                                println("[SERVER] Client disconnected ${e.message}")
                            }
                        }
                    } catch (e: Exception) {}
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
