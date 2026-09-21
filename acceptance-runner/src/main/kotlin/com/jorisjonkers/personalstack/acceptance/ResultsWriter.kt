package com.jorisjonkers.personalstack.acceptance

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path

private val GSON = GsonBuilder().setPrettyPrinting().create()

/** Writes the run's JSON (full detail) and a condensed Markdown summary alongside it. */
fun writeResults(
    resultsDir: Path,
    report: AcceptanceReport,
): WrittenResults {
    Files.createDirectories(resultsDir)
    val stamp = report.metadata.startedAt.replace(Regex("[:.]"), "-")
    val jsonPath = resultsDir.resolve("acceptance-run-$stamp.json")
    val markdownPath = resultsDir.resolve("acceptance-run-$stamp.md")
    Files.writeString(jsonPath, GSON.toJson(report))
    Files.writeString(markdownPath, renderMarkdown(report))
    return WrittenResults(jsonPath, markdownPath)
}

private fun renderMarkdown(report: AcceptanceReport): String {
    val lines =
        mutableListOf(
            "# Knowledge acceptance run — ${report.metadata.startedAt}",
            "",
            "- dry run: ${report.metadata.dryRun}",
            "- evaluation set: ${report.metadata.evaluationSetPath}",
            "",
        )
    report.hindsight?.let { lines += renderSeededSection("hindsight", it) }
    report.basicMemory?.let { lines += renderSeededSection("basic-memory", it) }
    report.knowledgeApi?.let { lines += renderComparisonSection("knowledge-api (comparison)", it) }
    return lines.joinToString("\n")
}

private fun renderSeededSection(
    name: String,
    outcome: TargetOutcome<SeededTargetReport>,
): List<String> {
    val header = "## $name"
    val body =
        outcome.report?.let { r ->
            listOf(
                "- recall quality: ${fmtPct(r.recall.ratePercent)} (${r.recall.passed}/${r.recall.evaluated})",
                "- misleading-memory rate: ${fmtPct(r.misleading.ratePercent)} " +
                    "(${r.misleading.flagged}/${r.misleading.evaluated})",
                "- query latency p50/p95: ${fmtMs(r.queryLatency.p50Ms)} / ${fmtMs(r.queryLatency.p95Ms)}",
                "- capture-to-availability p50/p95: " +
                    "${fmtMs(r.captureToAvailability.p50Ms)} / ${fmtMs(r.captureToAvailability.p95Ms)}",
                "- backlog: ${r.backlog ?: "n/a for this target"}",
                "- token cost: ${r.tokenUsage.totalTokens?.toString() ?: "not reported by this target"}",
                "- cleanup: deleted ${r.cleanup.attempted - r.cleanup.failed.size}/${r.cleanup.attempted}",
                manualReviewLine(r.manualReviewRequired),
            )
        } ?: listOf("FAILED: ${outcome.error}")
    return listOf(header, "") + body + listOf("")
}

private fun renderComparisonSection(
    name: String,
    outcome: TargetOutcome<ComparisonTargetReport>,
): List<String> {
    val header = "## $name"
    val body =
        outcome.report?.let { r ->
            listOf(
                "- ${r.note}",
                "- query latency p50/p95: ${fmtMs(r.queryLatency.p50Ms)} / ${fmtMs(r.queryLatency.p95Ms)}",
                "- backlog: ${r.backlog ?: "n/a"}",
            )
        } ?: listOf("FAILED: ${outcome.error}")
    return listOf(header, "") + body + listOf("")
}

private fun manualReviewLine(queryIds: List<String>): String =
    if (queryIds.isEmpty()) {
        "- manual/LLM-judge review required: none"
    } else {
        "- manual/LLM-judge review required for: ${queryIds.joinToString(", ")}"
    }

private fun fmtMs(value: Long?): String = value?.let { "${it}ms" } ?: "n/a"

private fun fmtPct(value: Double?): String = value?.let { "$it%" } ?: "n/a"
