package com.jorisjonkers.personalstack.acceptance

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Thrown for a transport failure or a non-2xx response from a target. */
class TargetHttpError(
    message: String,
    val status: Int? = null,
    val responseBody: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

data class TimedResponse(
    val durationMs: Long,
    val status: Int,
    val body: String,
)

/**
 * Minimal timed HTTP client shared by every target client. Each call
 * records wall-clock duration, since that duration is exactly the
 * per-query / per-seed latency the acceptance run reports.
 */
class TimedHttp(
    private val client: HttpClient = HttpClient.newHttpClient(),
) {
    fun send(
        uri: URI,
        method: String,
        body: String?,
        headers: Map<String, String>,
        timeoutMs: Long,
    ): TimedResponse {
        val request = buildRequest(uri, method, body, headers, timeoutMs)
        val started = System.nanoTime()
        val response = execute(request, method, uri)
        val durationMs = (System.nanoTime() - started) / NANOS_PER_MILLI
        return toTimedResponse(response, durationMs, method, uri)
    }

    private fun buildRequest(
        uri: URI,
        method: String,
        body: String?,
        headers: Map<String, String>,
        timeoutMs: Long,
    ): HttpRequest {
        val bodyPublisher =
            if (body != null) {
                HttpRequest.BodyPublishers.ofString(body)
            } else {
                HttpRequest.BodyPublishers.noBody()
            }
        val builder =
            HttpRequest
                .newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .method(method, bodyPublisher)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private fun execute(
        request: HttpRequest,
        method: String,
        uri: URI,
    ): HttpResponse<String> =
        try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (ex: java.io.IOException) {
            throw TargetHttpError("$method $uri failed: ${ex.message ?: ex}", cause = ex)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TargetHttpError("$method $uri interrupted: ${ex.message ?: ex}", cause = ex)
        }

    private fun toTimedResponse(
        response: HttpResponse<String>,
        durationMs: Long,
        method: String,
        uri: URI,
    ): TimedResponse {
        if (response.statusCode() !in SUCCESS_RANGE) {
            throw TargetHttpError(
                "$method $uri -> ${response.statusCode()}",
                status = response.statusCode(),
                responseBody = response.body(),
            )
        }
        return TimedResponse(durationMs, response.statusCode(), response.body())
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L
        private val SUCCESS_RANGE = 200..299
    }
}
