package com.jorisjonkers.personalstack.acceptance.stub

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Fake of knowledge-api's real, verified read endpoints (see KnowledgeApiClient.kt). */
class FakeKnowledgeApiServer(
    private val inboxTotal: Int = DEFAULT_INBOX_TOTAL,
    private val recallHitIds: List<String> = emptyList(),
) : StubHttpServer() {
    override fun route(request: RecordedRequest): Pair<Int, String> =
        when {
            request.path == RECALL_PATH -> recall()
            request.path == SUMMARY_PATH -> summary()
            else -> NOT_FOUND to """{"error":"not found: ${request.path}"}"""
        }

    private fun recall(): Pair<Int, String> {
        val hits = JsonArray().apply { recallHitIds.forEach { add(JsonObject().apply { addProperty("id", it) }) } }
        return OK to JsonObject().apply { add("hits", hits) }.toString()
    }

    private fun summary(): Pair<Int, String> {
        val inbox = JsonObject().apply { addProperty("total", inboxTotal) }
        return OK to JsonObject().apply { add("inbox", inbox) }.toString()
    }

    companion object {
        private const val OK = 200
        private const val NOT_FOUND = 404
        private const val DEFAULT_INBOX_TOTAL = 3
        private const val RECALL_PATH = "/api/v1/knowledge/recall"
        private const val SUMMARY_PATH = "/api/v1/knowledge/review/summary"
    }
}
