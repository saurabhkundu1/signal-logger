package com.example.signallogger

import com.example.signallogger.model.SignalMetric
import com.example.signallogger.repository.SignalRepository
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    println("Starting Signal Logger Ktor Server on port $port...")
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    val repository = SignalRepository()

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    routing {
        get("/") {
            call.respond(mapOf("status" to "ok", "service" to "Signal Logger Server"))
        }

        route("/api/signal-metrics") {
            post {
                try {
                    val metric = call.receive<SignalMetric>()
                    val saved = repository.addMetric(metric)
                    call.respond(HttpStatusCode.Created, saved)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.localizedMessage ?: "Invalid payload"))
                    )
                }
            }

            get {
                call.respond(repository.getAllMetrics())
            }

            get("/latest") {
                val latest = repository.getLatestMetric()
                if (latest != null) {
                    call.respond(latest)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "No metrics recorded yet"))
                }
            }
        }
    }
}
