package com.jorisjonkers.personalstack.acceptance

import java.time.Instant

/** What seeding one fixture note into a target returned. */
data class SeedResult(
    val ref: String,
    val capturedAt: Instant,
    val durationMs: Long,
)

/** What running one query against a target returned. */
data class QueryResult(
    val durationMs: Long,
    val recalledNoteIds: List<String>,
    val tokenUsage: Int?,
)

/**
 * A target the runner can seed fixtures into, query, and clean up after —
 * Hindsight and Basic Memory both implement this.
 */
interface SeededTargetClient {
    val name: String

    fun seed(note: EvalNote): SeedResult

    fun query(text: String): QueryResult

    /** Deletes one previously-seeded fixture, identified by the [SeedResult.ref] it returned. */
    fun delete(ref: String)

    /** Outstanding-work count, or null when the target has no such concept. */
    fun backlog(): Int? = null
}

/** A read-only target the runner only queries for comparison — knowledge-api. */
interface ComparisonTargetClient {
    val name: String

    fun query(text: String): QueryResult

    fun backlog(): Int? = null
}
