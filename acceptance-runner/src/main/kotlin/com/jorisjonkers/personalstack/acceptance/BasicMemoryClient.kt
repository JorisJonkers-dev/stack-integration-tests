package com.jorisjonkers.personalstack.acceptance

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.time.Instant

/**
 * Talks to Basic Memory 0.23.2 over its MCP transport
 * (`memory-mcp.jorisjonkers.dev/mcp`, or the in-cluster
 * `basic-memory:8000/mcp` service). Tool names (`write_note`,
 * `search_notes`, `delete_note`) match Basic Memory's published tool set;
 * confirm the exact argument shape against a live `tools/list` before the
 * real run.
 *
 * Basic Memory does not hand back a stable custom id for a note, so
 * [seed] embeds the fixture's own id in both the title and the body, and
 * [query] recovers ids by scanning the free-form search text it returns —
 * that only works because every fixture id looks like `eval-NNN-xx`.
 */
class BasicMemoryClient(
    private val config: BasicMemoryTargetConfig,
    private val transport: McpTransport = McpTransport(config.baseUrl, config.mcpPath, config.timeoutMs),
) : SeededTargetClient {
    override val name: String = "basic-memory"

    override fun seed(note: EvalNote): SeedResult {
        val args =
            JsonObject().apply {
                addProperty("project", config.project)
                addProperty("title", "[${note.id}] ${note.title}")
                addProperty("content", "eval_id: ${note.id}\n\n${note.body}")
                addProperty("folder", "eval")
                add("tags", tagsFor(note))
            }
        val call = transport.callTool("write_note", args, headers())
        val permalink = call.result.firstTextContent()?.let(::extractPermalink) ?: note.id
        return SeedResult(permalink, Instant.now(), call.durationMs)
    }

    override fun query(text: String): QueryResult {
        val args =
            JsonObject().apply {
                addProperty("project", config.project)
                addProperty("query", text)
                addProperty("page_size", DEFAULT_PAGE_SIZE)
            }
        val call = transport.callTool("search_notes", args, headers())
        val recalledIds = extractFixtureIds(call.result.firstTextContent())
        return QueryResult(call.durationMs, recalledIds, tokenUsage = null)
    }

    override fun delete(ref: String) {
        val args =
            JsonObject().apply {
                addProperty("project", config.project)
                addProperty("identifier", ref)
            }
        transport.callTool("delete_note", args, headers())
    }

    private fun headers(): Map<String, String> {
        val base =
            mapOf(
                "content-type" to "application/json",
                "accept" to "application/json, text/event-stream",
            )
        val key = config.apiKeyEnv?.let(System::getenv)
        return if (key != null) base + ("authorization" to "Bearer $key") else base
    }

    private fun tagsFor(note: EvalNote): JsonArray =
        JsonArray().apply {
            add(note.source)
            add(note.language)
        }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 10
        private val PERMALINK_PATTERN = Regex("""permalink["\s:]+([\w./-]+)""", RegexOption.IGNORE_CASE)
        private val FIXTURE_ID_PATTERN = Regex("""eval-\d{3}-[a-z]{2}""")

        internal fun extractPermalink(text: String): String? = PERMALINK_PATTERN.find(text)?.groupValues?.get(1)

        internal fun extractFixtureIds(text: String?): List<String> =
            text
                ?.let {
                    FIXTURE_ID_PATTERN
                        .findAll(it)
                        .map { m -> m.value }
                        .distinct()
                        .toList()
                }.orEmpty()
    }
}
