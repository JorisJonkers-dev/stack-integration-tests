package com.jorisjonkers.personalstack.acceptance

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val FIRST_LATENCY_MS = 10L
private const val SECOND_LATENCY_MS = 20L
private const val THIRD_LATENCY_MS = 30L
private const val FOURTH_LATENCY_MS = 100L
private const val HALF_RATE_PERCENT = 50.0
private const val FIRST_TOKEN_COUNT = 10
private const val SECOND_TOKEN_COUNT = 20
private const val TOTAL_TOKEN_COUNT = 30

class MetricsTest {
    @Test
    fun `summarizeLatencies reports count p50 p95 and max`() {
        val latencies = listOf(FIRST_LATENCY_MS, SECOND_LATENCY_MS, THIRD_LATENCY_MS, FOURTH_LATENCY_MS)
        val summary = summarizeLatencies(latencies)
        assertThat(summary.count).isEqualTo(latencies.size)
        assertThat(summary.p50Ms).isEqualTo(SECOND_LATENCY_MS)
        assertThat(summary.maxMs).isEqualTo(FOURTH_LATENCY_MS)
    }

    @Test
    fun `summarizeLatencies on an empty list reports nulls, not a crash`() {
        val summary = summarizeLatencies(emptyList())
        assertThat(summary.count).isZero()
        assertThat(summary.p50Ms).isNull()
        assertThat(summary.p95Ms).isNull()
    }

    @Test
    fun `scoreRecall counts only queries with an expected note id, and requires every id present`() {
        val outcomes =
            listOf(
                outcome("q1", expected = listOf("n1"), recalled = listOf("n1")),
                outcome("q2", expected = listOf("n1", "n2"), recalled = listOf("n1")),
                outcome("q3", expected = emptyList(), recalled = emptyList()),
            )
        val score = scoreRecall(outcomes)
        assertThat(score.evaluated).isEqualTo(2)
        assertThat(score.passed).isEqualTo(1)
        assertThat(score.ratePercent).isEqualTo(HALF_RATE_PERCENT)
    }

    @Test
    fun `scoreRecall on no positive queries reports a null rate, not a divide-by-zero`() {
        val score = scoreRecall(listOf(outcome("q1", expected = emptyList(), recalled = emptyList())))
        assertThat(score.ratePercent).isNull()
    }

    @Test
    fun `scoreMisleading flags a must-not-recall id leaking into results`() {
        val outcomes =
            listOf(
                outcome(
                    "q1",
                    expected = emptyList(),
                    recalled = listOf("forbidden"),
                    mustNotRecall = listOf("forbidden"),
                ),
                outcome("q2", expected = listOf("n1"), recalled = listOf("n1")),
            )
        val score = scoreMisleading(outcomes, hostileNoteIds = emptySet())
        assertThat(score.flagged).isEqualTo(1)
        assertThat(score.flaggedQueryIds).containsExactly("q1")
        assertThat(score.ratePercent).isEqualTo(HALF_RATE_PERCENT)
    }

    @Test
    fun `scoreMisleading flags the hostile fixture appearing in any result`() {
        val outcomes = listOf(outcome("q1", expected = listOf("n1"), recalled = listOf("n1", "eval-007-en")))
        val score = scoreMisleading(outcomes, hostileNoteIds = setOf("eval-007-en"))
        assertThat(score.flagged).isEqualTo(1)
    }

    @Test
    fun `scoreMisleading on a clean run reports zero flagged and a rate of zero`() {
        val outcomes = listOf(outcome("q1", expected = listOf("n1"), recalled = listOf("n1")))
        val score = scoreMisleading(outcomes, hostileNoteIds = emptySet())
        assertThat(score.flagged).isZero()
        assertThat(score.ratePercent).isZero()
    }

    @Test
    fun `summarizeTokenUsage sums known usages and ignores unreported calls`() {
        val summary = summarizeTokenUsage(listOf(FIRST_TOKEN_COUNT, null, SECOND_TOKEN_COUNT))
        assertThat(summary.totalTokens).isEqualTo(TOTAL_TOKEN_COUNT)
        assertThat(summary.sampledCalls).isEqualTo(2)
    }

    @Test
    fun `summarizeTokenUsage on no reported usage returns null rather than zero`() {
        val summary = summarizeTokenUsage(listOf(null, null))
        assertThat(summary.totalTokens).isNull()
        assertThat(summary.sampledCalls).isZero()
    }

    private fun outcome(
        id: String,
        expected: List<String>,
        recalled: List<String>,
        mustNotRecall: List<String> = emptyList(),
    ): QueryOutcome =
        QueryOutcome(
            queryId = id,
            expectedNoteIds = expected,
            mustNotRecall = mustNotRecall,
            recalledNoteIds = recalled,
            assertion = "exact_recall",
        )
}
