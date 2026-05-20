package server

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import rpc.RpcHandler
import bridge.MockFridaBridge

val globalBridge = MockFridaBridge()

fun Application.module() {
    val rpcHandler = RpcHandler(globalBridge)
    routing {
        get("/ping") {
            call.respondText("""{"status": "pong"}""", ContentType.Application.Json)
        }
        post("/rpc") {
            val body = call.receiveText()
            val responseText = rpcHandler.handle(body)
            if (responseText.contains("\"error\":")) {
                call.respondText(responseText, ContentType.Application.Json, HttpStatusCode.InternalServerError)
            } else {
                call.respondText(responseText, ContentType.Application.Json, HttpStatusCode.OK)
            }
        }
    }
}

fun startServer() {
    embeddedServer(CIO, port = 8080, host = "127.0.0.1", module = Application::module).start(wait = true)
}