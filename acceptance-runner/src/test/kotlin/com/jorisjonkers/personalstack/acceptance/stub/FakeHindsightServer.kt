package com.jorisjonkers.personalstack.acceptance.stub

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Stateful fake of the Hindsight bank/memory REST shape this runner assumes (see HindsightClient.kt). */
class FakeHindsightServer : StubHttpServer() {
    private val memories = mutableMapOf<String, JsonObject>()
    private var nextId = 1

    override fun route(request: RecordedRequest): Pair<Int, String> =
        when {
            request.method == "POST" && MEMORIES_PATH.matches(request.path) -> seed(request.body)
            request.method == "POST" && QUERY_PATH.matches(request.path) -> query(request.body)
            request.method == "DELETE" && MEMORY_PATH.matches(request.path) -> delete(memoryId(request.path))
            else -> NOT_FOUND to """{"error":"not found: ${request.method} ${request.path}"}"""
        }

    private fun seed(body: String): Pair<Int, String> {
        val doc = JsonParser.parseString(body).asJsonObject
        val id = doc.get("external_id")?.asString ?: "mem-${nextId++}"
        memories[id] = doc
        return OK to """{"id":"$id","external_id":"$id"}"""
    }

    private fun query(body: String): Pair<Int, String> {
        val text =
            JsonParser
                .parseString(body)
                .asJsonObject
                .get("query")
                .asString
                .lowercase()
        val results = JsonArray()
        memories.keys
            .filter { id -> memories.getValue(id).matchesQuery(text) }
            .forEach { id -> results.add(JsonObject().apply { addProperty("external_id", id) }) }
        return OK to JsonObject().apply { add("results", results) }.toString()
    }

    private fun delete(id: String): Pair<Int, String> {
        memories.remove(id)
        return OK to "{}"
    }

    private fun JsonObject.matchesQuery(query: String): Boolean {
        val haystack = "${get("title")?.asString.orEmpty()} ${get("content")?.asString.orEmpty()}".lowercase()
        return haystack.split(WORD_BOUNDARY).any { it.length > MIN_KEYWORD_LENGTH && query.contains(it) }
    }

    private fun memoryId(path: String): String =
        MEMORY_PATH
            .find(path)
            ?.groupValues
            ?.get(2)
            .orEmpty()

    companion object {
        private const val OK = 200
        private const val NOT_FOUND = 404
        private const val MIN_KEYWORD_LENGTH = 3
        private val WORD_BOUNDARY = Regex("""\W+""")
        private val MEMORIES_PATH = Regex("""/v1/banks/[^/]+/memories""")
        private val MEMORY_PATH = Regex("""/v1/banks/([^/]+)/memories/([^/]+)""")
        private val QUERY_PATH = Regex("""/v1/banks/[^/]+/query""")
    }
}
