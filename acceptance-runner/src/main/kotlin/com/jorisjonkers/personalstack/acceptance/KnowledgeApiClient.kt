package com.jorisjonkers.personalstack.acceptance

import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Comparison target: the old knowledge-api workflow (`kb.jorisjonkers.dev`,
 * `KnowledgeReadController` / `KnowledgeReviewController`). Read-only — this
 * runner never seeds knowledge-api, so it is queried against whatever is
 * already in its own corpus, never against this evaluation set's fixtures.
 *
 * Both paths are real, verified endpoints (`services/knowledge/api`), not
 * guesses: `/recall` for querying, `/review/summary` (`inbox.total`) as the
 * backlog proxy — the count of notes still awaiting review.
 */
class KnowledgeApiClient(
    private val config: KnowledgeApiTargetConfig,
    private val http: TimedHttp = TimedHttp(),
) : ComparisonTargetClient {
    override val name: String = "knowledge-api"

    override fun query(text: String): QueryResult {
        val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8)
        val uri = URI("${config.baseUrl}/api/v1/knowledge/recall?query=$encoded&limit=$DEFAULT_LIMIT")
        val response = http.send(uri, "GET", null, emptyMap(), config.timeoutMs)
        val json = JsonParser.parseString(response.body).asJsonObject
        val hits = json.getAsJsonArray("hits") ?: com.google.gson.JsonArray()
        val recalledIds = hits.mapNotNull { it.asJsonObject.stringOrNull("id") }
        return QueryResult(response.durationMs, recalledIds, tokenUsage = null)
    }

    override fun backlog(): Int? {
        val uri = URI("${config.baseUrl}/api/v1/knowledge/review/summary")
        val response = http.send(uri, "GET", null, emptyMap(), config.timeoutMs)
        val json = JsonParser.parseString(response.body).asJsonObject
        return json.getAsJsonObject("inbox")?.intOrNull("total")
    }

    companion object {
        private const val DEFAULT_LIMIT = 10
    }
}
