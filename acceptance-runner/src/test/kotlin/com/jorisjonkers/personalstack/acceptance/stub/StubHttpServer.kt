package com.jorisjonkers.personalstack.acceptance.stub

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

data class RecordedRequest(
    val method: String,
    val path: String,
    val query: String?,
    val body: String,
)

/**
 * A minimal in-process HTTP server for testing the acceptance runner's
 * clients offline (fleet-infra #251: "tested in CI against stubbed HTTP").
 * Subclasses implement [route] and can inspect [requests] afterwards.
 */
abstract class StubHttpServer {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
    val requests = mutableListOf<RecordedRequest>()

    init {
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/") { exchange -> handle(exchange) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : StubHttpServer> start(): T {
        server.start()
        return this as T
    }

    fun stop() {
        server.stop(0)
    }

    val baseUrl: String get() = "http://localhost:${server.address.port}"

    /** Returns the HTTP status and response body for one request. */
    protected abstract fun route(request: RecordedRequest): Pair<Int, String>

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
        val uri = exchange.requestURI
        val recorded = RecordedRequest(exchange.requestMethod, uri.path, uri.query, body)
        requests.add(recorded)
        val (status, response) = route(recorded)
        val bytes = response.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("content-type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
