package com.jorisjonkers.personalstack.acceptance

import com.jorisjonkers.personalstack.acceptance.stub.FakeHindsightServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val TIMEOUT_MS = 5_000L

class HindsightClientTest {
    private lateinit var server: FakeHindsightServer
    private lateinit var client: HindsightClient

    @BeforeEach
    fun start() {
        server = FakeHindsightServer().start()
        val config =
            HindsightTargetConfig(
                enabled = true,
                baseUrl = server.baseUrl,
                bank = "eval-test-bank",
                apiKeyEnv = null,
                endpoints = HindsightEndpoints(),
                timeoutMs = TIMEOUT_MS,
            )
        client = HindsightClient(config)
    }

    @AfterEach
    fun stop() {
        server.stop()
    }

    @Test
    fun `seed posts to the configured bank path with the note's id and content`() {
        val note = evalNote(id = "eval-001-en", title = "Postgres pool", body = "pgBouncer keeps idle sessions")
        val result = client.seed(note)

        assertThat(result.ref).isEqualTo("eval-001-en")
        val request = server.requests.single()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/v1/banks/eval-test-bank/memories")
        assertThat(request.body).contains("pgBouncer").contains("eval-001-en")
    }

    @Test
    fun `query recalls a seeded note whose content matches`() {
        client.seed(evalNote(id = "eval-001-en", title = "Postgres pool", body = "pgBouncer keeps idle sessions"))
        client.seed(evalNote(id = "eval-002-nl", title = "Thermostaat", body = "VLAN 40 firewall"))

        val result = client.query("what happens to pgBouncer sessions")

        assertThat(result.recalledNoteIds).containsExactly("eval-001-en")
    }

    @Test
    fun `delete removes the memory by id`() {
        client.seed(evalNote(id = "eval-001-en", title = "Postgres pool", body = "pgBouncer keeps idle sessions"))
        client.delete("eval-001-en")

        val deleteRequest = server.requests.last()
        assertThat(deleteRequest.method).isEqualTo("DELETE")
        assertThat(deleteRequest.path).isEqualTo("/v1/banks/eval-test-bank/memories/eval-001-en")
        assertThat(client.query("pgBouncer").recalledNoteIds).isEmpty()
    }

    private fun evalNote(
        id: String,
        title: String,
        body: String,
    ): EvalNote =
        EvalNote(
            id = id,
            language = "english",
            domain = "infrastructure",
            title = title,
            body = body,
            source = "eval-fixture",
            confidence = 1.0,
        )
}
