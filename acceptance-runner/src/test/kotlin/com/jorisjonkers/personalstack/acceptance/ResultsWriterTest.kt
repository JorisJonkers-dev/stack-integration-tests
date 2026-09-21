package com.jorisjonkers.personalstack.acceptance

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

private const val RECALL_RATE_PERCENT = 100.0
private const val ZERO_RATE_PERCENT = 0.0
private const val QUERY_LATENCY_MS = 5L
private const val CAPTURE_LATENCY_MS = 10L

class ResultsWriterTest {
    @Test
    fun `writes a json file and a markdown summary that both mention a failed target`() {
        val tempDir = Files.createTempDirectory("acceptance-results")
        val report =
            AcceptanceReport(
                metadata =
                    AcceptanceMetadata(
                        startedAt = "2026-09-15T10:00:00Z",
                        dryRun = true,
                        evaluationSetPath = "evaluation-set.json",
                        allowedBankPrefix = "eval-",
                    ),
                hindsight =
                    TargetOutcome(
                        report =
                            SeededTargetReport(
                                seededNotes = 1,
                                queryLatency = LatencySummary(1, QUERY_LATENCY_MS, QUERY_LATENCY_MS, QUERY_LATENCY_MS),
                                captureToAvailability =
                                    LatencySummary(
                                        1,
                                        CAPTURE_LATENCY_MS,
                                        CAPTURE_LATENCY_MS,
                                        CAPTURE_LATENCY_MS,
                                    ),
                                recall = RecallScore(evaluated = 1, passed = 1, ratePercent = RECALL_RATE_PERCENT),
                                misleading =
                                    MisleadingScore(
                                        evaluated = 1,
                                        flagged = 0,
                                        flaggedQueryIds = emptyList(),
                                        ratePercent = ZERO_RATE_PERCENT,
                                    ),
                                tokenUsage = TokenUsageSummary(totalTokens = null, sampledCalls = 0),
                                backlog = null,
                                manualReviewRequired = emptyList(),
                                cleanup = CleanupOutcome(attempted = 1, failed = emptyList()),
                                perQuery = emptyList(),
                            ),
                    ),
                basicMemory = TargetOutcome(error = "connection refused"),
                knowledgeApi = null,
            )

        val written = writeResults(tempDir, report)

        assertThat(written.jsonPath).exists()
        assertThat(written.markdownPath).exists()
        val json = Files.readString(written.jsonPath)
        assertThat(json).contains("\"dryRun\": true").contains("connection refused")
        val markdown = Files.readString(written.markdownPath)
        assertThat(markdown).contains("## hindsight").contains("## basic-memory").contains("FAILED: connection refused")
    }
}
