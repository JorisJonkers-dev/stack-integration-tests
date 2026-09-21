package com.jorisjonkers.personalstack.acceptance

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

/** Thrown when an MCP `tools/call` comes back as a JSON-RPC error. */
class McpToolError(
    message: String,
) : Exception(message)

data class McpCallResult(
    val durationMs: Long,
    val result: JsonObject,
)

/**
 * A minimal MCP client: one JSON-RPC 2.0 `tools/call` per HTTP POST, no
 * session handshake. Basic Memory's real streamable-HTTP transport may
 * require an `initialize` call and an `Mcp-Session-Id` header first — this
 * is the one-shot subset that is testable against a stub today; add the
 * handshake here once the live server confirms it needs one (the "wiring"
 * this ticket defers).
 */
class McpTransport(
    private val baseUrl: String,
    private val path: String,
    private val timeoutMs: Long,
    private val http: TimedHttp = TimedHttp(),
) {
    private val nextId = AtomicLong(1)

    fun callTool(
        toolName: String,
        arguments: JsonObject,
        headers: Map<String, String>,
    ): McpCallResult {
        val body = requestBody(toolName, arguments)
        val response = http.send(URI("$baseUrl$path"), "POST", body, headers, timeoutMs)
        val rpc =
            parseJsonRpc(response.body)
                ?: throw McpToolError("$toolName: no JSON-RPC response body from $baseUrl$path")
        return McpCallResult(response.durationMs, resultOrThrow(toolName, rpc))
    }

    private fun requestBody(
        toolName: String,
        arguments: JsonObject,
    ): String =
        JsonObject()
            .apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", nextId.getAndIncrement())
                addProperty("method", "tools/call")
                add(
                    "params",
                    JsonObject().apply {
                        addProperty("name", toolName)
                        add("arguments", arguments)
                    },
                )
            }.toString()

    private fun resultOrThrow(
        toolName: String,
        rpc: JsonObject,
    ): JsonObject {
        rpc.getAsJsonObject("error")?.let {
            throw McpToolError("$toolName: ${it.stringOrNull("message") ?: "MCP tool error"}")
        }
        return rpc.getAsJsonObject("result") ?: throw McpToolError("$toolName: JSON-RPC response had no result")
    }

    /** Accepts a plain JSON body or `text/event-stream` framing (`data: {...}` lines). */
    private fun parseJsonRpc(raw: String): JsonObject? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("{")) return JsonParser.parseString(trimmed).asJsonObject
        return trimmed
            .lineSequence()
            .filter { it.startsWith("data:") }
            .mapNotNull(::parseSseDataLine)
            .lastOrNull { it.isJsonObject }
            ?.asJsonObject
    }

    private fun parseSseDataLine(line: String): com.google.gson.JsonElement? =
        runCatching { JsonParser.parseString(line.removePrefix("data:").trim()) }.getOrNull()
}

/** The text content of an MCP tool result — Basic Memory's tools return markdown in this shape. */
fun JsonObject.firstTextContent(): String? =
    getAsJsonArray("content")
        ?.firstOrNull { it.asJsonObject.stringOrNull("type") == "text" }
        ?.asJsonObject
        ?.stringOrNull("text")
