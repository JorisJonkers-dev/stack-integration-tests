package com.jorisjonkers.personalstack.acceptance

import com.jorisjonkers.personalstack.acceptance.stub.FakeBasicMemoryServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val TIMEOUT_MS = 5_000L

class BasicMemoryClientTest {
    private lateinit var server: FakeBasicMemoryServer
    private lateinit var client: BasicMemoryClient

    @BeforeEach
    fun start() {
        server = FakeBasicMemoryServer().start()
        val config =
            BasicMemoryTargetConfig(
                enabled = true,
                baseUrl = server.baseUrl,
                mcpPath = "/mcp",
                project = "eval-test-project",
                apiKeyEnv = null,
                timeoutMs = TIMEOUT_MS,
            )
        client = BasicMemoryClient(config)
    }

    @AfterEach
    fun stop() {
        server.stop()
    }

    @Test
    fun `seed calls write_note with the note id embedded in the title and content`() {
        val note = evalNote(id = "eval-002-nl", title = "Thermostaat VLAN", body = "VLAN 40 firewallregel")
        val result = client.seed(note)

        assertThat(result.ref).isEqualTo("note-1")
        val request = server.requests.single()
        assertThat(request.body).contains("write_note").contains("eval-002-nl").contains("eval-test-project")
    }

    @Test
    fun `query extracts fixture ids scanning the free-form search text`() {
        client.seed(evalNote(id = "eval-002-nl", title = "Thermostaat VLAN", body = "VLAN 40 firewallregel"))
        client.seed(evalNote(id = "eval-006-en", title = "Garden watering", body = "irrigation timer"))

        val result = client.query("firewallregel voor VLAN")

        assertThat(result.recalledNoteIds).containsExactly("eval-002-nl")
    }

    @Test
    fun `delete calls delete_note with the write result's identifier`() {
        client.seed(evalNote(id = "eval-002-nl", title = "Thermostaat VLAN", body = "VLAN 40 firewallregel"))
        client.delete("note-1")

        val request = server.requests.last()
        assertThat(request.body).contains("delete_note").contains("note-1")
        assertThat(client.query("firewallregel").recalledNoteIds).isEmpty()
    }

    private fun evalNote(
        id: String,
        title: String,
        body: String,
    ): EvalNote =
        EvalNote(
            id = id,
            language = "dutch",
            domain = "domotica",
            title = title,
            body = body,
            source = "eval-fixture",
            confidence = 1.0,
        )
}
