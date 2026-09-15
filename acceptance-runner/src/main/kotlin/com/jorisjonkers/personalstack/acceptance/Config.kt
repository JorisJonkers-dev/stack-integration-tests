package com.jorisjonkers.personalstack.acceptance

import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/** Thrown for a missing, malformed, or incomplete target config. */
class ConfigError(
    message: String,
) : Exception(message)

data class SafetyConfig(
    val allowedBankPrefix: String = DEFAULT_ALLOWED_PREFIX,
) {
    companion object {
        const val DEFAULT_ALLOWED_PREFIX = "eval-"
    }
}

data class AvailabilityPollConfig(
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 2_000L
        const val DEFAULT_TIMEOUT_MS = 60_000L
    }
}

data class HindsightEndpoints(
    val seed: String = "/v1/banks/{bank}/memories",
    val query: String = "/v1/banks/{bank}/query",
    val deleteOne: String = "/v1/banks/{bank}/memories/{id}",
)

data class HindsightTargetConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val bank: String,
    val apiKeyEnv: String?,
    val endpoints: HindsightEndpoints,
    val timeoutMs: Long,
)

data class BasicMemoryTargetConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val mcpPath: String,
    val project: String,
    val apiKeyEnv: String?,
    val timeoutMs: Long,
)

data class KnowledgeApiTargetConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val timeoutMs: Long,
)

data class AcceptanceConfig(
    val evaluationSetPath: String?,
    val resultsDir: String,
    val safety: SafetyConfig,
    val availabilityPoll: AvailabilityPollConfig,
    val hindsight: HindsightTargetConfig?,
    val basicMemory: BasicMemoryTargetConfig?,
    val knowledgeApi: KnowledgeApiTargetConfig?,
)

/**
 * Loads the YAML target config the acceptance runner is pointed at. Every
 * base URL / bank / project name comes from here, never from a literal in
 * the runner — the config is what gets "wired" to a real target later.
 */
object AcceptanceConfigLoader {
    private const val DEFAULT_RESULTS_DIR = "./acceptance-runner/results"
    private const val DEFAULT_TIMEOUT_MS = 15_000L
    private const val DEFAULT_MCP_PATH = "/mcp"

    fun load(path: Path): AcceptanceConfig {
        if (!Files.isRegularFile(path)) throw ConfigError("config not found: $path")
        Files.newInputStream(path).use { return parse(it, path.toString()) }
    }

    fun parse(
        input: InputStream,
        sourceName: String = "<inline>",
    ): AcceptanceConfig {
        val doc = asMap(Yaml().load(input)) ?: throw ConfigError("$sourceName: empty or invalid YAML")
        return build(doc, sourceName)
    }

    private fun build(
        doc: Map<String, Any?>,
        sourceName: String,
    ): AcceptanceConfig {
        val safetyMap = asMap(doc["safety"]).orEmpty()
        val pollMap = asMap(doc["availabilityPoll"]).orEmpty()
        val targetsMap = asMap(doc["targets"]).orEmpty()
        val comparisonMap = asMap(doc["comparison"]).orEmpty()
        val allowedBankPrefix = safetyMap["allowedBankPrefix"] as? String ?: SafetyConfig.DEFAULT_ALLOWED_PREFIX
        return AcceptanceConfig(
            evaluationSetPath = doc["evaluationSetPath"] as? String,
            resultsDir = doc["resultsDir"] as? String ?: DEFAULT_RESULTS_DIR,
            safety = SafetyConfig(allowedBankPrefix = allowedBankPrefix),
            availabilityPoll =
                AvailabilityPollConfig(
                    intervalMs = longOrDefault(pollMap["intervalMs"], AvailabilityPollConfig.DEFAULT_INTERVAL_MS),
                    timeoutMs = longOrDefault(pollMap["timeoutMs"], AvailabilityPollConfig.DEFAULT_TIMEOUT_MS),
                ),
            hindsight = buildHindsight(asMap(targetsMap["hindsight"]), sourceName),
            basicMemory = buildBasicMemory(asMap(targetsMap["basicMemory"]), sourceName),
            knowledgeApi = buildKnowledgeApi(asMap(comparisonMap["knowledgeApi"]), sourceName),
        )
    }

    private fun buildHindsight(
        map: Map<String, Any?>?,
        sourceName: String,
    ): HindsightTargetConfig? {
        if (map == null) return null
        val endpointsMap = asMap(map["endpoints"]).orEmpty()
        val defaults = HindsightEndpoints()
        return HindsightTargetConfig(
            enabled = map["enabled"] as? Boolean ?: true,
            baseUrl = requireField(map, "baseUrl", "targets.hindsight", sourceName),
            bank = requireField(map, "bank", "targets.hindsight", sourceName),
            apiKeyEnv = map["apiKeyEnv"] as? String,
            endpoints =
                HindsightEndpoints(
                    seed = endpointsMap["seed"] as? String ?: defaults.seed,
                    query = endpointsMap["query"] as? String ?: defaults.query,
                    deleteOne = endpointsMap["deleteOne"] as? String ?: defaults.deleteOne,
                ),
            timeoutMs = longOrDefault(map["timeoutMs"], DEFAULT_TIMEOUT_MS),
        )
    }

    private fun buildBasicMemory(
        map: Map<String, Any?>?,
        sourceName: String,
    ): BasicMemoryTargetConfig? {
        if (map == null) return null
        return BasicMemoryTargetConfig(
            enabled = map["enabled"] as? Boolean ?: true,
            baseUrl = requireField(map, "baseUrl", "targets.basicMemory", sourceName),
            mcpPath = map["mcpPath"] as? String ?: DEFAULT_MCP_PATH,
            project = requireField(map, "project", "targets.basicMemory", sourceName),
            apiKeyEnv = map["apiKeyEnv"] as? String,
            timeoutMs = longOrDefault(map["timeoutMs"], DEFAULT_TIMEOUT_MS),
        )
    }

    private fun buildKnowledgeApi(
        map: Map<String, Any?>?,
        sourceName: String,
    ): KnowledgeApiTargetConfig? {
        if (map == null) return null
        return KnowledgeApiTargetConfig(
            enabled = map["enabled"] as? Boolean ?: true,
            baseUrl = requireField(map, "baseUrl", "comparison.knowledgeApi", sourceName),
            timeoutMs = longOrDefault(map["timeoutMs"], DEFAULT_TIMEOUT_MS),
        )
    }

    private fun requireField(
        map: Map<String, Any?>,
        field: String,
        context: String,
        sourceName: String,
    ): String = map[field] as? String ?: throw ConfigError("$sourceName: $context.$field is required")

    private fun longOrDefault(
        value: Any?,
        default: Long,
    ): Long = (value as? Number)?.toLong() ?: default

    @Suppress("UNCHECKED_CAST")
    private fun asMap(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>
}
