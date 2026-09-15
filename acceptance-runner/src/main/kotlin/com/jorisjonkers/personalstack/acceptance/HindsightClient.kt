package com.jorisjonkers.personalstack.acceptance

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.time.Instant

/**
 * Talks to the Hindsight v0.9.2 data-plane API (fleet-infra
 * `cluster/flux/apps/knowledge-platform/hindsight/api.yaml`).
 *
 * The three paths below are this runner's best-effort default against a
 * bank/memory-shaped REST API and are entirely config-driven
 * ([HindsightEndpoints]) — confirm the exact request/response shape against
 * the live OpenAPI (`/docs`) before the real run and override them in the
 * target config if they differ. That confirmation is the "wiring" this
 * ticket explicitly defers.
 */
class HindsightClient(
    private val config: HindsightTargetConfig,
    private val http: TimedHttp = TimedHttp(),
) : SeededTargetClient {
    override val name: String = "hindsight"

    override fun seed(note: EvalNote): SeedResult {
        val path = fillPath(config.endpoints.seed, mapOf("bank" to config.bank))
        val body =
            JsonObject().apply {
                addProperty("external_id", note.id)
                addProperty("title", note.title)
                addProperty("content", note.body)
                add(
                    "metadata",
                    JsonObject().apply {
                        addProperty("eval_note_id", note.id)
                        addProperty("source", note.source)
                        addProperty("confidence", note.confidence)
                        addProperty("language", note.language)
                    },
                )
            }
        val response = http.send(URI("${config.baseUrl}$path"), "POST", body.toString(), headers(), config.timeoutMs)
        val json = JsonParser.parseString(response.body).asJsonObject
        val ref = json.stringOrNull("id") ?: json.stringOrNull("memory_id") ?: note.id
        return SeedResult(ref, Instant.now(), response.durationMs)
    }

    override fun query(text: String): QueryResult {
        val path = fillPath(config.endpoints.query, mapOf("bank" to config.bank))
        val body =
            JsonObject().apply {
                addProperty("query", text)
                addProperty("limit", DEFAULT_QUERY_LIMIT)
            }
        val response = http.send(URI("${config.baseUrl}$path"), "POST", body.toString(), headers(), config.timeoutMs)
        val json = JsonParser.parseString(response.body).asJsonObject
        val items = firstArray(json, "results", "memories", "hits")
        val recalledIds =
            items
                .map { it.asJsonObject.let { hit -> hit.evalNoteId() ?: hit.stringOrNull("id") } }
                .filterNotNull()
        val tokenUsage = json.getAsJsonObject("usage")?.let { it.intOrNull("total_tokens") }
        return QueryResult(response.durationMs, recalledIds, tokenUsage)
    }

    override fun delete(ref: String) {
        val path = fillPath(config.endpoints.deleteOne, mapOf("bank" to config.bank, "id" to ref))
        http.send(URI("${config.baseUrl}$path"), "DELETE", null, headers(), config.timeoutMs)
    }

    private fun headers(): Map<String, String> {
        val base = mapOf("content-type" to "application/json")
        val key = config.apiKeyEnv?.let(System::getenv)
        return if (key != null) base + ("authorization" to "Bearer $key") else base
    }

    companion object {
        private const val DEFAULT_QUERY_LIMIT = 10
    }
}

internal fun fillPath(
    template: String,
    params: Map<String, String>,
): String = params.entries.fold(template) { acc, (key, value) -> acc.replace("{$key}", value) }

internal fun JsonObject.stringOrNull(field: String): String? = get(field)?.takeIf { !it.isJsonNull }?.asString

internal fun JsonObject.intOrNull(field: String): Int? = get(field)?.takeIf { !it.isJsonNull }?.asInt

internal fun JsonObject.evalNoteId(): String? {
    val fromMetadata = getAsJsonObject("metadata")?.stringOrNull("eval_note_id")
    return stringOrNull("external_id") ?: fromMetadata
}

internal fun firstArray(
    json: JsonObject,
    vararg fields: String,
) = fields.firstNotNullOfOrNull { field -> json.getAsJsonArray(field) } ?: com.google.gson.JsonArray()
