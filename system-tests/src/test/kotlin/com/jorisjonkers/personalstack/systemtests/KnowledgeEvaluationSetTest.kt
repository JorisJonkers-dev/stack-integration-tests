package com.jorisjonkers.personalstack.systemtests

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Validates the knowledge-recall evaluation set committed for the acceptance
 * run (fleet-infra #251).
 *
 * This is a *structural* test: it checks that the committed corpus
 * (`src/test/resources/evaluation-set.json`) is well-formed and covers every
 * dimension the ticket requires. It does NOT exercise a live recall engine —
 * the recall measurements are taken separately against the deployed stack and
 * recorded, never substituted with a vendor benchmark. Keeping the fixture
 * structurally sound here means the acceptance run cannot silently lose a
 * mandated dimension when the corpus is edited.
 *
 * Required dimensions (from #251): English and Dutch notes, technical
 * identifiers, changing decisions, uncertain suggestions, duplicate sources,
 * unrelated domains, and at least one source containing hostile instructions.
 */
class KnowledgeEvaluationSetTest {
    private val root: JsonObject = JsonParser.parseString(rawJson()).asJsonObject

    private fun rawJson(): String =
        requireNotNull(
            javaClass.getResourceAsStream("/evaluation-set.json"),
        ) { "evaluation-set.json not on the test classpath" }.readAllBytes().toString(Charsets.UTF_8)

    private fun notes(): JsonArray = root.getAsJsonArray("notes")

    private fun queries(): JsonArray = root.getAsJsonArray("queries")

    private fun JsonElement.str(field: String): String? = asJsonObject.get(field)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonElement.has(field: String): Boolean = asJsonObject.has(field)

    private fun JsonElement.bool(field: String): Boolean =
        has(field) && !asJsonObject.get(field).isJsonNull && asJsonObject.get(field).asBoolean

    @Test
    fun `commits a well-formed evaluation set`() {
        assertThat(root.has("schema_version")).isTrue
        assertThat(root.get("schema_version").asInt).isEqualTo(1)
        assertThat(root.has("notes")).isTrue
        assertThat(root.has("queries")).isTrue
    }

    @Test
    fun `covers every mandated recall dimension`() {
        val coverage = root.getAsJsonArray("coverage").map { it.asString }.toSet()
        val required =
            setOf(
                "english",
                "dutch",
                "technical_identifier",
                "changing_decision",
                "uncertain_suggestion",
                "duplicate_source",
                "unrelated_domain",
                "hostile_instructions",
            )
        assertThat(coverage).containsAll(required)
    }

    @Test
    fun `has notes in both english and dutch`() {
        val languages = notes().map { it.asJsonObject.get("language").asString }.toSet()
        assertThat(languages).contains("english", "dutch")
    }

    @Test
    fun `commits exactly one hostile-instruction fixture`() {
        val hostile = notes().filter { it.bool("hostile_instructions") }
        assertThat(hostile).hasSize(1)
        assertThat(hostile[0].str("id")).isEqualTo("eval-007-en")
        assertThat(hostile[0].asJsonObject.get("confidence").asDouble).isZero()
    }

    @Test
    fun `the hostile fixture is never an expected recall target`() {
        val expected =
            queries()
                .flatMap { q -> q.asJsonObject.getAsJsonArray("expected_note_ids").map { it.asString } }
                .toSet()
        assertThat(expected).doesNotContain("eval-007-en")
    }

    @Test
    fun `changing decisions mark current and superseded`() {
        val changing = notes().filter { it.bool("changing_decision") }
        assertThat(changing).isNotEmpty
        changing.forEach { note ->
            assertThat(note.has("superseded")).isTrue
            assertThat(note.asJsonObject.get("confidence").asDouble).isEqualTo(1.0)
        }
    }

    @Test
    fun `uncertain suggestions are low-confidence`() {
        val suggestions = notes().filter { it.bool("uncertain_suggestion") }
        assertThat(suggestions).isNotEmpty
        suggestions.forEach { note ->
            assertThat(note.asJsonObject.get("confidence").asDouble).isLessThan(0.5)
        }
    }

    @Test
    fun `duplicate-source note is present`() {
        assertThat(notes().any { it.bool("duplicate_source") }).isTrue
    }

    @Test
    fun `an unrelated-domain note exists and is project-scoped`() {
        val unrelated = notes().filter { it.str("scope") == "project:garden" }
        assertThat(unrelated).isNotEmpty
        assertThat(unrelated[0].str("domain")).isEqualTo("domotica")
    }

    @Test
    fun `every query has a non-empty expected answer and id`() {
        queries().forEach { query ->
            assertThat(query.str("expected_answer")).isNotBlank
            assertThat(query.str("id")).isNotBlank
        }
    }

    @Test
    fun `negative queries must name their must-not-recall targets`() {
        val negative =
            queries().filter { query ->
                query.asJsonObject
                    .get("expected_note_ids")
                    .asJsonArray.isEmpty
            }
        assertThat(negative).isNotEmpty
        negative.forEach { query ->
            val mustNot = query.asJsonObject.getAsJsonArray("must_not_recall") ?: JsonArray()
            assertThat(mustNot.size()).isPositive()
        }
    }
}
