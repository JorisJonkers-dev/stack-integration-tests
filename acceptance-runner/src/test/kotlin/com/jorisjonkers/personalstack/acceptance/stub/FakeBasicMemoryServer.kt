package com.jorisjonkers.personalstack.acceptance.stub

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Stateful fake of the Basic Memory MCP tool surface this runner calls (see BasicMemoryClient.kt). */
class FakeBasicMemoryServer : StubHttpServer() {
    private data class Note(
        val permalink: String,
        val title: String,
        val content: String,
    )

    private val notes = mutableListOf<Note>()
    private var nextId = 1

    override fun route(request: RecordedRequest): Pair<Int, String> {
        if (request.method != "POST") return NOT_FOUND to """{"error":"not found"}"""
        val rpc = JsonParser.parseString(request.body).asJsonObject
        val id = rpc.get("id")
        val params = rpc.getAsJsonObject("params")
        val arguments = params.getAsJsonObject("arguments")
        val text = dispatch(params.get("name").asString, arguments)
        return OK to jsonRpcResult(id, text)
    }

    private fun dispatch(
        toolName: String,
        args: JsonObject,
    ): String =
        when (toolName) {
            "write_note" -> writeNote(args)
            "search_notes" -> searchNotes(args)
            "delete_note" -> deleteNote(args)
            else -> "unsupported tool: $toolName"
        }

    private fun writeNote(args: JsonObject): String {
        val permalink = "note-${nextId++}"
        notes.add(Note(permalink, args.get("title").asString, args.get("content").asString))
        return "Created note\npermalink: $permalink"
    }

    private fun searchNotes(args: JsonObject): String {
        val query = args.get("query").asString.lowercase()
        val hits = notes.filter { it.matchesQuery(query) }
        return hits.joinToString("\n") { "- ${it.title} (${it.permalink})" }
    }

    private fun deleteNote(args: JsonObject): String {
        notes.removeIf { it.permalink == args.get("identifier").asString }
        return "deleted"
    }

    private fun Note.matchesQuery(query: String): Boolean {
        val haystack = "$title $content".lowercase()
        return haystack.split(WORD_BOUNDARY).any { it.length > MIN_KEYWORD_LENGTH && query.contains(it) }
    }

    private fun jsonRpcResult(
        id: JsonElement,
        text: String,
    ): String =
        JsonObject()
            .apply {
                addProperty("jsonrpc", "2.0")
                add("id", id)
                add(
                    "result",
                    JsonObject().apply {
                        add(
                            "content",
                            JsonArray().apply {
                                add(
                                    JsonObject().apply {
                                        addProperty("type", "text")
                                        addProperty("text", text)
                                    },
                                )
                            },
                        )
                    },
                )
            }.toString()

    companion object {
        private const val OK = 200
        private const val NOT_FOUND = 404
        private const val MIN_KEYWORD_LENGTH = 3
        private val WORD_BOUNDARY = Regex("""\W+""")
    }
}
