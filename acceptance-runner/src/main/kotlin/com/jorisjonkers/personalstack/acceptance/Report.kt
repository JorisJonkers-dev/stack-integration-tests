package com.jorisjonkers.personalstack.acceptance

import java.nio.file.Path

data class AcceptanceMetadata(
    val startedAt: String,
    val dryRun: Boolean,
    val evaluationSetPath: String,
    val allowedBankPrefix: String,
)

/** Either [report] or [error] is set, never both — a failed target is recorded, not lost. */
data class TargetOutcome<T>(
    val report: T? = null,
    val error: String? = null,
)

data class SeededTargetReport(
    val seededNotes: Int,
    val queryLatency: LatencySummary,
    val captureToAvailability: LatencySummary,
    val recall: RecallScore,
    val misleading: MisleadingScore,
    val tokenUsage: TokenUsageSummary,
    val backlog: Int?,
    val manualReviewRequired: List<String>,
    val cleanup: CleanupOutcome,
    val perQuery: List<QueryOutcome>,
)

data class ComparisonQueryOutcome(
    val queryId: String,
    val recalledNoteIds: List<String>,
)

data class ComparisonTargetReport(
    val note: String,
    val queryLatency: LatencySummary,
    val backlog: Int?,
    val perQuery: List<ComparisonQueryOutcome>,
)

data class WrittenResults(
    val jsonPath: Path,
    val markdownPath: Path,
)

data class AcceptanceReport(
    val metadata: AcceptanceMetadata,
    val hindsight: TargetOutcome<SeededTargetReport>? = null,
    val basicMemory: TargetOutcome<SeededTargetReport>? = null,
    val knowledgeApi: TargetOutcome<ComparisonTargetReport>? = null,
)
