package com.jorisjonkers.personalstack.acceptance

import java.time.Duration
import java.time.Instant

private val MANUAL_REVIEW_ASSERTIONS = setOf("current_wins", "suggestion_not_decision")

private data class TimedQueryOutcome(
    val outcome: QueryOutcome,
    val durationMs: Long,
    val tokenUsage: Int?,
)

/**
 * Orchestrates one acceptance run: seed fixtures into every enabled seeded
 * target, wait for them to become recallable, run the evaluation queries,
 * score the result, and clean every fixture back out again — then do the
 * same (query-only) against the knowledge-api comparison target.
 */
object AcceptanceRunner {
    fun run(
        config: AcceptanceConfig,
        evaluationSet: EvaluationSet,
        dryRun: Boolean,
    ): AcceptanceReport {
        assertSafeToSeed(config)
        if (dryRun) assertOfflineSafe(config)

        val metadata =
            AcceptanceMetadata(
                startedAt = Instant.now().toString(),
                dryRun = dryRun,
                evaluationSetPath = config.evaluationSetPath ?: "(embedded)",
                allowedBankPrefix = config.safety.allowedBankPrefix,
            )
        return AcceptanceReport(
            metadata = metadata,
            hindsight =
                config.hindsight
                    ?.takeIf { it.enabled }
                    ?.let { runSeeded(HindsightClient(it), evaluationSet, config.availabilityPoll) },
            basicMemory =
                config.basicMemory
                    ?.takeIf { it.enabled }
                    ?.let { runSeeded(BasicMemoryClient(it), evaluationSet, config.availabilityPoll) },
            knowledgeApi =
                config.knowledgeApi
                    ?.takeIf { it.enabled }
                    ?.let { runComparison(KnowledgeApiClient(it), evaluationSet) },
        )
    }

    private fun runSeeded(
        client: SeededTargetClient,
        evaluationSet: EvaluationSet,
        poll: AvailabilityPollConfig,
    ): TargetOutcome<SeededTargetReport> =
        try {
            TargetOutcome(report = seedQueryAndClean(client, evaluationSet, poll))
        } catch (ex: TargetHttpError) {
            TargetOutcome(error = ex.message)
        } catch (ex: McpToolError) {
            TargetOutcome(error = ex.message)
        }

    private fun seedQueryAndClean(
        client: SeededTargetClient,
        evaluationSet: EvaluationSet,
        poll: AvailabilityPollConfig,
    ): SeededTargetReport {
        val tracker = SeedTracker()
        try {
            val capturedAt = seedAll(client, evaluationSet.notes, tracker)
            val captureLatencies = measureAvailability(client, evaluationSet.notes, capturedAt, poll)
            val timed = runQueries(client, evaluationSet.queries)
            val cleanup = cleanUp(tracker, client::delete)
            return buildReport(client, evaluationSet, captureLatencies, timed, cleanup)
        } catch (ex: TargetHttpError) {
            throw rethrowWithCleanup(ex, tracker, client)
        } catch (ex: McpToolError) {
            throw rethrowWithCleanup(ex, tracker, client)
        }
    }

    private fun rethrowWithCleanup(
        original: Exception,
        tracker: SeedTracker,
        client: SeededTargetClient,
    ): Exception {
        val cleanup = cleanUp(tracker, client::delete)
        val deleted = cleanup.attempted - cleanup.failed.size
        val message = "${original.message} (cleanup after failure: deleted $deleted/${cleanup.attempted})"
        return if (original is McpToolError) McpToolError(message) else TargetHttpError(message)
    }

    private fun buildReport(
        client: SeededTargetClient,
        evaluationSet: EvaluationSet,
        captureLatencies: List<Long>,
        timed: List<TimedQueryOutcome>,
        cleanup: CleanupOutcome,
    ): SeededTargetReport {
        val outcomes = timed.map { it.outcome }
        val hostileNoteIds =
            evaluationSet.notes
                .filter { it.hostileInstructions }
                .map { it.id }
                .toSet()
        return SeededTargetReport(
            seededNotes = evaluationSet.notes.size,
            queryLatency = summarizeLatencies(timed.map { it.durationMs }),
            captureToAvailability = summarizeLatencies(captureLatencies),
            recall = scoreRecall(outcomes),
            misleading = scoreMisleading(outcomes, hostileNoteIds),
            tokenUsage = summarizeTokenUsage(timed.map { it.tokenUsage }),
            backlog = client.backlog(),
            manualReviewRequired = outcomes.filter { it.assertion in MANUAL_REVIEW_ASSERTIONS }.map { it.queryId },
            cleanup = cleanup,
            perQuery = outcomes,
        )
    }

    private fun seedAll(
        client: SeededTargetClient,
        notes: List<EvalNote>,
        tracker: SeedTracker,
    ): Map<String, Instant> {
        val capturedAt = mutableMapOf<String, Instant>()
        notes.forEach { note ->
            val seeded = client.seed(note)
            tracker.record(seeded.ref)
            capturedAt[note.id] = seeded.capturedAt
        }
        return capturedAt
    }

    private fun measureAvailability(
        client: SeededTargetClient,
        notes: List<EvalNote>,
        capturedAt: Map<String, Instant>,
        poll: AvailabilityPollConfig,
    ): List<Long> = notes.mapNotNull { note -> captureLatencyForNote(client, note, capturedAt, poll) }

    private fun captureLatencyForNote(
        client: SeededTargetClient,
        note: EvalNote,
        capturedAt: Map<String, Instant>,
        poll: AvailabilityPollConfig,
    ): Long? {
        val seenAt = pollUntilVisible(client, note, poll) ?: return null
        val captured = capturedAt[note.id] ?: return null
        return Duration.between(captured, seenAt).toMillis()
    }

    private fun pollUntilVisible(
        client: SeededTargetClient,
        note: EvalNote,
        poll: AvailabilityPollConfig,
    ): Instant? {
        val deadline = System.currentTimeMillis() + poll.timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (note.id in client.query(note.title).recalledNoteIds) return Instant.now()
            Thread.sleep(poll.intervalMs)
        }
        return null
    }

    private fun runQueries(
        client: SeededTargetClient,
        queries: List<EvalQuery>,
    ): List<TimedQueryOutcome> =
        queries.map { query ->
            val result = client.query(query.query)
            TimedQueryOutcome(
                outcome =
                    QueryOutcome(
                        queryId = query.id,
                        expectedNoteIds = query.expectedNoteIds,
                        mustNotRecall = query.mustNotRecall,
                        recalledNoteIds = result.recalledNoteIds,
                        assertion = query.assertion,
                    ),
                durationMs = result.durationMs,
                tokenUsage = result.tokenUsage,
            )
        }

    private fun runComparison(
        client: ComparisonTargetClient,
        evaluationSet: EvaluationSet,
    ): TargetOutcome<ComparisonTargetReport> =
        try {
            val timed = evaluationSet.queries.map { query -> query.id to client.query(query.query) }
            TargetOutcome(
                report =
                    ComparisonTargetReport(
                        note =
                            "existing corpus, not seeded with this evaluation set: recall and misleading-memory " +
                                "are not computed here, only latency and backlog, which are directly comparable",
                        queryLatency = summarizeLatencies(timed.map { (_, result) -> result.durationMs }),
                        backlog = client.backlog(),
                        perQuery = timed.map { (id, result) -> ComparisonQueryOutcome(id, result.recalledNoteIds) },
                    ),
            )
        } catch (ex: TargetHttpError) {
            TargetOutcome(error = ex.message)
        }
}
