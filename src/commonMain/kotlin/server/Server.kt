package server

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.http.ContentType

fun Application.module() {
    routing {
        get("/ping") {
            call.respondText("""{"status": "pong"}""", ContentType.Application.Json)
        }
    }
}

fun startServer() {
    embeddedServer(CIO, port = 8080, host = "127.0.0.1", module = Application::module).start(wait = true)
}