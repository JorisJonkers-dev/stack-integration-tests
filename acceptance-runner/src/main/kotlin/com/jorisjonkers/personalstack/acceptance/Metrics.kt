package com.jorisjonkers.personalstack.acceptance

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

data class LatencySummary(
    val count: Int,
    val p50Ms: Long?,
    val p95Ms: Long?,
    val maxMs: Long?,
)

data class RecallScore(
    val evaluated: Int,
    val passed: Int,
    val ratePercent: Double?,
)

data class MisleadingScore(
    val evaluated: Int,
    val flagged: Int,
    val flaggedQueryIds: List<String>,
    val ratePercent: Double?,
)

data class TokenUsageSummary(
    val totalTokens: Int?,
    val sampledCalls: Int,
)

/** One query's outcome against one target, ready to score. */
data class QueryOutcome(
    val queryId: String,
    val expectedNoteIds: List<String>,
    val mustNotRecall: List<String>,
    val recalledNoteIds: List<String>,
    val assertion: String,
)

private const val PERCENT_SCALE = 100.0
private const val ROUNDING_FACTOR = 100.0

fun percentile(
    sortedAscending: List<Long>,
    percent: Int,
): Long? {
    if (sortedAscending.isEmpty()) return null
    val rank = ceil((percent / PERCENT_SCALE) * sortedAscending.size).toInt() - 1
    return sortedAscending[max(0, min(rank, sortedAscending.size - 1))]
}

fun summarizeLatencies(latenciesMs: List<Long>): LatencySummary {
    if (latenciesMs.isEmpty()) return LatencySummary(count = 0, p50Ms = null, p95Ms = null, maxMs = null)
    val sorted = latenciesMs.sorted()
    val p50 = 50
    val p95 = 95
    return LatencySummary(
        count = sorted.size,
        p50Ms = percentile(sorted, p50),
        p95Ms = percentile(sorted, p95),
        maxMs = sorted.last(),
    )
}

fun scoreRecall(outcomes: List<QueryOutcome>): RecallScore {
    val positive = outcomes.filter { it.expectedNoteIds.isNotEmpty() }
    val passed = positive.filter { it.expectedNoteIds.all { id -> id in it.recalledNoteIds } }
    return RecallScore(
        evaluated = positive.size,
        passed = passed.size,
        ratePercent = ratePercent(passed.size, positive.size),
    )
}

fun scoreMisleading(
    outcomes: List<QueryOutcome>,
    hostileNoteIds: Set<String>,
): MisleadingScore {
    val flagged =
        outcomes.filter { outcome ->
            val forbidden = outcome.mustNotRecall.toSet() + hostileNoteIds
            outcome.recalledNoteIds.any { it in forbidden }
        }
    return MisleadingScore(
        evaluated = outcomes.size,
        flagged = flagged.size,
        flaggedQueryIds = flagged.map { it.queryId },
        ratePercent = ratePercent(flagged.size, outcomes.size),
    )
}

fun summarizeTokenUsage(usages: List<Int?>): TokenUsageSummary {
    val known = usages.filterNotNull()
    return if (known.isEmpty()) {
        TokenUsageSummary(totalTokens = null, sampledCalls = 0)
    } else {
        TokenUsageSummary(totalTokens = known.sum(), sampledCalls = known.size)
    }
}

private fun ratePercent(
    numerator: Int,
    denominator: Int,
): Double? {
    if (denominator == 0) return null
    return round((numerator.toDouble() / denominator) * PERCENT_SCALE * ROUNDING_FACTOR) / ROUNDING_FACTOR
}
