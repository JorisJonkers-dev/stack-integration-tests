package com.jorisjonkers.personalstack.acceptance

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/** Thrown for a missing, malformed, or incomplete evaluation set. */
class EvaluationSetError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Mirrors `system-tests/src/test/resources/evaluation-set.json` (fleet-infra#251 AC-1).
 * Field names follow the committed schema (`schema_version` 1) exactly, snake_case
 * mapped via [SerializedName] rather than renamed, so this stays a straight read of
 * the committed fixture and not a second, drifting definition of it.
 */
data class EvaluationSet(
    @SerializedName("schema_version") val schemaVersion: Int,
    val description: String = "",
    val coverage: List<String> = emptyList(),
    val notes: List<EvalNote> = emptyList(),
    val queries: List<EvalQuery> = emptyList(),
)

data class EvalNote(
    val id: String,
    val language: String,
    val domain: String,
    val title: String,
    val body: String,
    val source: String,
    val confidence: Double,
    @SerializedName("changing_decision") val changingDecision: Boolean = false,
    val superseded: String? = null,
    @SerializedName("uncertain_suggestion") val uncertainSuggestion: Boolean = false,
    @SerializedName("duplicate_source") val duplicateSource: Boolean = false,
    @SerializedName("hostile_instructions") val hostileInstructions: Boolean = false,
    val scope: String? = null,
)

data class EvalQuery(
    val id: String,
    val language: String,
    val query: String,
    @SerializedName("expected_note_ids") val expectedNoteIds: List<String> = emptyList(),
    @SerializedName("expected_answer") val expectedAnswer: String = "",
    val assertion: String,
    @SerializedName("historical_must_not_win") val historicalMustNotWin: Boolean = false,
    @SerializedName("must_not_recall") val mustNotRecall: List<String> = emptyList(),
)

object EvaluationSetLoader {
    private val gson = Gson()

    fun load(path: Path): EvaluationSet {
        if (!Files.isRegularFile(path)) throw EvaluationSetError("evaluation set not found at $path")
        Files.newInputStream(path).use { return parse(it, path.toString()) }
    }

    /** Loads from the classpath resource published by this module's build (see build.gradle.kts). */
    fun loadFromClasspath(resourceName: String = "/evaluation-set.json"): EvaluationSet {
        val stream: InputStream =
            EvaluationSetLoader::class.java.getResourceAsStream(resourceName)
                ?: throw EvaluationSetError("$resourceName not on the classpath")
        stream.use { return parse(it, resourceName) }
    }

    private fun parse(
        stream: InputStream,
        sourceName: String,
    ): EvaluationSet {
        val set = parseJson(stream, sourceName)
        validate(set, sourceName)
        return set
    }

    private fun parseJson(
        stream: InputStream,
        sourceName: String,
    ): EvaluationSet =
        try {
            gson.fromJson(stream.reader(Charsets.UTF_8), EvaluationSet::class.java)
        } catch (ex: com.google.gson.JsonSyntaxException) {
            throw EvaluationSetError("$sourceName: not valid JSON (${ex.message})", cause = ex)
        }

    private fun validate(
        set: EvaluationSet,
        sourceName: String,
    ) {
        requireSupportedSchema(set, sourceName)
        requireContent(set, sourceName)
    }

    private fun requireSupportedSchema(
        set: EvaluationSet,
        sourceName: String,
    ) {
        if (set.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw EvaluationSetError("$sourceName: unsupported schema_version ${set.schemaVersion}")
        }
    }

    private fun requireContent(
        set: EvaluationSet,
        sourceName: String,
    ) {
        if (set.notes.isEmpty()) throw EvaluationSetError("$sourceName: evaluation set has no notes")
        if (set.queries.isEmpty()) throw EvaluationSetError("$sourceName: evaluation set has no queries")
    }

    private const val SUPPORTED_SCHEMA_VERSION = 1
}
