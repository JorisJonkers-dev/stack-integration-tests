package com.jorisjonkers.personalstack.acceptance

import com.jorisjonkers.personalstack.acceptance.stub.FakeKnowledgeApiServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val TIMEOUT_MS = 5_000L
private const val CONFIGURED_INBOX_TOTAL = 7

class KnowledgeApiClientTest {
    private lateinit var server: FakeKnowledgeApiServer

    @AfterEach
    fun stop() {
        server.stop()
    }

    private fun clientWith(recallHitIds: List<String> = emptyList()): KnowledgeApiClient {
        server = FakeKnowledgeApiServer(inboxTotal = CONFIGURED_INBOX_TOTAL, recallHitIds = recallHitIds).start()
        val config = KnowledgeApiTargetConfig(enabled = true, baseUrl = server.baseUrl, timeoutMs = TIMEOUT_MS)
        return KnowledgeApiClient(config)
    }

    @Test
    fun `query calls the real recall endpoint and returns the hit ids`() {
        val client = clientWith(recallHitIds = listOf("kb-note-1"))
        val result = client.query("what is the load balancer decision")

        assertThat(result.recalledNoteIds).containsExactly("kb-note-1")
        val request = server.requests.single()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/v1/knowledge/recall")
        assertThat(request.query).contains("query=")
    }

    @Test
    fun `backlog reads the review summary's inbox total`() {
        val client = clientWith()
        assertThat(client.backlog()).isEqualTo(CONFIGURED_INBOX_TOTAL)
    }
}
